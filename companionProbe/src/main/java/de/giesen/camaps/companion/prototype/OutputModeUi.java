// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.Intent;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

final class OutputModeUi {
    static boolean validate(Activity activity,OutputPreferences desired) throws IOException {
        if(!desired.audibleAlarmRoute()) { showError(activity,"Mindestens Ton, Alarm-Sprache oder Vibration muss verfügbar sein."); return false; }
        if(desired.vibrationEnabled && !new PreviewVibration(activity).available()) { showError(activity,"Dieses Gerät bietet keine Vibration. Bitte Ton oder Sprache verwenden."); return false; }
        AlarmLevel missing=OutputRequirements.missingTone(new LevelStore(activity.getFilesDir()).load().levels,desired);
        if (missing==null) { try { desired.gap.require(desired); return true; } catch(IllegalArgumentException e) { showError(activity,e.getMessage()); return false; } }
        new AlertDialog.Builder(activity).setTitle("Alarmweg fehlt")
                .setMessage("Für „"+missing.name+"“ fehlt ein aktiver Alarmweg. Bitte Ton, Sprache oder Vibration für diese Stufe einrichten. Die Einstellungen bleiben bis dahin unverändert.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Stufe öffnen",(dialog,which) -> activity.startActivity(
                        desired.toneVolumePercent==0?new Intent(activity,OutputSettingsActivity.class):new Intent(activity,AlarmLevelActivity.class).putExtra("id",missing.rule.id))).show();
        return false;
    }
    static void channel(Activity activity,int index,boolean enabled,Runnable changed) {
        try {
            OutputPreferenceStore store=new OutputPreferenceStore(activity.getFilesDir()); OutputPreferenceStore.Snapshot snapshot=store.load(); OutputPreferences p=snapshot.preferences;
            boolean tone=index==0?enabled:p.tonesEnabled,alarms=index==1?enabled:p.alarmSpeechEnabled,values=index==2?enabled:p.valueSpeechEnabled;
            if(values && p.valueIntervalMinutes<1) { activity.startActivity(new Intent(activity,OutputSettingsActivity.class).putExtra("enable_values",true)); return; }
            OutputPreferences desired=p.withChannels(tone,alarms,values,index==3?enabled:p.vibrationEnabled);
            if(!validate(activity,desired)) return;
            store.save(snapshot.revision,desired); PreviewAudio.configurationChanged(); LiveAlarmService.settingsChanged(); changed.run();
        } catch(IOException | IllegalArgumentException e) { showError(activity,e.getMessage()); }
    }
    private static void showError(Activity activity,String text) { new AlertDialog.Builder(activity).setTitle("Modus unverändert").setMessage(text).setPositiveButton("Schließen",null).show(); }
    private OutputModeUi() {}
}
