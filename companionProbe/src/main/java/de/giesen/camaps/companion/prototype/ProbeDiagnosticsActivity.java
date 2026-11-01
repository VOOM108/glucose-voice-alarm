// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import android.os.*;
import android.widget.*;

/** Actionable status and opt-in fixed technical reports; no reading or notification dump. */
public final class ProbeDiagnosticsActivity extends UiActivity {
    private TextView reception,output,arrows,result,speechStatus;private PreviewAudio audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->finish());LevelUi.text(body,AppText.tr("Funktionsprüfung"),28);
        reception=LevelUi.text(body,"",18);reception.setTag("diagnosis_reception");
        LevelUi.button(body,AppText.tr("Empfang neu verbinden"),v->{result.setText(CamApsConnection.reconnect(this)?AppText.tr("Warte auf ein neues CamAPS-Update"):AppText.tr("Empfang und Benachrichtigungszugriff prüfen"));render();});
        speechStatus=LevelUi.text(body,"",14);audio=new PreviewAudio(this,text->speechStatus.setText(text));
        LinearLayout sample=new LinearLayout(this);body.addView(sample);TextView label=LevelUi.text(sample,AppText.tr("Letzten Empfang ansagen"),16);label.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        LevelUi.play(sample,AppText.tr("Letzten Empfang ansagen"),v->sayLatest());LevelUi.stop(sample,AppText.tr("Vorschau stoppen"),v->audio.stop());
        output=LevelUi.text(body,"",18);output.setTag("diagnosis_output");
        LevelUi.navigation(body,AppText.tr("Ausgabe mit Testdaten prüfen"),v->startActivity(new Intent(this,OutputTestActivity.class)));
        arrows=LevelUi.text(body,"",15);
        LevelUi.navigation(body,AppText.tr("Pfeilzuordnung im Dashboard prüfen"),v->startActivity(new Intent(this,ProbeActivity.class)));
        LevelUi.navigation(body,AppText.tr("Android-Freigaben"),v->startActivity(new Intent(this,ReadinessActivity.class)));
        result=LevelUi.text(body,"",14);
        LevelUi.button(body,AppText.tr("Technischen Kurzbericht kopieren"),v->{
            String report=DisplayDiagnostics.report(this)+"\n"+ProcessingDiagnostics.summary(this)+"\n"+AlarmOutputDiagnostics.summary(this)
+                    "\n"+CamApsConnection.summary()+"\n"+RuntimeStateBridge.status.name();
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("GVA",report));result.setText(AppText.tr("Technischer Kurzbericht kopiert"));}
        });
        LevelUi.text(body,AppText.tr("Der Kurzbericht enthält technische Zustände, keine Messwerte oder Benachrichtigungstexte."),14);
    }
    private void sayLatest(){ProbeState.Observation current=ProbeState.latest;long now=SystemClock.elapsedRealtime();
        if(current==null || !SpeechPolicy.canSpeak(getSharedPreferences(CamApsListener.PREFS,0).getBoolean(CamApsListener.ENABLED,false),current.receivedElapsed,now,current.postedWall,System.currentTimeMillis())){speechStatus.setText(AppText.tr("Keine Ansage: kein kürzlich empfangenes, zeitlich plausibles Ereignis vorhanden."));return;}
        try{de.giesen.camaps.companion.prototype.alarm.OutputPreferences p=new de.giesen.camaps.companion.prototype.alarm.OutputPreferenceStore(getFilesDir()).load().preferences;audio.speak(SpeechPolicy.observationText(current.value,current.trend,(now-current.receivedElapsed)/1000,p.trendSpeech,p.speechLanguage,UnitPreferences.display(this,p),current.arrowColor));}
        catch(java.io.IOException e){speechStatus.setText(AppText.tr("Ausgabe-Einstellungen nicht lesbar"));}
    }
    private void render(){if(output==null)return;ProbeState.Observation current=ProbeState.display(this);
        boolean fresh=ProbeState.displayFresh(this,current);
        reception.setText(AppText.tr(fresh?"✓ Empfang aktuell":"! Kein aktueller Empfang"));
        reception.setTextColor(fresh?(AppAppearance.dark(this)?0xff8edbb0:0xff1a7043):AppAppearance.colors(this).error);
        output.setText(SystemReadiness.operatingTitle(this));arrows.setText(ArrowCoverage.summary(this));
        DisplayDiagnostics.write(this);
    }
    @Override public void onResume(){super.onResume();if(SafetyConsent.ready(this))handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);if(audio!=null)audio.pause();super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(audio!=null)audio.close();super.onDestroy();}
}
