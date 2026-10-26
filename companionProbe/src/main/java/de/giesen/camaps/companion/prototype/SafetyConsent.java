// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.*;

/** Local, versioned acknowledgement of the notices. Never part of a settings profile. */
final class SafetyConsent {
    static final String PREFS="gva_safety_ack_v1", WARNING="warning_agreed_to", TERMS="I_understand";
    // Increment only when the meaning of the warning or terms changes, not for APK updates.
    static final int TEXT_REVISION=1;
    enum Stage { WARNING, TERMS, COMPLETE }
    static Stage stage(Context c) {
        try {
            SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            if(p.getInt(WARNING,0)!=TEXT_REVISION) return Stage.WARNING;
            return p.getInt(TERMS,0)==TEXT_REVISION?Stage.COMPLETE:Stage.TERMS;
        } catch(RuntimeException e) { return Stage.WARNING; }
    }
    static boolean ready(Context c) { return stage(c)==Stage.COMPLETE; }
    static boolean acknowledge(Context c,Stage expected,boolean checked) {
        if(!checked || expected==Stage.COMPLETE || stage(c)!=expected) return false;
        // Acknowledging the terms never enables output, including on an existing installation.
        if(expected==Stage.TERMS && !disarm(c)) return false;
        SharedPreferences.Editor edit=c.getSharedPreferences(PREFS,0).edit();
        if(expected==Stage.WARNING) edit.putInt(WARNING,TEXT_REVISION).remove(TERMS);
        else edit.putInt(TERMS,TEXT_REVISION);
        return edit.commit();
    }
    static boolean disarm(Context c) {
        boolean saved=c.getSharedPreferences("live_controls_v1",0).edit().putBoolean("alarms",false).putInt("consent_version",-1).commit();
        PreviewAudio.configurationChanged();
        c.stopService(new Intent(c,LiveAlarmService.class));
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(manager!=null) { manager.cancel(LiveNotifications.STATUS_ID); manager.cancel(LiveNotifications.ALARM_ID); }
        return saved;
    }
    static void require(Activity activity) {
        if(ready(activity)) return;
        disarm(activity);
        activity.startActivity(new Intent(activity,SafetyActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        activity.finish();
    }
    private SafetyConsent() {}
}
