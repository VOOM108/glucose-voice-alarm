// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.content.Intent;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Shared, non-navigating activation. The caller decides how to show a missing prerequisite. */
final class LiveActivation {
    static final class Problem {
        final String text; final Class<? extends Activity> target;
        Problem(String text,Class<? extends Activity> target) { this.text=text; this.target=target; }
    }
    static Problem check(Activity activity) {
        if(!SafetyConsent.ready(activity))return new Problem("de".equals(AppText.language())?"Sicherheitshinweise bestätigen":"Acknowledge safety notices",SafetyActivity.class);
        SystemReadiness ready=SystemReadiness.read(activity);
        if(!ready.ready()) return new Problem(ready.nextAction()+AppText.tr(" · Prüfen"),ReadinessActivity.class);
        try {
            LevelStore.Snapshot levels=new LevelStore(activity.getFilesDir()).load();
            OutputPreferences p=new OutputPreferenceStore(activity.getFilesDir()).load().preferences;
            if(!p.audibleAlarmRoute()) return new Problem(AppText.tr("Alarmweg fehlt · Ausgabe prüfen"),OutputSettingsActivity.class);
            if(p.vibrationEnabled && !new PreviewVibration(activity).available()) return new Problem(AppText.tr("Vibration fehlt · Ausgabe prüfen"),OutputSettingsActivity.class);
            AlarmLevel missing=OutputRequirements.missingTone(levels.levels,p);
            if(missing!=null) return new Problem(AppText.tr("Alarmweg fehlt · Stufen prüfen"),AlarmLevelsActivity.class);
            try { p.gap.require(p); } catch(IllegalArgumentException e) { return new Problem(AppText.tr("Lückenwarnung prüfen"),GapSettingsActivity.class); }
            boolean output=p.gap.enabled;
            for(AlarmLevel level:levels.levels) output|=p.alarmToneAllowed(level) || p.alarmSpeechAllowed(level) || p.alarmVibrationAllowed(level);
            if(!output) return new Problem(AppText.tr("Keine Alarmstufe aktiv · Stufen prüfen"),AlarmLevelsActivity.class);
            return null;
        } catch(IOException | IllegalArgumentException e) { return new Problem(AppText.tr("Einstellungen nicht lesbar · Prüfen"),ReadinessActivity.class); }
    }
    static Problem start(Activity activity) {
        Problem problem=check(activity); if(problem!=null) return problem;
        boolean monitor=LivePreferences.monitor(activity);
        try {
            CamApsConnection.ensure(activity);
            LivePreferences.set(activity,true,true);
            activity.startForegroundService(new Intent(activity,LiveAlarmService.class)); return null;
        } catch(RuntimeException e) {
            LivePreferences.set(activity,monitor,false);
            return new Problem(AppText.tr("Start fehlgeschlagen · Betrieb prüfen"),ReadinessActivity.class);
        }
    }
    private LiveActivation() {}
}
