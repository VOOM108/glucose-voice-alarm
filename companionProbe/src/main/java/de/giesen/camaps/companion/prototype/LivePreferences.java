// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.*;

final class LivePreferences {
    private static final String FILE="live_controls_v1";
    static boolean monitor(Context c) { return SafetyConsent.ready(c) && c.getSharedPreferences(FILE,Context.MODE_PRIVATE).getBoolean("monitor",false); }
    static boolean alarms(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        return SafetyConsent.ready(c) && prefs.getBoolean("alarms",false) && prefs.getInt("consent_version",-1)==BuildConfig.VERSION_CODE;
    }
    static boolean publicValue(Context c) { return c.getSharedPreferences(FILE,Context.MODE_PRIVATE).getBoolean("lockscreen_value",true); }
    static boolean nativeDisplay(Context c) { return c.getSharedPreferences(FILE,Context.MODE_PRIVATE).getBoolean("native_display",true); }
    static void nativeDisplay(Context c,boolean value) { c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean("native_display",value).commit(); }
    static long pauseUntil(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        long until=prefs.getLong("pause_until",0); if (until==0) return 0;
        de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock now=RuntimeClock.read(c);
        long wall=prefs.getLong("pause_wall",0),elapsed=prefs.getLong("pause_elapsed",0),boot=prefs.getLong("pause_boot",-1);
        if (boot>=0 && now.boot==boot) {
            if (now.elapsedMs<elapsed) return Long.MAX_VALUE;
            long remaining=(until-wall)-(now.elapsedMs-elapsed);
            return remaining<=0?0:now.wallMs>Long.MAX_VALUE-remaining?Long.MAX_VALUE:now.wallMs+remaining;
        }
        return now.wallMs<wall?Long.MAX_VALUE:until;
    }
    static void set(Context c,boolean monitor,boolean alarms) {
        if(!SafetyConsent.ready(c)){monitor=false;alarms=false;}
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean("monitor",monitor).putBoolean("alarms",monitor && alarms)
                .putInt("consent_version",alarms?BuildConfig.VERSION_CODE:-1).commit();
    }
    static void setPublicValue(Context c,boolean value) { c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean("lockscreen_value",value).commit(); }
    static void alarmStarted(Context c) {
        de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock now=RuntimeClock.read(c);
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putBoolean("alarm_seen",true).putLong("alarm_wall",now.wallMs)
                .putLong("alarm_elapsed",now.elapsedMs).putLong("alarm_boot",now.boot).commit();
    }
    static long valueQuietRemaining(Context c,int minutes) {
        if (minutes<=0) return 0;
        android.content.SharedPreferences p=c.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        if (!p.getBoolean("alarm_seen",false)) return 0;
        de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock now=RuntimeClock.read(c);
        long elapsed=p.getLong("alarm_boot",-1)>=0 && p.getLong("alarm_boot",-1)==now.boot
                ?now.elapsedMs-p.getLong("alarm_elapsed",now.elapsedMs):now.wallMs-p.getLong("alarm_wall",now.wallMs);
        long duration=minutes*60000L;
        return elapsed<0?duration:Math.max(0,duration-elapsed);
    }
    static void pauseUntil(Context c,long until) {
        de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock now=RuntimeClock.read(c);
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putLong("pause_until",until).putLong("pause_wall",now.wallMs)
                .putLong("pause_elapsed",now.elapsedMs).putLong("pause_boot",now.boot).commit();
    }
    private LivePreferences() {}
}
