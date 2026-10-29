// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.Intent;
import android.content.Context;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

final class OutputModeUi {
    static final class Problem {
        final String text;final AlarmLevel missing;
        Problem(String text,AlarmLevel missing){this.text=text;this.missing=missing;}
    }
    /** Same validation for dashboard, editor and direct widget clicks. */
    static Problem check(Context context,OutputPreferences desired) throws IOException {
        if(!desired.audibleAlarmRoute())return new Problem(AppText.tr("Mindestens Ton, Alarm-Sprache oder Vibration muss verfügbar sein."),null);
        if(desired.vibrationEnabled && !new PreviewVibration(context).available())return new Problem(AppText.tr("Dieses Gerät bietet keine Vibration. Bitte Ton oder Sprache verwenden."),null);
        AlarmLevel missing=OutputRequirements.missingTone(new LevelStore(context.getFilesDir()).load().levels,desired);
        if(missing!=null)return new Problem(null,missing);
        try {desired.gap.require(desired);return null;}catch(IllegalArgumentException e){return new Problem(e.getMessage(),null);}
    }
    static OutputPreferences desired(OutputPreferences p,int index,boolean enabled) {
        if(index<0 || index>3)throw new IllegalArgumentException("Invalid output channel");
        return p.withChannels(index==0?enabled:p.tonesEnabled,index==1?enabled:p.alarmSpeechEnabled,
                index==2?enabled:p.valueSpeechEnabled,index==3?enabled:p.vibrationEnabled);
    }
    static boolean needsInterval(OutputPreferences p,int index,boolean enabled){return index==2 && enabled && p.valueIntervalMinutes<1;}
    static void save(Context context,OutputPreferenceStore.Snapshot snapshot,OutputPreferences desired) throws IOException {
        new OutputPreferenceStore(context.getFilesDir()).save(snapshot.revision,desired);
        PreviewAudio.configurationChanged();LiveAlarmService.settingsChanged();
    }
    static boolean validate(Activity activity,OutputPreferences desired) throws IOException {
        Problem problem=check(activity,desired);if(problem==null)return true;
        if(problem.missing==null){showError(activity,problem.text);return false;}
        AlarmLevel missing=problem.missing;
        new AlertDialog.Builder(activity).setTitle("Alarmweg fehlt")
                .setMessage("Für „"+missing.name+"“ fehlt ein aktiver Alarmweg. Bitte Ton, Sprache oder Vibration für diese Stufe einrichten. Die Einstellungen bleiben bis dahin unverändert.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Stufe öffnen",(dialog,which) -> activity.startActivity(
                        desired.toneVolumePercent==0?new Intent(activity,OutputSettingsActivity.class):new Intent(activity,AlarmLevelActivity.class).putExtra("id",missing.rule.id))).show();
        return false;
    }
    static void channel(Activity activity,int index,boolean enabled,Runnable changed) {
        try {
            OutputPreferenceStore store=new OutputPreferenceStore(activity.getFilesDir()); OutputPreferenceStore.Snapshot snapshot=store.load(); OutputPreferences p=snapshot.preferences;
            if(needsInterval(p,index,enabled)) { activity.startActivity(new Intent(activity,OutputSettingsActivity.class).putExtra("enable_values",true)); return; }
            OutputPreferences desired=desired(p,index,enabled);
            if(!validate(activity,desired)) return;
            save(activity,snapshot,desired); changed.run();
        } catch(IOException | IllegalArgumentException e) { showError(activity,e.getMessage()); }
    }
    private static void showError(Activity activity,String text) { new AlertDialog.Builder(activity).setTitle("Modus unverändert").setMessage(text).setPositiveButton("Schließen",null).show(); }
    private OutputModeUi() {}
}
