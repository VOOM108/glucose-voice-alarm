// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;

/** Foreground, button-driven virtual clock. Own cache settings/state, never real observations. */
public final class SyntheticAlarmActivity extends UiActivity {
    private File directory;
    private PreviewAudio audio;
    private AlarmOutputFlow flow;
    private SyntheticAlarmScenario scenario;
    private TextView status,clock;
    private Button snooze;
    private long offset;
    private boolean condition=true;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish()); LevelUi.text(body,AppText.tr("Alarmtest mit synthetischen Daten"),26);
        LevelUi.text(body,AppText.tr("Nur feste Beispielregeln, eine virtuelle Uhr und getrennte Testdateien im Cache. Echte Alarmstufen, Pausen, Werte und CamAPS bleiben unberührt. Es läuft kein Test im Hintergrund weiter."),16);
        LevelUi.text(body,AppText.tr("Jeder Ausgabestart erfolgt per Knopfdruck. Ein Testton dauert höchstens acht Sekunden; danach folgt gegebenenfalls die deutlich als Test bezeichnete Sprache. Androids Lautstärke und Nicht stören werden nicht verändert."),15);
        LevelUi.text(body,AppText.tr("Eigene Untergrenze und intelligente Trendunterdrückung sind in diesen Beispielen nicht aktiv; die noch offenen Rückkehrregeln werden hier nicht festgelegt."),14);
        status=LevelUi.text(body,"",15); clock=LevelUi.text(body,"",16);
        audio=new PreviewAudio(this,message -> status.setText(message));
        directory=new File(getCacheDir(),"alarm-output-demo-"+UUID.randomUUID());
        Spinner kinds=new Spinner(this);
        List<String> labels=new ArrayList<>(); for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) labels.add(LevelForm.kindLabel(kind));
        kinds.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels)); body.addView(kinds);
        LevelUi.button(body,AppText.tr("Beispiel neu beginnen"),v -> begin(AlarmEngine.Kind.values()[kinds.getSelectedItemPosition()]));
        LevelUi.button(body,AppText.tr("Testdaten prüfen und Alarm versuchen"),v -> step(false)).setTag("synthetic_alarm_try");
        LevelUi.button(body,AppText.tr("Eine Testminute weiter, dann prüfen"),v -> step(true));
        CheckBox met=LevelUi.check(body,AppText.tr("Testbedingung erfüllt"),true);
        met.setOnCheckedChangeListener((button,value) -> { if (flow!=null) flow.stop(); audio.stop(); condition=value; });
        snooze=LevelUi.button(body,AppText.tr("Testalarm für fünf Testminuten snoozen"),v -> {
            if (flow!=null) status.setText(label(flow.snooze(scenario.level.rule.id,5,scenario.clock(offset))));
        });
        LevelUi.button(body,AppText.tr("Testsitzung neu laden (ohne Ausgabe)"),v -> {
            audio.stop(); if (scenario!=null) { openFlow(); if (flow!=null) status.setText(AppText.tr("Testsitzung neu geladen. Gespeicherte Testpause bleibt; für eine Ausgabe erst neue Testdaten prüfen.")); }
        });
        LevelUi.button(body,AppText.tr("Periodische Testwertansage prüfen"),v -> {
            if (flow!=null) status.setText(label(flow.value(scenario.clock(offset))));
        });
        LevelUi.button(body,AppText.tr("Test stoppen"),v -> { if (flow!=null) flow.stop(); audio.stop(); status.setText(AppText.tr("Testausgabe gestoppt. Kein automatischer Neustart.")); });
        begin(AlarmEngine.Kind.HIGH); audio.prepareSpeech();
    }
    private void begin(AlarmEngine.Kind kind) {
        audio.stop(); cleanup();
        scenario=new SyntheticAlarmScenario(kind); offset=0;
        try {
            new LevelStore(directory).save(0,Collections.singletonList(scenario.level)); openFlow();
            if (flow==null) return;
            snooze.setEnabled(LevelForm.hasSnooze(kind)); clock.setText("Testzeit: Beginn · "+LevelForm.kindLabel(kind));
            status.setText(AppText.tr("Getrenntes Beispiel bereit. Dauerhoch braucht weitere Testminuten; schnelle Änderungen brauchen keine Snoozezeit."));
        } catch (IOException e) { flow=null; status.setText(AppText.tr("Testdateien nicht verfügbar. Echte Dateien werden nicht angefasst.")); }
    }
    private void openFlow() {
        try {
            LevelStore.Snapshot settings=new LevelStore(directory).load();
            OutputPreferences preferences=new OutputPreferenceStore(getFilesDir()).load().preferences;
            flow=new AlarmOutputFlow(new AlarmStateStore(directory),settings.levels,SyntheticAlarmScenario.limits(),preferences,audio);
            flow.setOutputUnit(UnitPreferences.display(this,preferences));
        } catch (IOException e) { flow=null; status.setText(AppText.tr("Test-/Ausgabe-Einstellungen nicht lesbar; keine Ausgabe.")); }
    }
    private void step(boolean advance) {
        if (flow==null || scenario==null) return;
        audio.stop(); if (advance) offset+=SyntheticAlarmScenario.MINUTE;
        AlarmOutputFlow.Outcome outcome=flow.evaluate(AlarmOutputFlow.Origin.SYNTHETIC,scenario.points(offset,condition),scenario.clock(offset),720,
                SyntheticAlarmScenario.BASE-10*SyntheticAlarmScenario.MINUTE,scenario.trend(condition));
        if (outcome==AlarmOutputFlow.Outcome.ACCEPTED) outcome=flow.alarm(scenario.level.rule.id,scenario.clock(offset));
        clock.setText("Testzeit: "+offset/60000+AppText.tr(" Minuten nach Beginn · ")+LevelForm.kindLabel(scenario.kind)); status.setText(label(outcome));
    }
    private String label(AlarmOutputFlow.Outcome outcome) {
        switch(outcome) {
            case ACCEPTED: return AppText.tr("Testausgabe an Android übergeben oder Testpause gespeichert. Das bestätigt noch keine Hörbarkeit.");
            case NOT_DUE: return AppText.tr("Noch nicht fällig: Wiederholung, Snooze, Ereignissperre oder Wertintervall.");
            case CONDITION_FALSE: return AppText.tr("Testbedingung noch nicht erfüllt oder eine andere Grenzstufe hat Vorrang.");
            case OUTPUT_UNAVAILABLE: return AppText.tr("Audio noch nicht bereit. Bitte Offline-Stimme prüfen/vorbereiten und den Test erneut starten; kein späteres automatisches Abspielen.");
            case DISABLED: return AppText.tr("Diese Ausgabe ist im aktuellen Modus ausgeschaltet.");
            case DATA_BLOCKED: return AppText.tr("Erst neue gültige Testdaten prüfen; kein Abspielen aus altem Zustand.");
            case PAUSED: return AppText.tr("Testlauf pausiert.");
            case SUPPRESSED: return AppText.tr("Testausgabe durch eine ausdrücklich gesetzte Regel unterdrückt: ")+flow.suppressionReason();
            case ATTEMPT_FAILED: return AppText.tr("Ausgabeversuch fehlgeschlagen. Reservierte Frist bleibt erhalten; kein sofortiges Wiederholen nach Neustart.");
            case LIVE_BLOCKED: return AppText.tr("Live-Ausgabe gesperrt.");
            default: return AppText.tr("Speicher/Zustand blockiert. Keine Testausgabe.");
        }
    }
    private void cleanup() {
        if (directory==null) return;
        // Only these known files in this Activity's unique cache directory, no recursive deletion.
        for (String name:new String[]{LevelStore.FILE_NAME,AlarmStateStore.FILE_NAME}) {
            new File(directory,name).delete(); new File(directory,name+".new").delete();
        }
        directory.delete();
    }
    @Override public void onPause() { if (flow!=null) flow.stop(); if (audio!=null) audio.pause(); super.onPause(); }
    @Override public void onDestroy() { if (audio!=null) audio.close(); cleanup(); super.onDestroy(); }
}
