// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.os.*;
import android.widget.*;
import java.util.concurrent.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;

/** Private six-hour chart; loading and redraw scheduling exist only while the dashboard is visible. */
final class DashboardHistory {
    private final ObservationStore store;
    private final HistoryChartView chart;
    private final TextView summary;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean closed,loading,visible;
    private long latestReceipt=Long.MIN_VALUE;
    private final Runnable refresh=new Runnable() { @Override public void run() { load(); if (visible) handler.postDelayed(this,60000); } };
    DashboardHistory(Activity activity,LinearLayout parent) {
        store=new ObservationStore(activity); chart=new HistoryChartView(activity);
        parent.addView(chart,new LinearLayout.LayoutParams(-1,LevelUi.dp(activity,170)));
        summary=LevelUi.text(parent,AppText.tr("Wird geladen …"),13); DashboardUi.type(summary,DashboardUi.muted(activity),false);
        summary.setTag("history_summary"); summary.setPadding(0,LevelUi.dp(activity,5),0,LevelUi.dp(activity,5));
    }
    void receipt(long elapsed) { if (elapsed!=latestReceipt) { latestReceipt=elapsed; if (visible) load(); } }
    private void load() {
        if (closed || loading || !visible) return; loading=true;
        worker.execute(() -> {
            try {
                long now=System.currentTimeMillis(); DiagnosticSeries series=DiagnosticSeries.build(store.receipts(now),now);
                List<AlarmLevel> levels;
                try { levels=new LevelStore(chart.getContext().getFilesDir()).load().levels; } catch(java.io.IOException e) { levels=Collections.emptyList(); }
                final List<AlarmLevel> bands=levels;
                handler.post(() -> { loading=false; if (closed) return; chart.setSeries(series); chart.setAlarmLevels(bands);
                    summary.setText(series.rawPoints+AppText.tr(" Werte · ")+series.gaps.size()+AppText.tr(" Lücken")); });
            } catch (RuntimeException e) { handler.post(() -> { loading=false; if (!closed) summary.setText(AppText.tr("Verlauf gerade nicht lesbar")); }); }
        });
    }
    void resume() { if (closed) return; visible=true; handler.removeCallbacks(refresh); handler.post(refresh); }
    void pause() { visible=false; handler.removeCallbacks(refresh); }
    void close() { if (closed) return; closed=true; pause(); handler.removeCallbacksAndMessages(null); worker.execute(store::close); worker.shutdown(); }
}
