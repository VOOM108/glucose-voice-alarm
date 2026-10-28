// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import de.giesen.gva.watch.WatchAlert;
import java.util.concurrent.*;
/** Ephemeral bounded work only. No offline persistence, retry or alarm replay on reconnect. */
final class WatchAlertTransport {
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),
            r->{Thread t=new Thread(r,"gva-watch-alerts");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardOldestPolicy());
    static void enqueue(Context context,WatchAlert event){
        Context c=context.getApplicationContext();WORK.execute(()->{
            try {
                if(!WatchAlertPublisher.sendAllowed(c,event))return;
                java.util.List<Node> nodes=Tasks.await(Wearable.getNodeClient(c).getConnectedNodes(),3,TimeUnit.SECONDS);
                boolean sent=false;byte[] packet=event.encode();
                for(Node node:nodes)if(node.isNearby()){
                    if(!WatchAlertPublisher.sendAllowed(c,event))return;
                    Tasks.await(Wearable.getMessageClient(c).sendMessage(node.getId(),WatchAlert.PATH,packet),3,TimeUnit.SECONDS);sent=true;
                }
                WatchAlertPublisher.status=WatchTransferStatus.completed(WatchTransferStatus.alert(event.action,event.test),sent?WatchTransferStatus.Result.SENT:WatchTransferStatus.Result.NO_NEARBY);
            }catch(Exception e){WatchAlertPublisher.status=WatchTransferStatus.completed(WatchTransferStatus.alert(event.action,event.test),WatchTransferStatus.Result.FAILED);}
        });
    }
    private WatchAlertTransport(){}
}
