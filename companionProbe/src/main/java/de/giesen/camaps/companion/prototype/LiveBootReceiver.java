// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.*;

public final class LiveBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if (intent==null || !(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))) return;
        if (LivePreferences.monitor(context) && SystemReadiness.read(context).ready()) {
            try { context.startForegroundService(new Intent(context,LiveAlarmService.class)); }
            catch (RuntimeException e) { LiveAlarmService.status=AppText.tr("Hintergrundstart nicht möglich; bitte die App öffnen."); }
        }
    }
}
