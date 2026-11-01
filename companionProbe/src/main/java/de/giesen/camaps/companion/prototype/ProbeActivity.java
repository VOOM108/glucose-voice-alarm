// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.Set;
import java.io.IOException;

/** Current reading and operation first; bounded technical tools remain behind a disclosure. */
public class ProbeActivity extends UiActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, value, trendValue, calculatedRate, speechStatus, diagnostics;
    private Button copyDiagnostics;
    private Button confirmVector;
    private AlertDialog vectorDialog;
    private TextView vectorStatus;
    private TextView coverage;
    private TextView coreStatus;
    private TextView editorStatus;
    private TextView runtimeStatus;
    private TextView operationStatus;
    private Button operation;
    private TextView readingTime,readingUnit;
    private TextView activationHint,unitHint;
    private LiveActivation.Problem activationProblem;
    private long activationPendingUntil;
    private Switch liveSwitch;
    private boolean syncingControls;
    private ModeToggleRow modeToggles;
    private PauseControls pauseControls;
    private DashboardHistory history;
    private PreviewAudio audio;
    private long lastDisplayDiagnostic;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return;
        try { SoundDefaults.install(this); } catch (IOException e) { ProbeState.status=AppText.tr("App-Tonstandard noch nicht übernommen; gespeicherte Einstellungen bleiben erhalten"); }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundColor(DashboardUi.background(this));
        getWindow().setStatusBarColor(DashboardUi.background(this)); getWindow().setNavigationBarColor(DashboardUi.background(this));
        AppAppearance.apply(this);
        ScrollView scroll = new ScrollView(this); scroll.addView(panel); setContentView(scroll);
        LinearLayout header=new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL); panel.addView(header);
        ImageView logo=new ImageView(this); logo.setImageResource(R.mipmap.ic_launcher); LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(LevelUi.dp(this,34),LevelUi.dp(this,34)); logoSize.setMargins(0,0,LevelUi.dp(this,10),0); header.addView(logo,logoSize);
        TextView name=text(header,"Glucose Voice Alarm",24); name.setTag("app_name"); name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); name.setSingleLine(true); name.setHorizontallyScrolling(false); name.setAutoSizeTextTypeUniformWithConfiguration(16,24,1,android.util.TypedValue.COMPLEX_UNIT_SP); DashboardUi.type(name,DashboardUi.ink(this),true);
        LinearLayout reading=DashboardUi.card(this,panel);
        LinearLayout valueRow=new LinearLayout(this); valueRow.setOrientation(LinearLayout.HORIZONTAL); valueRow.setGravity(android.view.Gravity.CENTER_VERTICAL); reading.addView(valueRow);
        value = text(valueRow, "—", 54); value.setTag("dashboard_value"); value.setFontFeatureSettings("tnum"); value.setSingleLine(true); value.setHorizontallyScrolling(false); value.setAutoSizeTextTypeUniformWithConfiguration(28,54,1,android.util.TypedValue.COMPLEX_UNIT_SP); value.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); DashboardUi.type(value,DashboardUi.ink(this),true);
        LinearLayout stamp=new LinearLayout(this); stamp.setOrientation(LinearLayout.VERTICAL); valueRow.addView(stamp);
        readingTime=text(stamp,"",18); readingTime.setTag("dashboard_time"); readingTime.setSingleLine(true); readingTime.setFontFeatureSettings("tnum");
        readingTime.setMinWidth((int)Math.ceil(readingTime.getPaint().measureText("00:00"))+LevelUi.dp(this,2));
        readingTime.setLayoutParams(new LinearLayout.LayoutParams(-2,-2)); DashboardUi.type(readingTime,DashboardUi.muted(this),false); readingUnit=text(stamp,"",13); readingUnit.setTag("dashboard_unit");
        LinearLayout trendRow=new LinearLayout(this); trendRow.setGravity(android.view.Gravity.CENTER_VERTICAL); reading.addView(trendRow);
        trendValue=text(trendRow,AppText.tr("Warte auf CamAPS"),17);
        trendValue.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        trendValue.setTag("camaps_trend");
        Button editVector=LevelUi.button(trendRow,AppText.tr("Pfeilzuordnung prüfen"),UiActions.Role.EDIT,v -> labelVector()); editVector.setTag("edit_vector");
        confirmVector=button(reading,AppText.tr("Pfeilgrafik zuordnen …"),v -> labelVector());
        vectorStatus=text(reading,"",13);
        calculatedRate=text(reading,"",14); calculatedRate.setTag("calculated_rate"); calculatedRate.setSingleLine(true);
        DashboardUi.type(calculatedRate,DashboardUi.muted(this),false);
        unitHint=text(reading,"",14); unitHint.setTag("unit_warning"); unitHint.setTextColor(AppAppearance.colors(this).error); unitHint.setOnClickListener(v -> startActivity(new Intent(this,DeveloperOptionsActivity.class)));
        history=new DashboardHistory(this,reading);
        LinearLayout controls=DashboardUi.card(this,panel);
        operationStatus=text(controls,"",18);
        liveSwitch=new Switch(this); liveSwitch.setText(AppText.tr("Ausgabe aktiv")); liveSwitch.setTextSize(16); liveSwitch.setContentDescription(AppText.tr("Live-Alarme")); liveSwitch.setMinHeight(LevelUi.dp(this,52)); liveSwitch.setPadding(0,6,0,6); controls.addView(liveSwitch);
        liveSwitch.setOnCheckedChangeListener((button,checked) -> {
            if (syncingControls) return;
            activationProblem=null; activationPendingUntil=0;
            if (checked) { activationProblem=LiveActivation.start(this); if(activationProblem==null) activationPendingUntil=SystemClock.elapsedRealtime()+5000; }
            else { LivePreferences.set(this,LivePreferences.monitor(this),false); LiveAlarmService.settingsChanged(); }
            render();
        });
        activationHint=text(controls,"",14); activationHint.setTag("activation_hint"); activationHint.setTextColor(AppAppearance.colors(this).error);
        activationHint.setPaintFlags(activationHint.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG); activationHint.setVisibility(View.GONE);
        activationHint.setOnClickListener(v -> { if(activationProblem!=null) startActivity(new Intent(this,activationProblem.target)); });
        View divider=new View(this); divider.setBackgroundColor(AppAppearance.colors(this).line); LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,LevelUi.dp(this,1)); line.setMargins(0,LevelUi.dp(this,6),0,LevelUi.dp(this,6)); controls.addView(divider,line);
        modeToggles=new ModeToggleRow(this); controls.addView(modeToggles);
        pauseControls=new PauseControls(this,controls);
        LinearLayout navigation=DashboardUi.card(this,panel);
        LevelUi.navigation(navigation,AppText.tr("Alarmstufen"),v->startActivity(new Intent(this,AlarmLevelsActivity.class)));
        LevelUi.navigation(navigation,AppText.tr("Ausgabe"),v->startActivity(new Intent(this,OutputSettingsActivity.class)));
        LevelUi.navigation(navigation,AppText.tr("Trendbezeichnungen"),v->startActivity(new Intent(this,TrendNamesActivity.class)));
        operation=LevelUi.navigation(navigation,AppText.tr("Android-Freigaben"),v->startActivity(new Intent(this,ReadinessActivity.class)));
        LevelUi.navigation(navigation,AppText.tr("Profile"),v->startActivity(new Intent(this,ProfilesActivity.class)));
        LevelUi.navigation(navigation,AppText.tr("Verlauf & Diagnose"),v->startActivity(new Intent(this,DiagnosticHistoryActivity.class)));
        LevelUi.navigation(navigation,AppText.tr("Einstellungen"),v->startActivity(new Intent(this,SettingsActivity.class)));
        speechStatus=new TextView(this);
        audio=new PreviewAudio(this,message->speechStatus.setText(message));
    }
    private boolean enabled() { return getSharedPreferences(CamApsListener.PREFS, MODE_PRIVATE).getBoolean(CamApsListener.ENABLED, false); }
    private void runCoreSelfTest() {
        try { AlarmProbe.selfTestPassed=AlarmProbe.runSyntheticSelfTest(); }
        catch (RuntimeException e) { AlarmProbe.selfTestPassed=0; }
        saveCoreReport();
    }
    private void saveCoreReport() {
        try { DiagnosticCache.writeCore(getCacheDir(),BuildConfig.VERSION_NAME); }
        catch (IOException e) { /* Private diagnostics are optional; no platform delivery exists. */ }
    }
    private void render() {
        if(pauseControls!=null)pauseControls.render();
        if (SystemClock.elapsedRealtime()-lastDisplayDiagnostic>2000 || lastDisplayDiagnostic==0) { DisplayDiagnostics.write(this); lastDisplayDiagnostic=SystemClock.elapsedRealtime(); }
        ProbeState.Observation current = ProbeState.display(this);
        SystemReadiness readiness=SystemReadiness.read(this);
        operationStatus.setText(SystemReadiness.operatingTitle(this));

        boolean live=LiveAlarmService.live();
        if(live) { activationPendingUntil=0; activationProblem=null; }
        boolean pending=!live && activationPendingUntil>SystemClock.elapsedRealtime();
        if(!pending && activationPendingUntil!=0) { activationPendingUntil=0; activationProblem=new LiveActivation.Problem(AppText.tr("Ausgabe läuft nicht · Betrieb prüfen"),ReadinessActivity.class); }
        if(!live && !pending && activationProblem==null && LivePreferences.alarms(this)) {
            activationProblem=LiveActivation.check(this);
            if(activationProblem==null) activationProblem=new LiveActivation.Problem(AppText.tr("Ausgabe läuft nicht · Betrieb prüfen"),ReadinessActivity.class);
        }
        syncingControls=true; liveSwitch.setChecked(live || pending); liveSwitch.setEnabled(!pending); syncingControls=false;
        activationHint.setText(activationProblem==null?"":activationProblem.text); activationHint.setVisibility(activationProblem==null?View.GONE:View.VISIBLE);
        VectorReference.Candidate candidate=ProbeState.vectorCandidate;
        confirmVector.setEnabled(enabled() && candidate!=null && candidate.current());
        NotificationParser.Trend exact=candidate==null?NotificationParser.Trend.UNKNOWN:TrendReference.lookup(getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).getAll(),candidate.key);
        NotificationParser.Trend shape=candidate==null?NotificationParser.Trend.UNKNOWN:TrendReference.lookup(getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE).getAll(),candidate.shapeKey);
        boolean known=ShapeReference.resolve(exact,shape)!=NotificationParser.Trend.UNKNOWN;
        boolean parsedKnown=ProbeState.latest!=null && ProbeState.latest.trend!=NotificationParser.Trend.UNKNOWN;
        confirmVector.setVisibility(!parsedKnown && candidate!=null && candidate.current()?View.VISIBLE:View.GONE);
        vectorStatus.setVisibility(!parsedKnown && candidate!=null && candidate.current()?View.VISIBLE:View.GONE);
        UiActions.title(confirmVector,known?AppText.tr("Bekannte Pfeilgrafik prüfen/ändern …"):AppText.tr("Pfeilgrafik zuordnen …"));
        vectorStatus.setText(candidate!=null && candidate.current()
                ? (known?AppText.tr("Diese Grafik ist bereits zugeordnet; keine erneute Bestätigung nötig."):AppText.tr("Noch nicht zugeordnete Grafik. Bitte nur einen klar erkennbaren Pfeil bestätigen."))
                : AppText.tr("Keine aktuelle, eindeutige Vektorgrafik zum Zuordnen verfügbar."));
        GlucoseUnit unit=UnitPreferences.display(this); readingUnit.setText(unit.label);
        unitHint.setText(AppText.tr("Einheitenkonflikt · Einheit prüfen")); unitHint.setVisibility(UnitPreferences.conflict(this)?View.VISIBLE:View.GONE);
        trendValue.setText(AppText.tr("Trend:")+" "+TrendNotice.dashboard(this,current));
        value.setText(current == null ? "—" : MetricNotification.reading(current.value,current.trend,unit,ProbeState.displayFresh(this,current),current.arrowColor));
        value.setContentDescription(current==null?AppText.tr("Noch kein Empfang"):unit.number(current.value,java.util.Locale.getDefault())+" "+unit.label+(current.arrowColor.colored()?". "+DisplayTrendPreferences.title(this,current.trend,current.arrowColor):""));
        boolean currentFresh=ProbeState.displayFresh(this,current);
        Double rate=currentFresh?current.receiptRate.at(System.currentTimeMillis()):null;
        calculatedRate.setText(rate==null?"":unit.rate(rate,java.util.Locale.getDefault()));
        calculatedRate.setVisibility(rate==null?View.GONE:View.VISIBLE);
        calculatedRate.setContentDescription(rate==null?"":AppText.tr("Änderungsrate")+" "+calculatedRate.getText());
        DashboardUi.type(value,currentFresh?DashboardUi.ink(this):DashboardUi.muted(this),true);
        readingTime.setText(current==null?"":new java.text.SimpleDateFormat("HH:mm",java.util.Locale.GERMANY).format(new java.util.Date(current.receivedWall)));
        DashboardUi.type(readingTime,current!=null && !currentFresh?AppAppearance.colors(this).error:DashboardUi.muted(this),false);
        readingTime.setContentDescription(current==null?AppText.tr("Noch kein Empfang"):readingTime.getText()+(currentFresh?"":AppText.tr("; letzter Empfang nicht mehr aktuell")));
        if (current!=null) history.receipt(current.receivedElapsed);
    }
    private void labelVector() {
        final VectorReference.Candidate captured=ProbeState.vectorCandidate;
        if (!enabled() || captured==null || !captured.current()) {
            new AlertDialog.Builder(this).setTitle(AppText.tr("Pfeilzuordnung prüfen"))
                    .setMessage(AppText.tr("Keine aktuelle, eindeutige Pfeilgrafik verfügbar. Bitte nach dem nächsten CamAPS-Update erneut öffnen."))
                    .setPositiveButton(AppText.tr("OK"),null).show(); render(); return;
        }
        NotificationParser.Trend exact=TrendReference.lookup(getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).getAll(),captured.key);
        NotificationParser.Trend shape=TrendReference.lookup(getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE).getAll(),captured.shapeKey);
        NotificationParser.Trend assigned=ShapeReference.resolve(exact,shape);
        ArrowColor assignedColor=TrendReference.color(getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).getAll(),captured.key,assigned);
        final NotificationParser.Trend[] choices=ArrowChoicePanel.DIRECTIONS;
        final int[] selected={-1};
        final AlertDialog[] shown={null};
        ArrowChoicePanel content=new ArrowChoicePanel(this,captured.preview,assigned,assignedColor,index->{
            selected[0]=index;
            if(shown[0]!=null)shown[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled() && VectorReference.canConfirm(captured,ProbeState.vectorCandidate));
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(AppText.tr("Pfeilgrafik bestätigen"))
                .setView(content).setNeutralButton(AppText.tr("Neu laden"),(d,w) -> handler.post(this::labelVector)).setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Speichern"),(d,which) -> {
                    VectorReference.Candidate current=ProbeState.vectorCandidate;
                    if (!enabled() || selected[0]<0 || !VectorReference.canConfirm(captured,current)) {
                        ProbeState.status=AppText.tr("Grafik inzwischen geändert oder zu alt; bitte neu prüfen"); render(); return;
                    }
                    android.content.SharedPreferences references=getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE);
                    android.content.SharedPreferences shapes=getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE);
                    boolean canExact=references.contains(captured.key) || references.getAll().size()<64;
                    boolean canShape=captured.shapeKey!=null && (shapes.contains(captured.shapeKey) || shapes.getAll().size()<64);
                    if (!canExact) {
                        new AlertDialog.Builder(this).setMessage(AppText.tr("Pfeilspeicher voll; diese Grafik kann nicht gespeichert werden."))
                                .setPositiveButton(AppText.tr("OK"),null).show(); render(); return;
                    }
                    NotificationParser.Trend chosen=choices[selected[0]%choices.length];
                    ArrowColor chosenColor=ArrowColor.values()[selected[0]/choices.length];
                    if (canExact) references.edit().putString(captured.key,TrendReference.label(chosen,chosenColor)).apply();
                    if (canShape) shapes.edit().putString(captured.shapeKey,
                            ShapeReference.registration(shapes.getAll().get(captured.shapeKey),chosen)).apply();
                    boolean historySaved=false;
                    try {
                        android.content.SharedPreferences history=getSharedPreferences(VectorReference.HISTORY_PREFS,MODE_PRIVATE);
                        String encoded=VectorHistory.append(history.getString("entries",""),new VectorHistory.Entry(captured.key,captured.shapeKey,chosen));
                        historySaved=history.edit().putString("entries",encoded).commit();
                    } catch (IllegalArgumentException | ClassCastException e) { /* Preserve unknown history; reference storage is independent. */ }
                    ProbeState.forgetTrend();
                    LiveAlarmService.trendReferencesChanged();
                    HomeWidget.changed(this);
                    if(WatchPublisher.enabled(this))WatchPublisher.changed(this);
                    if (audio!=null) audio.stop();
                    ProbeState.status=AppText.tr("Pfeilreferenz gespeichert; warte auf ein neues CamAPS-Update")+(historySaved?"":AppText.tr(". Bestätigungshistorie nicht verfügbar."));
                    render();
                }).create();
        shown[0]=dialog;
        Runnable guard=new Runnable() { @Override public void run() {
            if (!dialog.isShowing()) return;
            boolean fresh=enabled() && VectorReference.canConfirm(captured,ProbeState.vectorCandidate);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(fresh && selected[0]>=0);
            if (!fresh) dialog.setTitle(AppText.tr("Vorschau veraltet · neu laden"));
            handler.postDelayed(this,500);
        }};
        dialog.setOnShowListener(d -> { ArrowChoicePanel.fitActions(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); handler.post(guard); });
        dialog.setOnDismissListener(d -> { handler.removeCallbacks(guard); if(vectorDialog==dialog) vectorDialog=null; });
        vectorDialog=dialog;
        dialog.show();
    }
    private String diagnosticText() { return "Prototyp " + BuildConfig.VERSION_NAME + "\n" + ProbeState.diagnostics; }
    private void say(String text) {
        if (audio!=null) audio.speak(text);
    }
    private TextView text(LinearLayout parent, String content, int size) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(AppAppearance.colors(this).ink);
        int vertical=LevelUi.dp(this,size>=40?0:5); view.setPadding(0,vertical,0,vertical); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String title, View.OnClickListener listener) {
        Button view = new UiActions.ActionButton(this); UiActions.style(view,title);
        view.setOnClickListener(listener); parent.addView(view);
        return view;
    }
    @Override public void onResume() {
        super.onResume(); if(!SafetyConsent.ready(this))return; RuntimeStateBridge.refresh(getFilesDir(),RuntimeClock.read(this)); render();handler.postDelayed(refresh,1000);
        if (history!=null) history.resume(); if (modeToggles!=null) modeToggles.render();
    }
    @Override public void onPause() {
        if(vectorDialog!=null) vectorDialog.dismiss();
        handler.removeCallbacks(refresh); if (history!=null) history.pause(); if (audio != null) audio.pause(); super.onPause();
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (history!=null) history.close();
        if (audio != null) { audio.close(); audio=null; }
        super.onDestroy();
    }
}
