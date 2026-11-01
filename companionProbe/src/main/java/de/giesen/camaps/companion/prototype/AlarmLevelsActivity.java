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
        super.onCreate(saved); if(!SafetyConsent.ready(this))return; store=new LevelStore(getFilesDir());
        LinearLayout body=LevelUi.screen(this);
        LevelUi.back(body,AppText.tr("Zurück zum Empfang"),v -> finish());
        LevelUi.text(body,AppText.tr("Alarmstufen"),28);
        LevelUi.text(body,AppText.tr("Grenzwerte und schnelle Änderungen. Neue Regeln bleiben aus, bis Du sie einschaltest."),16);

        message=LevelUi.text(body,"",14);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
        LevelUi.primary(body,AppText.tr("Alarm hinzufügen"),v -> add());
    }
    @Override public void onResume() { super.onResume(); if(!SafetyConsent.ready(this))return; refresh(); }
    private void refresh() {
        list.removeAllViews();
        try { snapshot=store.load(); }
        catch (IOException e) {
            snapshot=null; message.setText(AppText.tr("Alarmstufen konnten nicht gelesen werden. Die vorhandene Datei bleibt unverändert. Bitte erneut laden.")); return;
        }
        message.setText(snapshot.levels.size()+AppText.tr(" Stufen gespeichert. Den Live-Betrieb schaltest Du auf dem Dashboard ein."));
        for(AlarmEngine.Kind kind:new AlarmEngine.Kind[]{AlarmEngine.Kind.HIGH,AlarmEngine.Kind.LOW,AlarmEngine.Kind.SOURCE_LOW,AlarmEngine.Kind.PERSISTENT_HIGH,AlarmEngine.Kind.FALLING,AlarmEngine.Kind.RISING}) {
            List<AlarmLevel> group=new ArrayList<>();
            for(AlarmLevel level:snapshot.levels)if(level.rule.kind==kind)group.add(level);
            group.sort((x,y)->{int c=kind==AlarmEngine.Kind.LOW?Double.compare(y.rule.threshold,x.rule.threshold):Double.compare(x.rule.threshold,y.rule.threshold);
                if(c==0)c=Long.compare(x.rule.durationMinutes,y.rule.durationMinutes);if(c==0)c=x.name.compareToIgnoreCase(y.name);return c==0?x.rule.id.compareTo(y.rule.id):c;});
            for(AlarmLevel level:group)row(level);
        }
        gapRow();
    }
    private void add(){
        List<AlarmEngine.Kind> choices=new ArrayList<>();List<String> labels=new ArrayList<>();
        if(snapshot!=null && snapshot.levels.size()<LevelStore.MAX_LEVELS)for(AlarmEngine.Kind kind:new AlarmEngine.Kind[]{AlarmEngine.Kind.HIGH,AlarmEngine.Kind.LOW,AlarmEngine.Kind.SOURCE_LOW,AlarmEngine.Kind.PERSISTENT_HIGH,AlarmEngine.Kind.FALLING,AlarmEngine.Kind.RISING}){choices.add(kind);labels.add(LevelForm.kindLabel(kind));}
        labels.add(AppText.tr("Datenlücken"));
        new AlertDialog.Builder(this).setTitle(AppText.tr("Alarmart auswählen")).setItems(labels.toArray(new String[0]),(d,which)->{
            if(which==choices.size())startActivity(new Intent(this,GapSettingsActivity.class));
            else startActivity(new Intent(this,AlarmLevelActivity.class).putExtra("kind",choices.get(which).name()));
        }).setNegativeButton(AppText.tr("Abbrechen"),null).show();
    }
    private void gapRow(){
        try{OutputPreferenceStore store=new OutputPreferenceStore(getFilesDir());OutputPreferenceStore.Snapshot base=store.load();GapSettings g=base.preferences.gap;
            LinearLayout card=DashboardUi.card(this,list);card.setTag("alarm_gap");
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(heading);TextView title=LevelUi.text(heading,AppText.tr("Datenlücken")+" · "+g.delayMinutes+" "+AppText.tr("Minuten"),19);title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            Switch enabled=new Switch(this);enabled.setContentDescription(AppText.tr("Datenlücken")+" · "+AppText.tr("Aktiv"));enabled.setMinHeight(LevelUi.dp(this,48));enabled.setChecked(g.enabled);heading.addView(enabled);
            enabled.setOnCheckedChangeListener((v,on)->{try{GapSettings desired=new GapSettings(on,g.onlyAfterFast,g.delayMinutes,g.repeatMinutes,g.tone,g.speech,g.toneUri,g.vibrationPattern,g.speechTemplate);
                desired.require(base.preferences);store.save(base.revision,base.preferences.withGap(desired));PreviewAudio.configurationChanged();LiveAlarmService.settingsChanged();
            }catch(IOException | IllegalArgumentException e){new AlertDialog.Builder(this).setTitle(AppText.tr("Alarmweg fehlt")).setMessage(AppText.tr(e.getMessage())).setPositiveButton(AppText.tr("Schließen"),null).show();}refresh();});
            LevelUi.navigation(card,AppText.tr("Bearbeiten"),v->startActivity(new Intent(this,GapSettingsActivity.class)));
        }catch(IOException e){LevelUi.text(list,AppText.tr("Datenlücken-Einstellungen nicht lesbar"),14);}
    }
    private void row(AlarmLevel level) {
        final LevelStore.Snapshot base=snapshot;
        LinearLayout card=DashboardUi.card(this,list);
        card.setTag("alarm_"+level.rule.id);
        AlarmEngine.Rule r=level.rule;
        GlucoseUnit unit=UnitPreferences.display(this);
        String summary=(r.kind==AlarmEngine.Kind.SOURCE_LOW?AppText.tr("Roter Quellpfeil · Wert oberhalb der aktiven Niedrig-Grenzen"):(LevelForm.isRate(r.kind)?AppText.tr("Änderungsrate: "):AppText.tr("Grenze: "))+unit.threshold(r.threshold,LevelForm.isRate(r.kind))
                +" "+unit.label+(LevelForm.isRate(r.kind)?AppText.tr(" pro Minute"):""))
                +(r.kind==AlarmEngine.Kind.PERSISTENT_HIGH?AppText.tr(" · länger als ")+r.durationMinutes+" Minuten":"")
                +"\nTon "+(r.outputs.tone?AppText.tr("ein"):AppText.tr("aus"))+" · Sprache "+(r.outputs.speech?AppText.tr("ein"):AppText.tr("aus"))
                +" · Vibration "+(level.vibrationPattern==null?AppText.tr("aus"):AppText.tr(VibrationCatalog.valueOf(level.vibrationPattern).label))
                +(level.suppressRecoveryRepeats?AppText.tr("\nErholung: Wiederholungen aussetzen"):"")
                +(r.allDay?AppText.tr("\nGanztägig"):String.format(Locale.GERMANY,AppText.tr("\nZeitfenster %02d:%02d bis %02d:%02d"),r.startMinute/60,r.startMinute%60,r.endMinute/60,r.endMinute%60))
                +"\n"+LevelForm.timingSummary(r);
        String title=LevelForm.kindLabel(r.kind)+(r.kind==AlarmEngine.Kind.SOURCE_LOW?"":LevelForm.isRate(r.kind)?" · "+unit.threshold(r.threshold,true)+" "+unit.label+AppText.tr(" pro Minute"):" · "+AppText.tr("Grenze")+" "+unit.threshold(r.threshold,false)+" "+unit.label);
        if(r.kind==AlarmEngine.Kind.PERSISTENT_HIGH)title+=" · "+r.durationMinutes+" "+AppText.tr("Minuten");
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(heading);
        TextView titleView=LevelUi.text(heading,title,19);titleView.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        if(!level.name.equals(LevelForm.kindLabel(r.kind)))LevelUi.text(card,level.name,14);
        Switch enabled=new Switch(this);enabled.setContentDescription(title+" · "+AppText.tr("Aktiv"));enabled.setMinHeight(LevelUi.dp(this,48));enabled.setChecked(r.enabled);heading.addView(enabled);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);LevelUi.text(details,summary,15);
        boolean stacked=getResources().getConfiguration().fontScale>1.3;
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);actions.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(actions);
        DisclosureRow disclosure=new DisclosureRow(this,AppText.tr("Details"),details);disclosure.setTag("alarm_details_"+r.id);actions.addView(disclosure,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        Button edit=LevelUi.navigation(actions,AppText.tr("Bearbeiten"),v -> startActivity(new Intent(this,AlarmLevelActivity.class).putExtra("id",r.id)));edit.setLayoutParams(stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));card.addView(details);
        enabled.setOnCheckedChangeListener((view,checked) -> {
            List<AlarmLevel> desired=new ArrayList<>();
            for (AlarmLevel current:base.levels) desired.add(current.rule.id.equals(r.id)?current.withEnabled(checked):current);
            commit(base,desired);
        });
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
