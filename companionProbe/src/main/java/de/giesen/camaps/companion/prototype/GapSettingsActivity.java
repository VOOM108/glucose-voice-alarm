// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

/** One clearly separated page for loss-of-observation warnings. */
public final class GapSettingsActivity extends UiActivity {
    private CheckBox enabled,onlyFast,tone,speech;
    private EditText delay,repeat,speechTemplate;
    private String speechLanguage;
    private Spinner vibration;
    private TextView status,toneLabel;
    private String toneUri;
    private long revision;
    private PreviewAudio audio;
    private boolean ready;
    private ArrayList<String> initial;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> onBackPressed()); LevelUi.text(body,AppText.tr("Datenlücken"),28);
        LevelUi.text(body,AppText.tr("Warnt bei fehlenden Werten und nennt den zuletzt bekannten Verlauf. Keine Schätzung des aktuellen Werts."),16);
        status=LevelUi.text(body,"",15); audio=new PreviewAudio(this,text -> status.setText(text));
        try {
            OutputPreferenceStore.Snapshot snapshot=new OutputPreferenceStore(getFilesDir()).load(); revision=saved==null?snapshot.revision:saved.getLong("revision"); GapSettings p=snapshot.preferences.gap; toneUri=p.toneUri;
            speechLanguage=snapshot.preferences.speechLanguage;
            enabled=LevelUi.check(body,AppText.tr("Lückenwarnung aktiv"),p.enabled);
            LinearLayout timing=DashboardUi.card(this,body); LevelUi.text(timing,AppText.tr("Wann warnen?"),21);
            delay=LevelUi.field(timing,AppText.tr("Ohne neuen Wert seit … Minuten"),Integer.toString(p.delayMinutes),InputType.TYPE_CLASS_NUMBER);
            onlyFast=LevelUi.check(timing,AppText.tr("Nur nach schnellem Trend"),p.onlyAfterFast);
            LevelUi.text(timing,AppText.tr("Aus: auch bei zuvor stabilem oder unbekanntem Verlauf warnen."),14);
            repeat=LevelUi.field(timing,AppText.tr("Erneut warnen nach … Minuten"),Integer.toString(p.repeatMinutes),InputType.TYPE_CLASS_NUMBER);
            LevelUi.text(timing,AppText.tr("0 = nur einmal je Lücke. Neue gültige Werte beenden die Lücke."),14);
            LinearLayout outputs=DashboardUi.card(this,body); LevelUi.text(outputs,AppText.tr("Wie warnen?"),21);
            tone=LevelUi.check(outputs,AppText.tr("Ton"),p.tone); toneLabel=LevelUi.text(outputs,"",14);
            LinearLayout sounds=new LinearLayout(this); outputs.addView(sounds);
            Button choose=LevelUi.button(sounds,AppText.tr("Ton auswählen"),v -> chooseTone()); choose.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            LevelUi.button(sounds,AppText.tr("Ton anhören"),v -> audio.tone(toneUri));
            speech=LevelUi.check(outputs,AppText.tr("Sprechen"),p.speech);
            speechTemplate=LevelUi.field(outputs,AppText.tr("Text der Lückenwarnung"),p.speechTemplate,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            speechTemplate.setId(R.id.gap_speech_template); speechTemplate.setHint(GapSpeechTemplate.defaults(speechLanguage)); speechTemplate.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
            speechTemplate.setSingleLine(false); speechTemplate.setMinLines(2); speechTemplate.setMaxLines(5); speechTemplate.setHorizontallyScrolling(false); speechTemplate.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            TokenButtons.add(outputs,speechTemplate,"{dauer_minuten}","{trend}","{status}");
            LevelUi.help(outputs,AppText.tr("Zu den Variablen"),AppText.tr("Leer verwendet den Standard. Dauer ist die Länge der Lücke. Trend und Status beschreiben den letzten bekannten Verlauf als vollständige Sätze; sie bleiben ohne sichere Angabe leer. Ein aktueller Wert ist nicht einsetzbar."));
            LevelUi.button(outputs,AppText.tr("Sprachtext mit Testdaten anhören"),v -> {
                try {
                    boolean en="en".equals(speechLanguage);
                    String text=GapSpeechTemplate.render(speechTemplate.getText().toString(),speechLanguage,4,en?"Last trend: falling fast.":"Zuletzt schnell fallend.","");
                    audio.speak((en?"This is a gap warning test. ":"Dies ist ein Test der Lückenwarnung. ")+text);
                } catch(IllegalArgumentException e) { status.setText(AppText.tr(e.getMessage())); }
            });
            LevelUi.text(outputs,AppText.tr("Vibration"),16); vibration=new Spinner(this); vibration.setContentDescription(AppText.tr("Vibrationsmuster der Lückenwarnung"));
            List<String> patterns=new ArrayList<>(); patterns.add(AppText.tr("Aus")); for(VibrationCatalog pattern:VibrationCatalog.values()) patterns.add(AppText.tr(pattern.label));
            vibration.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,patterns)); outputs.addView(vibration);
            vibration.setSelection(p.vibrationPattern==null?0:VibrationCatalog.valueOf(p.vibrationPattern).ordinal()+1);
            LinearLayout previews=new LinearLayout(this); outputs.addView(previews);
            LevelUi.button(previews,AppText.tr("Vibration testen"),v -> { if(pattern()!=null) audio.vibrate(pattern()); }); LevelUi.button(previews,AppText.tr("Vorschau stoppen"),v -> audio.stop());
            LevelUi.help(body,AppText.tr("Schalter auf dem Dashboard"),AppText.tr("Ton, Alarme sprechen und Vibration müssen auch auf dem Dashboard eingeschaltet sein. Ausgabe aktiv und eine globale Pause gelten ebenfalls für die Lückenwarnung."));
            LevelUi.button(body,AppText.tr("Speichern"),v -> save()); initial=fields();
            if(saved!=null) { ArrayList<String> values=saved.getStringArrayList("fields"); if(values!=null && (values.size()==8 || values.size()==9)) restore(values); initial=saved.getStringArrayList("initial"); }
            toneName(); ready=true; audio.prepareSpeech();
        } catch(IOException | IllegalArgumentException e) { status.setText(AppText.tr("Einstellungen nicht lesbar. Vorhandene Daten bleiben erhalten.")); }
    }
    private String pattern() { return vibration.getSelectedItemPosition()==0?null:VibrationCatalog.values()[vibration.getSelectedItemPosition()-1].name(); }
    private ArrayList<String> fields() { return new ArrayList<>(Arrays.asList(Boolean.toString(enabled.isChecked()),Boolean.toString(onlyFast.isChecked()),delay.getText().toString(),repeat.getText().toString(),Boolean.toString(tone.isChecked()),Boolean.toString(speech.isChecked()),toneUri,Integer.toString(vibration.getSelectedItemPosition()),speechTemplate.getText().toString())); }
    private void restore(List<String> v) { enabled.setChecked(Boolean.parseBoolean(v.get(0))); onlyFast.setChecked(Boolean.parseBoolean(v.get(1))); delay.setText(v.get(2)); repeat.setText(v.get(3)); tone.setChecked(Boolean.parseBoolean(v.get(4))); speech.setChecked(Boolean.parseBoolean(v.get(5))); toneUri=v.get(6); vibration.setSelection(Integer.parseInt(v.get(7))); if(v.size()==9)speechTemplate.setText(v.get(8)); }
    private void toneName() { String set=ToneCatalog.setOf(toneUri); toneLabel.setText(set==null?AppText.tr("Eigener Android-Ton"):ToneCatalog.setName(set)+AppText.tr(" · langer Ton")); }
    private void chooseTone() {
        audio.stop(); new AlertDialog.Builder(this).setTitle(AppText.tr("Ton der Lückenwarnung")).setItems(new String[]{AppText.tr("Samt"),AppText.tr("Klar"),AppText.tr("Rund"),AppText.tr("Eigener Ton …")},(d,index) -> {
            if(index<3) { toneUri=ToneCatalog.uri(ToneCatalog.SETS.get(index),AlarmEngine.Kind.PERSISTENT_HIGH); toneName(); return; }
            Intent intent=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(toneUri));
            try { startActivityForResult(intent,51); } catch(android.content.ActivityNotFoundException e) { status.setText(AppText.tr("Androids Tonauswahl ist nicht verfügbar.")); }
        }).setNegativeButton(AppText.tr("Abbrechen"),null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(request==51 && result==RESULT_OK && data!=null) { Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI); if(uri!=null) { toneUri=uri.toString(); toneName(); } }
    }
    private void save() {
        if(!ready) return;
        try {
            if(!delay.getText().toString().matches("[0-9]+") || !repeat.getText().toString().matches("[0-9]+")) throw new IllegalArgumentException(AppText.tr("Bitte ganze Minuten eingeben"));
            GapSettings gap=new GapSettings(enabled.isChecked(),onlyFast.isChecked(),Integer.parseInt(delay.getText().toString()),Integer.parseInt(repeat.getText().toString()),tone.isChecked(),speech.isChecked(),toneUri,pattern(),speechTemplate.getText().toString());
            OutputPreferenceStore store=new OutputPreferenceStore(getFilesDir()); OutputPreferences desired=store.load().preferences.withGap(gap);
            if(!OutputModeUi.validate(this,desired)) return; store.save(revision,desired); PreviewAudio.configurationChanged(); LiveAlarmService.settingsChanged();
            Toast.makeText(this,AppText.tr("Lückenwarnung gespeichert"),Toast.LENGTH_SHORT).show(); finish();
        } catch(IOException | IllegalArgumentException e) { status.setText(e.getMessage()); }
    }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); if(ready) { state.putLong("revision",revision); state.putStringArrayList("fields",fields()); state.putStringArrayList("initial",initial); } }
    @Override public void onBackPressed() {
        if(audio!=null) audio.stop(); if(ready && initial!=null && !initial.equals(fields())) new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null).setPositiveButton(AppText.tr("Verwerfen"),(d,w) -> finish()).show(); else super.onBackPressed();
    }
    @Override public void onPause() { if(audio!=null) audio.pause(); super.onPause(); }
    @Override public void onDestroy() { if(audio!=null) audio.close(); super.onDestroy(); }
}
