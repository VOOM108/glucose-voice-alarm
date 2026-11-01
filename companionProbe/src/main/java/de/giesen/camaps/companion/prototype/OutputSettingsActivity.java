// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

public final class OutputSettingsActivity extends UiActivity {
    private OutputPreferenceStore store;private long revision;
    private CheckBox tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled;
    private EditText interval,template,valueQuiet;private SeekBar toneVolume,speechVolume,bellBoost;
    private Button preview,volumeSpeechPreview,save;private TextView status;private PreviewAudio audio;
    private Spinner language;private String draftSpeechLanguage;private TrendSpeech savedWords;
    private boolean ready;private ArrayList<String> initial;
    private AlarmEngine.OutputMode mode(){return valueSpeechEnabled.isChecked()?AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES:alarmSpeechEnabled.isChecked()?AlarmEngine.OutputMode.SPOKEN_ALARMS:AlarmEngine.OutputMode.TONES_ONLY;}
    private CheckBox channel(LinearLayout parent,String name,boolean on){CheckBox c=new CheckBox(this);c.setText(name);c.setContentDescription(name);c.setChecked(on);c.setMinHeight(LevelUi.dp(this,48));parent.addView(c);return c;}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!SafetyConsent.ready(this))return;
        store=new OutputPreferenceStore(getFilesDir());LinearLayout body=LevelUi.screen(this);
        LevelUi.back(body,AppText.tr("Zurück"),v->onBackPressed());LevelUi.text(body,AppText.tr("Ausgabe"),28);
        status=LevelUi.text(body,"",15);audio=new PreviewAudio(this,text->status.setText(text));
        try{OutputPreferenceStore.Snapshot snapshot=store.load();revision=state==null?snapshot.revision:state.getLong("revision",snapshot.revision);
            OutputPreferences p=snapshot.preferences;savedWords=p.trendSpeech;draftSpeechLanguage=p.speechLanguage;
            LevelUi.text(body,AppText.tr("Telefon"),21);
            tonesEnabled=channel(body,AppText.tr("Ton"),p.tonesEnabled);alarmSpeechEnabled=channel(body,AppText.tr("Alarme sprechen"),p.alarmSpeechEnabled);
            valueSpeechEnabled=channel(body,AppText.tr("Werte sprechen"),p.valueSpeechEnabled);vibrationEnabled=channel(body,AppText.tr("Vibration"),p.vibrationEnabled);
            LevelUi.navigation(body,AppText.tr("Glockentöne"),v->startActivity(new Intent(this,ToneSetsActivity.class)));
            LevelUi.text(body,AppText.tr("Sprache der Ansagen"),18);language=new Spinner(this);language.setTag("speech_language");
            language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Deutsch","English"}));body.addView(language);language.setSelection("en".equals(draftSpeechLanguage)?1:0);
            language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int position,long id){draftSpeechLanguage=position==1?"en":"de";audio.previewLanguage(draftSpeechLanguage);}public void onNothingSelected(AdapterView<?> p){}});
            LevelUi.text(body,AppText.tr("Eigene Texte bleiben beim Sprachwechsel erhalten."),14);
            LevelUi.text(body,AppText.tr("Lautstärke"),21);toneVolume=volume(body,AppText.tr("Töne"),p.toneVolumePercent);speechVolume=volume(body,AppText.tr("Sprache"),p.speechVolumePercent);bellBoost=boost(body,p.bellBoostDb);
            LevelUi.help(body,AppText.tr("Zum Boost"),AppText.tr("Boost nur für App-Glockentöne. System-stumm bleibt stumm. Anhören nutzt den Entwurf."));
            LinearLayout tonePreview=new LinearLayout(this);body.addView(tonePreview);LevelUi.text(tonePreview,AppText.tr("Ton"),16);
            LevelUi.play(tonePreview,AppText.tr("Tonlautstärke anhören"),v->audio.tone(SoundDefaults.uri(this,AlarmEngine.Kind.HIGH),toneVolume.getProgress(),bellBoost.getProgress()));LevelUi.stop(tonePreview,AppText.tr("Vorschau stoppen"),v->audio.stop());
            LinearLayout speechPreview=new LinearLayout(this);body.addView(speechPreview);LevelUi.text(speechPreview,AppText.tr("Sprache"),16);
            volumeSpeechPreview=LevelUi.play(speechPreview,AppText.tr("Sprachlautstärke anhören"),v->audio.speak(AppText.in(draftSpeechLanguage,"So laut werden die Ansagen von Glucose Voice Alarm wiedergegeben."),speechVolume.getProgress()));LevelUi.stop(speechPreview,AppText.tr("Vorschau stoppen"),v->audio.stop());
            LevelUi.text(body,AppText.tr("Wertansagen"),21);
            interval=LevelUi.field(body,AppText.tr("Abstand der Wertansagen in ganzen Minuten"),p.valueIntervalMinutes==0?"":Integer.toString(p.valueIntervalMinutes),InputType.TYPE_CLASS_NUMBER);interval.setTag("value_interval");
            template=LevelUi.field(body,AppText.tr("Text der Wertansage"),p.valueTemplate,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            template.setSingleLine(false);template.setMinLines(2);template.setMaxLines(4);template.setHorizontallyScrolling(false);template.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
            TokenButtons.add(body,template,"{wert}","{einheit}","{trend}","{farbe}","{alter_sekunden}");
            valueQuiet=LevelUi.field(body,AppText.tr("Wertansagen nach einem Alarm pausieren (Minuten, 0 = aus)"),Integer.toString(p.valueQuietMinutes),InputType.TYPE_CLASS_NUMBER);
            LinearLayout samples=new LinearLayout(this);body.addView(samples);TextView valuePreviewLabel=LevelUi.text(samples,AppText.tr("Wertansage testen"),16);valuePreviewLabel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            preview=LevelUi.play(samples,AppText.tr("Werttext mit Testdaten anhören"),v->{try{audio.speak(SyntheticSpeech.value(template.getText().toString(),savedWords,draftSpeechLanguage,UnitPreferences.display(this)));}catch(IllegalArgumentException e){status.setText(e.getMessage());}});
            LevelUi.stop(samples,AppText.tr("Vorschau stoppen"),v->audio.stop());
            LevelUi.button(body,AppText.tr("Standardtext einsetzen"),v->new AlertDialog.Builder(this).setTitle(AppText.tr("Standardtext einsetzen?"))
                    .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Ersetzen"),(d,w)->template.setText(SpeechTemplate.valueDefault(draftSpeechLanguage))).show());
            save=LevelUi.primary(body,AppText.tr("Ausgabe-Einstellungen speichern"),v->save());
            LevelUi.text(body,AppText.tr("Weitere Ausgabeziele"),21);
            LevelUi.navigation(body,AppText.tr("Sperrbildschirm und Benachrichtigungen"),v->startActivity(new Intent(this,DisplaySettingsActivity.class)));
            LevelUi.navigation(body,AppText.tr("Uhr"),v->startActivity(new Intent(this,WatchSettingsActivity.class)));
            initial=values();if(state!=null){ArrayList<String> draft=state.getStringArrayList("fields");if(draft!=null && draft.size()==11)restore(draft);ArrayList<String> original=state.getStringArrayList("initial");if(original!=null && original.size()==11)initial=original;}
            if(state==null && getIntent().hasExtra("mode")){AlarmEngine.OutputMode mode=AlarmEngine.OutputMode.valueOf(getIntent().getStringExtra("mode"));tonesEnabled.setChecked(true);alarmSpeechEnabled.setChecked(mode!=AlarmEngine.OutputMode.TONES_ONLY);valueSpeechEnabled.setChecked(mode==AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES);}
            if(state==null && getIntent().getBooleanExtra("enable_values",false))valueSpeechEnabled.setChecked(true);
            CompoundButton.OnCheckedChangeListener changed=(v,on)->{audio.stop();controls();};tonesEnabled.setOnCheckedChangeListener(changed);alarmSpeechEnabled.setOnCheckedChangeListener(changed);valueSpeechEnabled.setOnCheckedChangeListener(changed);vibrationEnabled.setOnCheckedChangeListener(changed);
            ready=true;controls();if(revision!=snapshot.revision){save.setEnabled(false);status.setText(AppText.tr("Ausgabe-Einstellungen wurden inzwischen geändert. Bitte neu öffnen."));}audio.prepareSpeech();
        }catch(IOException | IllegalArgumentException e){status.setText(AppText.tr("Ausgabe-Einstellungen konnten nicht geöffnet werden. Gespeicherte Daten bleiben unverändert."));}
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
    private void controls(){boolean periodic=valueSpeechEnabled.isChecked(),speech=periodic || alarmSpeechEnabled.isChecked();interval.setEnabled(periodic);template.setEnabled(periodic);TokenButtons.enabled(template);preview.setEnabled(speech);volumeSpeechPreview.setEnabled(speech);}
    private ArrayList<String> values(){return new ArrayList<>(Arrays.asList(Boolean.toString(tonesEnabled.isChecked()),Boolean.toString(alarmSpeechEnabled.isChecked()),Boolean.toString(valueSpeechEnabled.isChecked()),Boolean.toString(vibrationEnabled.isChecked()),interval.getText().toString(),template.getText().toString(),Integer.toString(toneVolume.getProgress()),Integer.toString(speechVolume.getProgress()),valueQuiet.getText().toString(),Integer.toString(bellBoost.getProgress()),draftSpeechLanguage));}
    private void restore(List<String> v){tonesEnabled.setChecked(Boolean.parseBoolean(v.get(0)));alarmSpeechEnabled.setChecked(Boolean.parseBoolean(v.get(1)));valueSpeechEnabled.setChecked(Boolean.parseBoolean(v.get(2)));vibrationEnabled.setChecked(Boolean.parseBoolean(v.get(3)));interval.setText(v.get(4));template.setText(v.get(5));toneVolume.setProgress(Integer.parseInt(v.get(6)));speechVolume.setProgress(Integer.parseInt(v.get(7)));valueQuiet.setText(v.get(8));bellBoost.setProgress(Integer.parseInt(v.get(9)));draftSpeechLanguage=v.get(10);language.setSelection("en".equals(draftSpeechLanguage)?1:0);}
    private void save(){if(!ready)return;try{String n=interval.getText().toString().trim(),q=valueQuiet.getText().toString().trim();if(!n.isEmpty() && !n.matches("[0-9]+") || !q.matches("[0-9]+"))throw new IllegalArgumentException(AppText.tr("Bitte ganze Minuten eingeben"));
            OutputPreferences current=store.load().preferences;OutputPreferences desired=new OutputPreferences(mode(),n.isEmpty()?0:Integer.parseInt(n),template.getText().toString(),toneVolume.getProgress(),speechVolume.getProgress(),Integer.parseInt(q),bellBoost.getProgress(),savedWords,tonesEnabled.isChecked(),alarmSpeechEnabled.isChecked(),valueSpeechEnabled.isChecked(),vibrationEnabled.isChecked(),current.gap,draftSpeechLanguage,current.unitMode);
            if(!OutputModeUi.validate(this,desired))return;store.save(revision,desired);PreviewAudio.configurationChanged();LiveAlarmService.settingsChanged();finish();
        }catch(IOException | IllegalArgumentException e){status.setText(e.getMessage()==null?AppText.tr("Speichern fehlgeschlagen; bitte neu öffnen."):e.getMessage());}}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(ready){state.putLong("revision",revision);state.putStringArrayList("fields",values());state.putStringArrayList("initial",initial);}}
    @Override public void onBackPressed(){if(audio!=null)audio.stop();if(ready && !initial.equals(values()))new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null).setPositiveButton(AppText.tr("Verwerfen"),(d,w)->finish()).show();else super.onBackPressed();}
    @Override public void onPause(){if(audio!=null)audio.pause();super.onPause();}
    @Override public void onDestroy(){if(audio!=null)audio.close();super.onDestroy();}
}
