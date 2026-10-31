// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import android.os.SystemClock;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import de.giesen.gva.watch.WatchSnapshot;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional display copy only. One bounded worker; never waits on the alarm/listener thread. */
public final class WatchPublisher {
    private static final String PREFS="gva_watch_link_v1";
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"gva-watch-display");t.setDaemon(true);return t;},
            new ThreadPoolExecutor.DiscardOldestPolicy());
    static volatile WatchTransferStatus status=WatchTransferStatus.EMPTY;
    // Capabilities survive a coalesced/dropped display task. Only confirmed nearby peers are retained.
    private static final Set<String> queried=new HashSet<>(),colourPeers=ConcurrentHashMap.newKeySet();
    static boolean enabled(Context c){return SafetyConsent.ready(c)&&c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static void set(Context c,boolean value) {
        if(!SafetyConsent.ready(c))return;
        c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",value).commit();
        if(value)changed(c);else clear(c);
    }
    static void changed(Context context){submit(context,false,null);}
    static void clear(Context context){submit(context,true,null);}
    static void reply(Context context,String node){reply(context,node,1);}
    static void reply(Context context,String node,int version){
        if(!enabled(context)||(version!=1&&version!=2))return;
        try {
            Set<String> nearby=new HashSet<>();
            for(Node peer:Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(),3,TimeUnit.SECONDS))if(peer.isNearby())nearby.add(peer.getId());
            colourPeers.retainAll(nearby);if(!nearby.contains(node))return;
            if(version==2)colourPeers.add(node);else colourPeers.remove(node);
            // Persist only in RAM before enqueueing: the one-slot queue may discard this reply.
            submit(context,false,node);
        }catch(Exception e){status=WatchTransferStatus.completed(WatchTransferStatus.Event.DISPLAY,WatchTransferStatus.Result.FAILED);}
    }
    private static void submit(Context context,boolean clear,String target) {
        final Context c=context.getApplicationContext();
        WORK.execute(()->{
            WatchTransferStatus.Event operation=clear?WatchTransferStatus.Event.DISPLAY_CLEAR:WatchTransferStatus.Event.DISPLAY;
            try {
                if(!SafetyConsent.ready(c)||(!clear&&!enabled(c)))return;
                List<Node> nodes=Tasks.await(Wearable.getNodeClient(c).getConnectedNodes(),3,TimeUnit.SECONDS);
                Set<String> nearby=new HashSet<>();for(Node node:nodes)if(node.isNearby())nearby.add(node.getId());
                queried.retainAll(nearby);colourPeers.retainAll(nearby);
                if(target!=null&&nearby.contains(target))queried.add(target);
                WatchSnapshot display=clear?null:PhoneDisplaySnapshot.read(c);
                if(!clear&&display==null){status=WatchTransferStatus.completed(operation,WatchTransferStatus.Result.NO_DATA);return;}
                byte[] legacy=clear?new byte[0]:display.encodeForVersion(1);
                byte[] colour=clear?legacy:display.encode();
                boolean sent=false;
                for(Node node:nodes)if(node.isNearby()&&(target==null||target.equals(node.getId()))) {
                    // Recheck the toggle immediately before transfer; disabling sends only CLEAR.
                    if(!clear&&!enabled(c))return;
                    Tasks.await(Wearable.getMessageClient(c).sendMessage(node.getId(),clear?WatchSnapshot.CLEAR:WatchSnapshot.PATH,colourPeers.contains(node.getId())?colour:legacy),3,TimeUnit.SECONDS);
                    sent=true;
                    if(!clear&&queried.add(node.getId()))try {
                        Tasks.await(Wearable.getMessageClient(c).sendMessage(node.getId(),WatchSnapshot.CAPABILITIES,new byte[0]),3,TimeUnit.SECONDS);
                    }catch(Exception e){queried.remove(node.getId()); /* Optional negotiation failure does not undo the sent display. */ }
                }
                status=WatchTransferStatus.completed(operation,sent?WatchTransferStatus.Result.SENT:WatchTransferStatus.Result.NO_NEARBY);
            }catch(Exception e){status=WatchTransferStatus.completed(operation,WatchTransferStatus.Result.FAILED);}
        });
    }
    static byte[] snapshot(Context c)throws java.io.IOException {
        if(!enabled(c))return null;
        WatchSnapshot snapshot=PhoneDisplaySnapshot.read(c);
        return snapshot!=null&&enabled(c)?snapshot.encodeForVersion(1):null;
    }
    static String tr(String de,String en){return "de".equals(AppText.language())?de:en;}
    private WatchPublisher(){}
}
