// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

/** Native list editor; saving settings never enables the live engine. */
public final class AlarmLevelsActivity extends UiActivity {
    private LinearLayout list;
    private TextView message;
    private LevelStore store;
    private LevelStore.Snapshot snapshot;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new LevelStore(getFilesDir());
        LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück zum Empfang"),v -> finish());
        LevelUi.text(body,AppText.tr("Alarmstufen"),28);
        LevelUi.text(body,AppText.tr("Grenzwerte und schnelle Änderungen. Neue Regeln bleiben aus, bis Du sie einschaltest."),16);
        LevelUi.button(body,AppText.tr("Datenlücken"),v -> startActivity(new Intent(this,GapSettingsActivity.class)));
        message=LevelUi.text(body,"",14);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
        LevelUi.button(body,AppText.tr("Liste neu laden"),v -> refresh());
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private void refresh() {
        list.removeAllViews();
        try { snapshot=store.load(); }
        catch (IOException e) {
            snapshot=null; message.setText(AppText.tr("Alarmstufen konnten nicht gelesen werden. Die vorhandene Datei bleibt unverändert. Bitte erneut laden.")); return;
        }
        message.setText(snapshot.levels.size()+AppText.tr(" Stufen gespeichert. Den Live-Betrieb schaltest Du auf dem Dashboard ein."));
        for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) {
            LevelUi.text(list,LevelForm.kindLabel(kind),22);
            List<AlarmLevel> group=new ArrayList<>();
            for (AlarmLevel level:snapshot.levels) if (level.rule.kind==kind) group.add(level);
            group.sort((a,b) -> kind==AlarmEngine.Kind.LOW?Double.compare(b.rule.threshold,a.rule.threshold):Double.compare(a.rule.threshold,b.rule.threshold));
            if (group.isEmpty()) LevelUi.text(list,AppText.tr("Noch keine Stufe"),14);
            for (AlarmLevel level:group) row(level);
            Button add=LevelUi.button(list,AppText.tr("Stufe für „")+LevelForm.kindLabel(kind)+AppText.tr("“ hinzufügen"),v -> startActivity(
                    new Intent(this,AlarmLevelActivity.class).putExtra("kind",kind.name())));
            add.setEnabled(snapshot.levels.size()<LevelStore.MAX_LEVELS);
        }
    }
    private void row(AlarmLevel level) {
        final LevelStore.Snapshot base=snapshot;
        LinearLayout card=DashboardUi.card(this,list);
        LevelUi.text(card,level.name,19);
        AlarmEngine.Rule r=level.rule;
        String summary=(LevelForm.isRate(r.kind)?AppText.tr("Änderungsrate: "):"Grenze: ")+LevelForm.number(r.threshold)
                +(LevelForm.isRate(r.kind)?AppText.tr(" mg/dl pro Minute"):" mg/dl")
                +(r.kind==AlarmEngine.Kind.PERSISTENT_HIGH?AppText.tr(" · länger als ")+r.durationMinutes+" Minuten":"")
                +"\nTon "+(r.outputs.tone?AppText.tr("ein"):AppText.tr("aus"))+" · Sprache "+(r.outputs.speech?AppText.tr("ein"):AppText.tr("aus"))
                +" · Vibration "+(level.vibrationPattern==null?AppText.tr("aus"):AppText.tr(VibrationCatalog.valueOf(level.vibrationPattern).label))
                +(level.suppressRecoveryRepeats?AppText.tr("\nErholung: Wiederholungen aussetzen"):"")
                +(r.allDay?AppText.tr("\nGanztägig"):String.format(Locale.GERMANY,AppText.tr("\nZeitfenster %02d:%02d bis %02d:%02d"),r.startMinute/60,r.startMinute%60,r.endMinute/60,r.endMinute%60))
                +"\n"+LevelForm.timingSummary(r);
        LevelUi.text(card,summary,15);
        CheckBox enabled=LevelUi.check(card,AppText.tr("Alarmstufe einschalten"),r.enabled);
        enabled.setOnCheckedChangeListener((view,checked) -> {
            List<AlarmLevel> desired=new ArrayList<>();
            for (AlarmLevel current:base.levels) desired.add(current.rule.id.equals(r.id)?current.withEnabled(checked):current);
            commit(base,desired);
        });
        LevelUi.button(card,AppText.tr("Bearbeiten"),v -> startActivity(new Intent(this,AlarmLevelActivity.class).putExtra("id",r.id)));
        LevelUi.button(card,AppText.tr("Löschen"),v -> new AlertDialog.Builder(this).setTitle(AppText.tr("Stufe löschen?"))
                .setMessage("„"+level.name+AppText.tr("“ aus Deinen gespeicherten Alarmstufen entfernen?"))
                .setNegativeButton(AppText.tr("Behalten"),null).setPositiveButton(AppText.tr("Löschen"),(dialog,which) -> {
                    List<AlarmLevel> desired=new ArrayList<>();
                    for (AlarmLevel current:base.levels) if (!current.rule.id.equals(r.id)) desired.add(current);
                    commit(base,desired);
                }).show());
    }
    private void commit(LevelStore.Snapshot base,List<AlarmLevel> desired) {
        try {
            OutputPreferences output=new OutputPreferenceStore(getFilesDir()).load().preferences;
            for (AlarmLevel level:desired) {
                AlarmLevel old=base.find(level.rule.id);
                if (level.rule.enabled && (old==null || !old.rule.enabled)) OutputRequirements.require(Collections.singletonList(level),output);
            }
            store.save(base.revision,desired); RuntimeStateBridge.refresh(getFilesDir(),RuntimeClock.read(this)); LiveAlarmService.settingsChanged();
            Toast.makeText(this,AppText.tr("Alarmstufen gespeichert"),Toast.LENGTH_SHORT).show();
        }
        catch (IllegalArgumentException e) { new AlertDialog.Builder(this).setTitle(AppText.tr("Alarmweg fehlt")).setMessage(e.getMessage()).setPositiveButton(AppText.tr("Schließen"),null).show(); }
        catch (IOException e) { Toast.makeText(this,e instanceof LevelStore.Conflict?e.getMessage():AppText.tr("Speichern fehlgeschlagen; bisherige Einstellungen bleiben erhalten"),Toast.LENGTH_LONG).show(); }
        refresh();
    }
}
