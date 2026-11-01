// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.os.Bundle;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

/** Explicit synthetic auditions. Does not evaluate live conditions or reserve alarm deadlines. */
public final class OutputTestActivity extends UiActivity {
    private PreviewAudio audio;private OutputPreferences preferences;private TextView status;
    private final List<AlarmLevel> levels=new ArrayList<>();private int selected;
    private Button tone,speech,vibration;
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->finish());
        LevelUi.text(body,AppText.tr("Ausgabe testen"),28);
        LevelUi.text(body,AppText.tr("Die Vorschau verwendet Testwerte und Deine gespeicherten Ausgabewege. Jeder Start erfolgt per Knopfdruck."),16);
        status=LevelUi.text(body,"",14);audio=new PreviewAudio(this,text->status.setText(text));
        try{preferences=new OutputPreferenceStore(getFilesDir()).load().preferences;levels.addAll(new LevelStore(getFilesDir()).load().levels);
            if(levels.isEmpty())levels.add(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level);
            Spinner choose=new Spinner(this);choose.setContentDescription(AppText.tr("Alarmstufe für den Ausgabetest"));List<String> labels=new ArrayList<>();
            for(AlarmLevel level:levels)labels.add(level.name+" · "+LevelForm.kindLabel(level.rule.kind));
            choose.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));body.addView(choose);
            tone=pair(body,AppText.tr("Ton"),()->audio.tone(levels.get(selected).toneUri));
            speech=pair(body,AppText.tr("Sprache"),()->{AlarmLevel level=levels.get(selected);audio.speak(SyntheticSpeech.alarm(level.rule.kind,level.name,android.text.TextUtils.join("\n",level.speechVariants),0,preferences.trendSpeech,preferences.speechLanguage,UnitPreferences.display(this,preferences)));});
            vibration=pair(body,AppText.tr("Vibration"),()->audio.vibrate(levels.get(selected).vibrationPattern));
            choose.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){audio.stop();selected=n;controls();}public void onNothingSelected(AdapterView<?> p){}});
            LevelUi.help(body,AppText.tr("Wenn die Vorschau stumm bleibt"),AppText.tr("Prüfe die gewählten Ausgabewege, Lautstärke, eine verfügbare Offline-Stimme und Androids Nicht-stören-Einstellungen. Eine laufende Alarmausgabe hat Vorrang vor der Vorschau."));
            controls();audio.prepareSpeech();
        }catch(IOException | IllegalArgumentException e){status.setText(AppText.tr("Ausgabe-Einstellungen nicht lesbar"));}
    }
    private Button pair(LinearLayout parent,String name,Runnable start){LinearLayout row=new LinearLayout(this);parent.addView(row);TextView label=LevelUi.text(row,name,18);label.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Button play=LevelUi.play(row,name+" · "+AppText.tr("Anhören"),v->{try{start.run();}catch(IllegalArgumentException e){status.setText(AppText.tr(e.getMessage()));}});
        play.setTag("output_test_"+name);LevelUi.stop(row,AppText.tr("Vorschau stoppen"),v->audio.stop());return play;
    }
    private void controls(){AlarmLevel level=levels.get(selected);tone.setEnabled(preferences.tonesEnabled && preferences.toneVolumePercent>0 && level.rule.outputs.tone);
        speech.setEnabled(preferences.alarmSpeechEnabled && preferences.speechVolumePercent>0 && level.rule.outputs.speech);
        vibration.setEnabled(preferences.vibrationEnabled && level.vibrationPattern!=null && new PreviewVibration(this).available());}
    @Override public void onPause(){if(audio!=null)audio.pause();super.onPause();}
    @Override public void onDestroy(){if(audio!=null)audio.close();super.onDestroy();}
}
