// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class DiagnosticHistoryActivity extends UiActivity {
    private ObservationStore store;
    private HistoryChartView chart;
    private TextView summary;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean closed,loading;
    private final Runnable refresh=new Runnable() { @Override public void run() { load(); handler.postDelayed(this,60000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(!SafetyConsent.ready(this))return; store=new ObservationStore(this); LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish()); LevelUi.text(body,AppText.tr("Verlauf"),28);
        LevelUi.help(body,AppText.tr("Zur Kurve"),AppText.tr("Empfangszeiten über sechs Stunden. Gestrichelte senkrechte Grenzen markieren Lücken über zwei Minuten. Horizontale Flächen zeigen eingestellte Hoch- und Niedrigbereiche. Die Zeichnung verändert keine Alarmberechnung."));
        chart=new HistoryChartView(this); body.addView(chart,new LinearLayout.LayoutParams(-1,LevelUi.dp(this,320)));
        summary=LevelUi.text(body,AppText.tr("Daten werden lokal gelesen …"),15);
        LevelUi.button(body,AppText.tr("Aktualisieren"),v -> load());
        LevelUi.button(body,AppText.tr("Technische Prüfungen und Pfeildiagnose"),v -> startActivity(new android.content.Intent(this,ProbeDiagnosticsActivity.class)));
        LevelUi.text(body,AppText.tr("Nur private Daten dieser App. Kein Export, kein Upload und keine zusätzlichen Sensorabfragen. Vor dem ersten gespeicherten Empfang bleibt die Kurve leer."),14);
    }
    private void load() {
        if (closed || loading) return; loading=true;
        worker.execute(() -> {
            try {
                long now=System.currentTimeMillis(); DiagnosticSeries result=DiagnosticSeries.build(store.receipts(now),now);
                java.util.List<de.giesen.camaps.companion.prototype.alarm.AlarmLevel> levels;
                try { levels=new de.giesen.camaps.companion.prototype.alarm.LevelStore(getFilesDir()).load().levels; } catch(java.io.IOException e) { levels=Collections.emptyList(); }
                final java.util.List<de.giesen.camaps.companion.prototype.alarm.AlarmLevel> bands=levels;
                handler.post(() -> {
                    loading=false; if (closed) return; chart.setSeries(result); chart.setAlarmLevels(bands);
                    summary.setText(result.rawPoints+AppText.tr(" Werte · ")+result.gaps.size()+AppText.tr(" Lücken")
                            +(result.conflicts>0?" · "+result.conflicts+AppText.tr(" widersprüchliche Zeitpunkte ausgelassen"):""));
                });
            } catch (RuntimeException e) { handler.post(() -> { loading=false; if (!closed) summary.setText(AppText.tr("Diagnoseverlauf nicht lesbar. Vorhandene Daten bleiben erhalten.")); }); }
        });
    }
    @Override public void onResume() { super.onResume(); if(!SafetyConsent.ready(this))return; handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { closed=true; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); if (store!=null) store.close(); super.onDestroy(); }
}
