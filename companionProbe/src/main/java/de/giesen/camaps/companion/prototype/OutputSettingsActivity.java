// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

public final class OutputSettingsActivity extends UiActivity {
    private OutputPreferenceStore store;
    private long revision;
    private Switch tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled;
    private EditText interval,template,valueQuiet;
    private SeekBar toneVolume,speechVolume,bellBoost;
    private final List<EditText> trendWords=new ArrayList<>();
    private final List<CheckBox> trendEnabled=new ArrayList<>();
    private LinearLayout trendPanel;
    private DisclosureRow trendDisclosure;
    private Button volumeSpeechPreview;
    private TextView status;
    private Button save,preview;
    private PreviewAudio audio;
    private boolean ready;
    private ArrayList<String> initial;
    private Spinner language;
    private String draftSpeechLanguage="de";
    private AlarmEngine.OutputMode mode() { return valueSpeechEnabled.isChecked()?AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES:alarmSpeechEnabled.isChecked()?AlarmEngine.OutputMode.SPOKEN_ALARMS:AlarmEngine.OutputMode.TONES_ONLY; }
    private Switch channel(LinearLayout parent,String title,boolean checked) {
        Switch control=new Switch(this); control.setText(title); control.setContentDescription(title); control.setChecked(checked); control.setMinHeight(LevelUi.dp(this,48)); parent.addView(control); return control;
    }
    private void restoreMode(AlarmEngine.OutputMode mode) { tonesEnabled.setChecked(true); alarmSpeechEnabled.setChecked(mode!=AlarmEngine.OutputMode.TONES_ONLY); valueSpeechEnabled.setChecked(mode==AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(!SafetyConsent.ready(this))return; store=new OutputPreferenceStore(getFilesDir());
        LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> onBackPressed()); LevelUi.text(body,AppText.tr("Ausgabe"),28);
        LevelUi.button(body,AppText.tr("App-Glockentöne anhören und auswählen"),v -> startActivity(new android.content.Intent(this,ToneSetsActivity.class)));
            LevelUi.text(body,AppText.tr("Die Schalter geben die Ausgabewege Deiner Alarmstufen und Lückenwarnung frei."),14);
        status=LevelUi.text(body,"",15); audio=new PreviewAudio(this,message -> status.setText(message));
        try {
            OutputPreferenceStore.Snapshot snapshot=store.load(); revision=state==null?snapshot.revision:state.getLong("revision");
            OutputPreferences p=snapshot.preferences;
            draftSpeechLanguage=p.speechLanguage;
            LevelUi.text(body,AppText.tr("Sprache der Ansagen"),18); language=new Spinner(this); language.setTag("speech_language");
            ArrayAdapter<String> languages=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Deutsch","English"}); languages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); language.setAdapter(languages); body.addView(language);
            language.setSelection("en".equals(draftSpeechLanguage)?1:0);
            language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id) { draftSpeechLanguage=position==1?"en":"de"; audio.previewLanguage(draftSpeechLanguage); }
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            LevelUi.text(body,AppText.tr("Eigene Texte bleiben beim Sprachwechsel erhalten."),14);
            LevelUi.help(body,AppText.tr("Trend und Ratenalarme"),AppText.tr("Pfeil und Trendwort stammen von CamAPS. Die kritische Fall- oder Anstiegsrate prüft die empfangenen Werte gegen Deine eigene Schwelle, auch ohne Pfeil. Die Änderungsrate im Dashboard ist ein Mittel aus vier Empfangs-Minutenpunkten; der Ratenalarm prüft zusätzlich das letzte Intervall. Ohne neue Werte bleibt nur die Lückenwarnung."));
            tonesEnabled=channel(body,AppText.tr("Ton"),p.tonesEnabled); alarmSpeechEnabled=channel(body,AppText.tr("Alarme sprechen"),p.alarmSpeechEnabled); valueSpeechEnabled=channel(body,AppText.tr("Werte sprechen"),p.valueSpeechEnabled); vibrationEnabled=channel(body,AppText.tr("Vibration"),p.vibrationEnabled);
            LevelUi.text(body,AppText.tr("Vibrationsmuster legst Du je Alarmstufe fest."),14);
            LevelUi.text(body,AppText.tr("Lautstärke"),21);
            toneVolume=volume(body,AppText.tr("Töne"),p.toneVolumePercent);
            speechVolume=volume(body,AppText.tr("Sprache"),p.speechVolumePercent);
            bellBoost=boost(body,p.bellBoostDb);
            LevelUi.text(body,AppText.tr("Boost nur für App-Glockentöne. System-stumm bleibt stumm. Anhören nutzt den Entwurf."),14);
            LinearLayout auditions=new LinearLayout(this); auditions.setGravity(android.view.Gravity.CENTER_VERTICAL); body.addView(auditions);
            LevelUi.text(auditions,AppText.tr("Ton"),14); LevelUi.button(auditions,AppText.tr("Tonlautstärke anhören"),v -> audio.tone(SoundDefaults.uri(this,AlarmEngine.Kind.HIGH),toneVolume.getProgress(),bellBoost.getProgress()));
            LevelUi.text(auditions,AppText.tr("Sprache"),14); volumeSpeechPreview=LevelUi.button(auditions,AppText.tr("Sprachlautstärke anhören"),v -> audio.speak(AppText.in(draftSpeechLanguage,"So laut werden die Ansagen von Glucose Voice Alarm wiedergegeben."),speechVolume.getProgress()));
            interval=LevelUi.field(body,AppText.tr("Abstand der Wertansagen in ganzen Minuten"),p.valueIntervalMinutes==0?"":Integer.toString(p.valueIntervalMinutes),InputType.TYPE_CLASS_NUMBER);
            template=LevelUi.field(body,AppText.tr("Text der Wertansage"),p.valueTemplate,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            template.setSingleLine(false); template.setMinLines(2); template.setMaxLines(4); template.setHorizontallyScrolling(false); template.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            template.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
            TokenButtons.add(body,template,"{wert}","{einheit}","{trend}","{alter_sekunden}");
            valueQuiet=LevelUi.field(body,AppText.tr("Wertansagen nach einem Alarm pausieren (Minuten, 0 = aus)"),Integer.toString(p.valueQuietMinutes),InputType.TYPE_CLASS_NUMBER);
            trendPanel=new LinearLayout(this); trendPanel.setOrientation(LinearLayout.VERTICAL);
            trendDisclosure=DisclosureRow.add(body,AppText.tr("Trendwörter"),trendPanel);
            LevelUi.text(trendPanel,AppText.tr("Haken aus: dieses Trendwort nicht ansagen. Pfeil und Alarmprüfung bleiben unverändert."),14);
            String[] trendLabels={AppText.tr("↗ Steigend"),AppText.tr("↑ Schnell steigend"),AppText.tr("→ Stabil"),AppText.tr("↘ Fallend"),AppText.tr("↓ Schnell fallend"),AppText.tr("⇈ Doppelt aufwärts"),AppText.tr("⇊ Doppelt abwärts")};
            for(int i=0;i<TrendSpeech.COUNT;i++) {
                EditText field=LevelUi.field(trendPanel,trendLabels[i],p.trendSpeech.words.get(i),InputType.TYPE_CLASS_TEXT);
                field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)}); trendWords.add(field);
                trendPanel.removeView(field); LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); trendPanel.addView(row);
                CheckBox enabled=new CheckBox(this); enabled.setTag("trend_speech_"+i); enabled.setContentDescription(trendLabels[i]+" "+AppText.tr("ansagen"));
                enabled.setChecked(p.trendSpeech.enabled.get(i)); enabled.setMinHeight(LevelUi.dp(this,48)); row.addView(enabled,new LinearLayout.LayoutParams(LevelUi.dp(this,48),-2)); trendEnabled.add(enabled);
                row.addView(field,new LinearLayout.LayoutParams(0,-2,1));
                Button sample=LevelUi.button(row,AppText.tr("Trend anhören"),v -> audio.speak(field.getText().toString())); sample.setContentDescription(trendLabels[i]+AppText.tr(" anhören"));
                sample.setEnabled(enabled.isChecked()); enabled.setOnCheckedChangeListener((view,checked) -> { sample.setEnabled(checked); audio.stop(); });
            }
            LevelUi.button(body,AppText.tr("Standardtexte einsetzen"),v -> new android.app.AlertDialog.Builder(this).setTitle(AppText.tr("Standardtexte einsetzen?"))
                    .setMessage(AppText.tr("Wert- und Trendtexte werden ersetzt. Eigene Alarmtexte bleiben erhalten."))
                    .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Ersetzen"),(d,w) -> {
                        template.setText(SpeechTemplate.valueDefault(draftSpeechLanguage)); TrendSpeech defaults=TrendSpeech.defaults(draftSpeechLanguage);
                        for(int i=0;i<TrendSpeech.COUNT;i++) trendWords.get(i).setText(defaults.words.get(i));
                    }).show());
            LevelUi.text(body,AppText.tr("Werttext mit Testwert anhören"),14);
            LinearLayout samples=new LinearLayout(this); samples.setGravity(android.view.Gravity.CENTER_VERTICAL); body.addView(samples);
            preview=LevelUi.button(samples,AppText.tr("Werttext mit Testdaten anhören"),v -> {
                try { if (mode()!=AlarmEngine.OutputMode.TONES_ONLY) audio.speak(SyntheticSpeech.value(template.getText().toString(),words(),draftSpeechLanguage,UnitPreferences.display(this))); }
                catch (IllegalArgumentException e) { status.setText(e.getMessage()); }
            });
            LevelUi.button(samples,AppText.tr("Vorschau stoppen"),v -> audio.stop());
            save=LevelUi.button(body,AppText.tr("Ausgabe-Einstellungen speichern"),v -> save());
            initial=values();
            if (state!=null) {
                trendDisclosure.setExpanded(state.getBoolean("trend_open"));
                ArrayList<String> fields=state.getStringArrayList("fields");
                if (fields!=null && (fields.size()==3 || fields.size()==5 || fields.size()==6 || fields.size()==7 || fields.size()==12 || fields.size()==15 || fields.size()==16 || fields.size()==19 || fields.size()==21 || fields.size()==26)) {
                    restoreMode(AlarmEngine.OutputMode.valueOf(fields.get(0))); interval.setText(fields.get(1)); template.setText(fields.get(2));
                    if (fields.size()>=5) { toneVolume.setProgress(Integer.parseInt(fields.get(3))); speechVolume.setProgress(Integer.parseInt(fields.get(4))); }
                    if (fields.size()>=6) valueQuiet.setText(fields.get(5));
                    if (fields.size()>=7) bellBoost.setProgress(Integer.parseInt(fields.get(6)));
                    int wordCount=fields.size()>=19?7:5,flags=7+wordCount;
                    if (fields.size()>=12) for(int i=0;i<wordCount;i++) trendWords.get(i).setText(fields.get(7+i));
                    if (fields.size()>=flags+3) { tonesEnabled.setChecked(Boolean.parseBoolean(fields.get(flags))); alarmSpeechEnabled.setChecked(Boolean.parseBoolean(fields.get(flags+1))); valueSpeechEnabled.setChecked(Boolean.parseBoolean(fields.get(flags+2))); }
                    if(fields.size()>=flags+4) vibrationEnabled.setChecked(Boolean.parseBoolean(fields.get(flags+3)));
                    if(fields.size()>=19) { draftSpeechLanguage=fields.get(18); language.setSelection("en".equals(draftSpeechLanguage)?1:0); }
                    if(fields.size()==26) for(int i=0;i<TrendSpeech.COUNT;i++) trendEnabled.get(i).setChecked(Boolean.parseBoolean(fields.get(19+i)));
                }
                ArrayList<String> original=state.getStringArrayList("initial");
                if(original!=null) { initial=original.size()==21?new ArrayList<>(original.subList(0,19)):original; if(initial.size()==19) for(int i=0;i<TrendSpeech.COUNT;i++) initial.add("true"); }
            } else if (getIntent().hasExtra("mode")) restoreMode(AlarmEngine.OutputMode.valueOf(getIntent().getStringExtra("mode")));
            if(state==null && getIntent().getBooleanExtra("enable_values",false)) valueSpeechEnabled.setChecked(true);
            CompoundButton.OnCheckedChangeListener changed=(view,checked) -> { audio.stop(); controls(); };
            tonesEnabled.setOnCheckedChangeListener(changed); alarmSpeechEnabled.setOnCheckedChangeListener(changed); valueSpeechEnabled.setOnCheckedChangeListener(changed); vibrationEnabled.setOnCheckedChangeListener(changed);
            controls(); ready=true;
            if (snapshot.revision!=revision) { save.setEnabled(false); status.setText(AppText.tr("Ausgabe-Einstellungen wurden inzwischen geändert. Bitte neu öffnen.")); }
            audio.prepareSpeech();
        } catch (IOException | IllegalArgumentException e) {
            if (save!=null) save.setEnabled(false);
            status.setText(AppText.tr("Ausgabe-Einstellungen konnten nicht geöffnet werden. Gespeicherte Daten bleiben unverändert."));
        }
    }
    private SeekBar volume(LinearLayout body,String name,int initial) {
        TextView label=LevelUi.text(body,name+": "+initial+" %",16);
        SeekBar bar=new SeekBar(this); bar.setMax(100); bar.setProgress(initial); body.addView(bar);
        bar.setContentDescription(name+AppText.tr(" Lautstärke"));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view,int value,boolean fromUser) { label.setText(name+": "+value+" %"); }
            public void onStartTrackingTouch(SeekBar view) { audio.stop(); }
            public void onStopTrackingTouch(SeekBar view) { }
        });
        return bar;
    }
    private SeekBar boost(LinearLayout body,int initial) {
        TextView label=LevelUi.text(body,AppText.tr("Glockenton-Boost: +")+initial+" dB",16);
        SeekBar bar=new SeekBar(this); bar.setMax(ToneCatalog.MAX_BOOST_DB); bar.setProgress(initial); body.addView(bar);
        bar.setContentDescription(AppText.tr("Glockenton-Boost"));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view,int value,boolean fromUser) { label.setText(AppText.tr("Glockenton-Boost: +")+value+" dB"); }
            public void onStartTrackingTouch(SeekBar view) { audio.stop(); }
            public void onStopTrackingTouch(SeekBar view) { }
        });
        return bar;
    }
    private void controls() {
        boolean periodic=valueSpeechEnabled.isChecked(),speech=alarmSpeechEnabled.isChecked() || periodic;
        interval.setEnabled(periodic); template.setEnabled(periodic); preview.setEnabled(speech);
        TokenButtons.enabled(template);
        volumeSpeechPreview.setEnabled(speech);
    }
    private TrendSpeech words() { List<String> words=new ArrayList<>(); List<Boolean> enabled=new ArrayList<>(); for(EditText field:trendWords) words.add(field.getText().toString()); for(CheckBox box:trendEnabled) enabled.add(box.isChecked()); return new TrendSpeech(words,enabled); }
    private ArrayList<String> values() {
        ArrayList<String> result=new ArrayList<>(Arrays.asList(mode().name(),interval.getText().toString(),template.getText().toString(),Integer.toString(toneVolume.getProgress()),Integer.toString(speechVolume.getProgress()),valueQuiet.getText().toString(),Integer.toString(bellBoost.getProgress())));
        for(EditText field:trendWords) result.add(field.getText().toString()); result.add(Boolean.toString(tonesEnabled.isChecked())); result.add(Boolean.toString(alarmSpeechEnabled.isChecked())); result.add(Boolean.toString(valueSpeechEnabled.isChecked())); result.add(Boolean.toString(vibrationEnabled.isChecked())); result.add(draftSpeechLanguage); for(CheckBox box:trendEnabled) result.add(Boolean.toString(box.isChecked())); return result;
    }
    private void save() {
        if (!ready) return;
        try {
            String text=interval.getText().toString().trim();
            if (!text.isEmpty() && !text.matches("[0-9]+")) throw new IllegalArgumentException(AppText.tr("Bitte ganze Minuten eingeben"));
            int minutes;
            try { minutes=text.isEmpty()?0:Integer.parseInt(text); } catch (NumberFormatException e) { throw new IllegalArgumentException(AppText.tr("Intervall außerhalb des speicherbaren Bereichs")); }
            String quiet=valueQuiet.getText().toString().trim(); if (!quiet.matches("[0-9]+")) throw new IllegalArgumentException(AppText.tr("Für die Wertansagepause bitte ganze Minuten eingeben, 0 schaltet sie aus"));
            int quietMinutes; try { quietMinutes=Integer.parseInt(quiet); } catch (NumberFormatException e) { throw new IllegalArgumentException(AppText.tr("Wertansagepause außerhalb des speicherbaren Bereichs")); }
            OutputPreferences desired=new OutputPreferences(mode(),minutes,template.getText().toString(),toneVolume.getProgress(),speechVolume.getProgress(),quietMinutes,bellBoost.getProgress(),words(),tonesEnabled.isChecked(),alarmSpeechEnabled.isChecked(),valueSpeechEnabled.isChecked(),vibrationEnabled.isChecked()).withGap(store.load().preferences.gap).withSpeechLanguage(draftSpeechLanguage).withUnitMode(store.load().preferences.unitMode);
            if (!OutputModeUi.validate(this,desired)) return;
            store.save(revision,desired); PreviewAudio.configurationChanged();
            LiveAlarmService.settingsChanged();
            Toast.makeText(this,AppText.tr("Ausgabe gespeichert"),Toast.LENGTH_LONG).show(); finish();
        } catch (IllegalArgumentException e) { status.setText(e.getMessage()); }
        catch (IOException e) { status.setText(e.getMessage()==null?AppText.tr("Speichern fehlgeschlagen; bitte neu öffnen."):e.getMessage()); }
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); if (!ready) return;
        state.putLong("revision",revision); state.putStringArrayList("fields",values()); state.putStringArrayList("initial",initial);
        state.putBoolean("trend_open",trendPanel.getVisibility()==android.view.View.VISIBLE);
    }
    @Override public void onBackPressed() {
        if (audio!=null) audio.stop();
        if (ready && !initial.equals(values())) new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?"))
                .setMessage(AppText.tr("Die Ausgabe-Einstellungen sind noch nicht gespeichert.")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null)
                .setPositiveButton(AppText.tr("Verwerfen"),(d,w) -> finish()).show();
        else super.onBackPressed();
    }
    @Override public void onPause() { if (audio!=null) audio.pause(); super.onPause(); }
    @Override public void onDestroy() { if (audio!=null) audio.close(); super.onDestroy(); }
}
