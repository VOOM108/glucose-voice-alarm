// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import com.google.android.gms.wearable.*;
import de.giesen.gva.watch.WatchSnapshot;
/** Only requests a display copy; no command can change phone alarms or settings. */
public final class PhoneWatchRequests extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if(WatchSnapshot.REQUEST.equals(event.getPath())&&event.getData().length==0&&WatchPublisher.enabled(this))
            WatchPublisher.reply(this,event.getSourceNodeId());
    }
}
