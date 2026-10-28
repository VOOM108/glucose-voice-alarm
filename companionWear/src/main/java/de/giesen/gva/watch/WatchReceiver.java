// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import com.google.android.gms.wearable.*;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;
/** Play services checks package/signature; additionally accept only connected nearby peers. */
public final class WatchReceiver extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if(!WatchConsent.ready(this)||event.getData().length>WatchSnapshot.MAX_BYTES)return;
        try {
            boolean nearby=false;
            for(Node node:Tasks.await(Wearable.getNodeClient(this).getConnectedNodes(),3,TimeUnit.SECONDS))
                if(node.isNearby()&&node.getId().equals(event.getSourceNodeId()))nearby=true;
            if(!nearby)return;
            if(WatchAlert.PATH.equals(event.getPath())){WatchAlerts.receive(this,WatchAlert.decode(event.getData(),System.currentTimeMillis()));return;}
            if(WatchSnapshot.CLEAR.equals(event.getPath())&&event.getData().length==0)WatchStore.clear(this);
            else if(!WatchSnapshot.PATH.equals(event.getPath())||!WatchStore.accept(this,event.getData()))return;
            DisplayComplication.update(this);
        }catch(Exception e){/* No raw payload, node identifier or measurement in logs. */}
    }
}
