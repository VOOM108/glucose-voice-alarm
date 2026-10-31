// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import com.google.android.gms.wearable.*;
import de.giesen.gva.watch.WatchSnapshot;
/** Only requests a display copy; no command can change phone alarms or settings. */
public final class PhoneWatchRequests extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        byte[] data=event.getData();
        if(WatchSnapshot.REQUEST.equals(event.getPath())&&WatchPublisher.enabled(this)
                &&(data.length==0 || (data.length==1 && data[0]==2)))
            WatchPublisher.reply(this,event.getSourceNodeId(),data.length==0?1:2);
    }
}
