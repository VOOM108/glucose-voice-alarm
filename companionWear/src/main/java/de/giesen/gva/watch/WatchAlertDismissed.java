// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.content.*;
/** Dismissing a watch card never acknowledges or snoozes the phone alarm. */
public final class WatchAlertDismissed extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(WatchAlerts.DISMISSED.equals(intent.getAction()))WatchAlerts.dismissed(c,intent.getStringExtra("token"),intent.getBooleanExtra("test",false));}
}
