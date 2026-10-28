// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Explicit presentation override; never changes source interpretation or therapy settings. */
public final class DeveloperOptionsActivity extends UiActivity {
    private OutputPreferenceStore store;private OutputPreferenceStore.Snapshot snapshot;
    private Spinner mode;private TextView status,source;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return;store=new OutputPreferenceStore(getFilesDir());LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish());LevelUi.text(body,AppText.tr("Entwickleroptionen"),28);
        source=LevelUi.text(body,UnitPreferences.status(this),16);source.setTag("unit_source");
        LevelUi.text(body,AppText.tr("Einmal erkannt, bleibt die CamAPS-Einheit gespeichert. Eine andere Einheit im Empfang wird nicht automatisch übernommen."),14);
        LevelUi.text(body,AppText.tr("Einheit für Anzeige und Ansagen"),18);
        mode=new Spinner(this);mode.setTag("unit_mode");mode.setContentDescription(AppText.tr("Einheit für Anzeige und Ansagen"));
        String[] options=new String[GlucoseUnit.Mode.values().length];for(int i=0;i<options.length;i++)options[i]=GlucoseUnit.Mode.values()[i].title();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);mode.setAdapter(adapter);body.addView(mode);
        status=LevelUi.text(body,"",14);
        try {snapshot=store.load();mode.setSelection(saved==null?snapshot.preferences.unitMode.ordinal():saved.getInt("unit_mode",snapshot.preferences.unitMode.ordinal()));if(saved!=null)snapshot=new OutputPreferenceStore.Snapshot(saved.getLong("revision",snapshot.revision),snapshot.preferences);}
        catch(IOException e) {mode.setEnabled(false);status.setText(AppText.tr("Ausgabe-Einstellungen nicht lesbar"));}
        LevelUi.text(body,AppText.tr("Automatisch folgt der gespeicherten CamAPS-Einheit. Eine feste Auswahl rechnet Dashboard, AOD, Ansagen und Grenzfelder um. Vorhandene Alarmgrenzen behalten ihre Bedeutung."),14);
        LevelUi.button(body,AppText.tr("Einheit übernehmen"),v -> {
            if(snapshot==null)return;GlucoseUnit.Mode chosen=GlucoseUnit.Mode.values()[mode.getSelectedItemPosition()];
            if(chosen==GlucoseUnit.Mode.AUTO)save(chosen);
            else new AlertDialog.Builder(this).setTitle(AppText.tr("Abweichende Einheit verwenden?"))
                    .setMessage(AppText.tr("Anzeige und Ansagen verwenden künftig ")+chosen.title()+AppText.tr(". CamAPS selbst bleibt unverändert. Zahlen und Grenzfelder werden umgerechnet; gespeicherte Alarmgrenzen werden nicht verschoben. Eigene Namen und fest ausgeschriebene Einheiten bleiben unverändert. AOD zeigt weiterhin keine Einheit. Bitte bestätige die gewählte Einheit bewusst."))
                    .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Bestätigen"),(d,w) -> save(chosen)).show();
        });
        LevelUi.button(body,AppText.tr("CamAPS-Einheit neu erkennen"),v -> new AlertDialog.Builder(this).setTitle(AppText.tr("Einheit neu erkennen?"))
                .setMessage(AppText.tr("Schaltet die Live-Ausgabe aus und verwirft das aktuelle Empfangsfenster. Die nächste gültige CamAPS-Meldung legt die Quelleinheit neu fest. Gespeicherte Alarmgrenzen bleiben unverändert."))
                .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Neu erkennen"),(d,w) -> {
                    LivePreferences.set(this,LivePreferences.monitor(this),false);LiveAlarmService.sourceDisconnected();LiveAlarmService.settingsChanged();ProbeState.clear();ProbeState.vectorCandidate=null;
                    status.setText(UnitPreferences.reset(this)?AppText.tr("Warte auf einen neuen gültigen CamAPS-Wert"):AppText.tr("Einheit konnte nicht zurückgesetzt werden"));source.setText(UnitPreferences.status(this));
                }).show());
    }
    private void save(GlucoseUnit.Mode chosen) {
        try {store.save(snapshot.revision,snapshot.preferences.withUnitMode(chosen));PreviewAudio.configurationChanged();LiveAlarmService.settingsChanged();finish();}
        catch(IOException | IllegalArgumentException e) {status.setText(AppText.tr("Einstellungen inzwischen geändert oder nicht speicherbar. Bitte neu öffnen."));}
    }
    @Override public void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);if(mode==null)return;state.putInt("unit_mode",mode.getSelectedItemPosition());if(snapshot!=null)state.putLong("revision",snapshot.revision);}
}
