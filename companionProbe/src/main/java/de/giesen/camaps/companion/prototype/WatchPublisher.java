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
    static boolean enabled(Context c){return SafetyConsent.ready(c)&&c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static void set(Context c,boolean value) {
        if(!SafetyConsent.ready(c))return;
        c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",value).commit();
        if(value)changed(c);else clear(c);
    }
    static void changed(Context context){submit(context,false,null);}
    static void clear(Context context){submit(context,true,null);}
    static void reply(Context context,String node){submit(context,false,node);}
    private static void submit(Context context,boolean clear,String target) {
        final Context c=context.getApplicationContext();
        WORK.execute(()->{
            WatchTransferStatus.Event operation=clear?WatchTransferStatus.Event.DISPLAY_CLEAR:WatchTransferStatus.Event.DISPLAY;
            try {
                if(!SafetyConsent.ready(c)||(!clear&&!enabled(c)))return;
                List<Node> nodes=Tasks.await(Wearable.getNodeClient(c).getConnectedNodes(),3,TimeUnit.SECONDS);
                byte[] packet=clear?new byte[0]:snapshot(c);
                if(packet==null){status=WatchTransferStatus.completed(operation,WatchTransferStatus.Result.NO_DATA);return;}
                boolean sent=false;
                for(Node node:nodes)if(node.isNearby()&&(target==null||target.equals(node.getId()))) {
                    // Recheck the toggle immediately before transfer; disabling sends only CLEAR.
                    if(!clear&&!enabled(c))return;
                    Tasks.await(Wearable.getMessageClient(c).sendMessage(node.getId(),clear?WatchSnapshot.CLEAR:WatchSnapshot.PATH,packet),3,TimeUnit.SECONDS);
                    sent=true;
                }
                status=WatchTransferStatus.completed(operation,sent?WatchTransferStatus.Result.SENT:WatchTransferStatus.Result.NO_NEARBY);
            }catch(Exception e){status=WatchTransferStatus.completed(operation,WatchTransferStatus.Result.FAILED);}
        });
    }
    static byte[] snapshot(Context c)throws java.io.IOException {
        if(!enabled(c))return null;
        WatchSnapshot snapshot=PhoneDisplaySnapshot.read(c);
        return snapshot!=null&&enabled(c)?snapshot.encode():null;
    }
    static String tr(String de,String en){return "de".equals(AppText.language())?de:en;}
    private WatchPublisher(){}
}
