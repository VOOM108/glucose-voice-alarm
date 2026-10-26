// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore;

final class RuntimeClock {
    static AlarmStateStore.Clock read(Context context) {
        long boot=-1;
        try { boot=Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1); }
        catch (SecurityException e) { /* Unknown boot: use conservative wall-time restoration. */ }
        return new AlarmStateStore.Clock(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot);
    }
    private RuntimeClock() {}
}
