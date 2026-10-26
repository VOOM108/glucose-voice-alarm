// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

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
    protected boolean diagnosticsPage() { return false; }
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
        trendValue=text(reading,AppText.tr("Warte auf CamAPS"),17);
        trendValue.setTag("camaps_trend");
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
        LinearLayout firstNav=DashboardUi.navigationRow(this,panel);
        DashboardUi.navigationSize(this,firstNav,button(firstNav,AppText.tr("Alarme und Stufen"),v -> startActivity(new Intent(this,AlarmLevelsActivity.class))));
        DashboardUi.navigationSize(this,firstNav,button(firstNav,AppText.tr("Ton und Sprache"),v -> startActivity(new Intent(this,OutputSettingsActivity.class))));
        LinearLayout secondNav=DashboardUi.navigationRow(this,panel);
        DashboardUi.navigationSize(this,secondNav,button(secondNav,AppText.tr("Darstellung"),v -> startActivity(new Intent(this,AppearanceActivity.class))));
        operation=button(secondNav,AppText.tr("Anzeige und Hintergrund"),v -> startActivity(new Intent(this,ReadinessActivity.class))); DashboardUi.navigationSize(this,secondNav,operation);
        button(panel,AppText.tr("Profile"),v -> startActivity(new Intent(this,ProfilesActivity.class)));
        button(panel,AppText.tr("Verlauf und Diagnose"),v -> startActivity(new Intent(this,DiagnosticHistoryActivity.class)));
        final LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
        if (diagnosticsPage()) {
            int inset=LevelUi.dp(this,20); details.setPadding(inset,inset,inset,inset);
            ScrollView diagnosticScroll=new ScrollView(this); diagnosticScroll.addView(details); setContentView(diagnosticScroll);
            button(details,AppText.tr("Zurück zur Diagnosekurve"),v -> finish()); text(details,AppText.tr("Technische Prüfungen"),24);
        }
        panel=details;
        status = text(panel, "", 16);
        text(panel,"Beta · "+BuildConfig.VERSION_NAME,14);
        coverage=text(panel,ArrowCoverage.summary(this),15);
        button(panel,AppText.tr("Pfeile prüfen"),v -> labelVector());
        button(panel,AppText.tr("Alarmablauf mit Testdaten ausprobieren"),v -> startActivity(new Intent(this,SyntheticAlarmActivity.class)));
        text(panel, AppText.tr("Zeitbasis: Empfang des CamAPS-Werts, wie im xDrip-Companionmodus."), 16);
        Switch receive = new Switch(this);
        receive.setText(AppText.tr("CamAPS-Empfang einschalten"));
        receive.setChecked(enabled()); panel.addView(receive);
        receive.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(CamApsListener.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(CamApsListener.ENABLED, checked).apply();
            ProbeState.latest = null;
            ProbeState.vectorCandidate = null;
            AlarmProbe.clear(checked?AlarmProbe.Ingress.WAITING:AlarmProbe.Ingress.OFF);
            saveCoreReport();
            try { DiagnosticCache.clear(getCacheDir()); } catch (IOException e) { /* No raw data is written. */ }
            ProbeState.diagnostics = checked ? AppText.tr("Warte auf ein neues CamAPS-Update für die Trenddiagnose.") : AppText.tr("Empfang ausgeschaltet; Trenddiagnose geleert.");
            if (audio != null) audio.stop();
            ProbeState.status = checked ? AppText.tr("Warte auf Verbindung / neues CamAPS-Update") : AppText.tr("Empfang ausgeschaltet");
            ComponentName component = new ComponentName(this, CamApsListener.class);
            if (!checked) {
                LivePreferences.set(this,false,false); stopService(new Intent(this,LiveAlarmService.class));
                // Component disable releases listener binding and its SQLite helper.
                getPackageManager().setComponentEnabledSetting(component,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
            }
            if (checked) {
                getPackageManager().setComponentEnabledSetting(component,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
                NotificationListenerService.requestRebind(component);
            }
            render();
        });
        button(panel, AppText.tr("Benachrichtigungszugriff öffnen"), v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        text(panel, AppText.tr("Android vergibt Benachrichtigungszugriff für alle Apps. Verarbeitet werden nur laufende unterstützte CamAPS-Meldungen; vollständige Meldungstexte werden nicht gespeichert."), 14);
        button(panel, AppText.tr("Sprachvorschau mit Testtext"), v -> {
            try { say(AppText.in(new de.giesen.camaps.companion.prototype.alarm.OutputPreferenceStore(getFilesDir()).load().preferences.speechLanguage,"Dies ist eine Sprachvorschau von Glucose Voice Alarm. Es wird kein Messwert angesagt.")); }
            catch(IOException e) { speechStatus.setText(AppText.tr("Ausgabe-Einstellungen nicht lesbar")); }
        });
        button(panel, AppText.tr("Letzten Empfang ansagen"), v -> {
            ProbeState.Observation current = ProbeState.latest;
            long now = SystemClock.elapsedRealtime();
            if (current == null || !SpeechPolicy.canSpeak(enabled(), current.receivedElapsed, now,
                    current.postedWall, System.currentTimeMillis())) {
                speechStatus.setText(AppText.tr("Keine Ansage: kein kürzlich empfangenes, zeitlich plausibles Ereignis vorhanden."));
                return;
            }
            try { de.giesen.camaps.companion.prototype.alarm.OutputPreferences p=new de.giesen.camaps.companion.prototype.alarm.OutputPreferenceStore(getFilesDir()).load().preferences; say(SpeechPolicy.observationText(current.value, current.trend, (now-current.receivedElapsed)/1000,p.trendSpeech,p.speechLanguage,UnitPreferences.display(this,p))); }
            catch(IOException e) { speechStatus.setText(AppText.tr("Ausgabe-Einstellungen nicht lesbar")); }
        });
        button(panel, AppText.tr("Sprache stoppen"), v -> { if (audio != null) audio.stop(); });
        speechStatus = text(panel, AppText.tr("Sprachausgabe wird vorbereitet …"), 14);
        audio=new PreviewAudio(this,message -> speechStatus.setText(message));
        coreStatus=text(panel,"",14);
        button(panel,AppText.tr("Alarmkern mit Testdaten prüfen"),v -> runCoreSelfTest());
        runCoreSelfTest();
        editorStatus=text(panel,"",14);
        button(panel,AppText.tr("Speichern der Alarmstufen mit Testdaten prüfen"),v -> EditorDiagnostics.start(getCacheDir(),BuildConfig.VERSION_NAME));
        if (EditorDiagnostics.passed<0) EditorDiagnostics.start(getCacheDir(),BuildConfig.VERSION_NAME);
        runtimeStatus=text(panel,"",14);
        button(panel,AppText.tr("Alarm- und Snooze-Speicher mit Testdaten prüfen"),v -> RuntimeDiagnostics.start(getCacheDir(),BuildConfig.VERSION_NAME));
        if (RuntimeDiagnostics.passed<0) RuntimeDiagnostics.start(getCacheDir(),BuildConfig.VERSION_NAME);
        button(panel,AppText.tr("Gespeicherte Pfeilzuordnungen löschen"),v -> {
            getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences(VectorReference.HISTORY_PREFS,MODE_PRIVATE).edit().clear().apply();
            ReferenceAuditStore.clear(this);
            ProbeState.latest=null;
            ProbeState.status=AppText.tr("Pfeilzuordnungen gelöscht; warte auf ein neues CamAPS-Update");
            if (audio!=null) audio.stop();
            render();
        });
        LinearLayout trendDetails=new LinearLayout(this); trendDetails.setOrientation(LinearLayout.VERTICAL);
        DisclosureRow.add(panel,AppText.tr("Trenddiagnose anzeigen"),trendDetails);
        copyDiagnostics=button(trendDetails,AppText.tr("Trenddiagnose kopieren"),v -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard!=null) clipboard.setPrimaryClip(ClipData.newPlainText(AppText.tr("CamAPS-Trenddiagnose"),diagnosticText()));
        });
        diagnostics=text(trendDetails,"",13);
        diagnostics.setTextIsSelectable(true);
        text(panel, AppText.tr("Diagnoseverlauf: letzte sechs Stunden im privaten App-Speicher, technisch höchstens 5000 Ereignisse. Keine Cloud oder automatische Sicherung. Gleiche Zahlen zu neuen Empfangsereignissen bleiben erhalten. Daten lassen sich über Androids App-Speicherverwaltung löschen."), 14);
        text(panel, AppText.tr("Grundlage: xDrip+ · GPLv3. Uhranbindung und weitere Alltagserprobung stehen noch aus."), 14);
        audio.prepareSpeech();
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
        if(coverage!=null) coverage.setText(ArrowCoverage.summary(this));
        if (SystemClock.elapsedRealtime()-lastDisplayDiagnostic>2000 || lastDisplayDiagnostic==0) { DisplayDiagnostics.write(this); lastDisplayDiagnostic=SystemClock.elapsedRealtime(); }
        ProbeState.Observation current = ProbeState.latest;
        SystemReadiness readiness=SystemReadiness.read(this);
        operationStatus.setText(SystemReadiness.operatingTitle(this));
        UiActions.title(operation,readiness.ready()?AppText.tr("Anzeige und Hintergrund"):readiness.nextAction());
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
        status.setText(enabled() ? ProbeState.status : AppText.tr("Empfang ausgeschaltet"));
        coreStatus.setText(AppText.tr("xDrip-Alarmkern: eingebunden.\nSelbsttest mit getrennten Testdaten: ")
                +(AlarmProbe.selfTestPassed<0?AppText.tr("ausstehend"):AlarmProbe.selfTestPassed+AppText.tr("/5 Alarmarten bestanden"))
                +AppText.tr(".\nZeitbasis: Empfang. Live-Alarme: ")+(LiveAlarmService.live()?AppText.tr("eingeschaltet"):AppText.tr("aus"))+".\n"+LiveAlarmService.status);
        editorStatus.setText(AppText.tr("Speichertest mit getrennten Testdaten: ")+(EditorDiagnostics.passed<0?AppText.tr("läuft …"):EditorDiagnostics.passed+AppText.tr("/6 Prüfungen bestanden")));
        runtimeStatus.setText(AppText.tr("Alarm-/Snooze-Speicher: ")+(RuntimeStateBridge.status==RuntimeStateBridge.Status.STORAGE_BLOCKED
                || RuntimeStateBridge.status==RuntimeStateBridge.Status.CLOCK_BLOCKED?AppText.tr("gesperrt; Prüfung erforderlich"):AppText.tr("bereit; vor Ausgabe wäre ein neuer gültiger Empfang nötig"))
                +AppText.tr(".\nNeustarttest mit getrennten Testdaten: ")+(RuntimeDiagnostics.passed<0?AppText.tr("läuft …"):RuntimeDiagnostics.passed+AppText.tr("/8 Prüfungen bestanden"))+AppText.tr(". Diese Selbsttests erzeugen keine Live-Ausgabe."));
        if (diagnostics.isShown()) diagnostics.setText(diagnosticText());
        VectorReference.Candidate candidate=ProbeState.vectorCandidate;
        confirmVector.setEnabled(enabled() && candidate!=null && candidate.current());
        NotificationParser.Trend exact=candidate==null?NotificationParser.Trend.UNKNOWN:TrendReference.lookup(getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).getAll(),candidate.key);
        NotificationParser.Trend shape=candidate==null?NotificationParser.Trend.UNKNOWN:TrendReference.lookup(getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE).getAll(),candidate.shapeKey);
        boolean known=ShapeReference.resolve(exact,shape)!=NotificationParser.Trend.UNKNOWN;
        boolean parsedKnown=current!=null && current.trend!=NotificationParser.Trend.UNKNOWN;
        confirmVector.setVisibility(!parsedKnown && candidate!=null && candidate.current()?View.VISIBLE:View.GONE);
        vectorStatus.setVisibility(!parsedKnown && candidate!=null && candidate.current()?View.VISIBLE:View.GONE);
        UiActions.title(confirmVector,known?AppText.tr("Bekannte Pfeilgrafik prüfen/ändern …"):AppText.tr("Pfeilgrafik zuordnen …"));
        vectorStatus.setText(candidate!=null && candidate.current()
                ? (known?AppText.tr("Diese Grafik ist bereits zugeordnet; keine erneute Bestätigung nötig."):AppText.tr("Noch nicht zugeordnete Grafik. Bitte nur einen klar erkennbaren Pfeil bestätigen."))
                : AppText.tr("Keine aktuelle, eindeutige Vektorgrafik zum Zuordnen verfügbar."));
        GlucoseUnit unit=UnitPreferences.display(this); readingUnit.setText(unit.label);
        unitHint.setText(AppText.tr("Einheitenkonflikt · Entwickleroptionen öffnen")); unitHint.setVisibility(UnitPreferences.conflict(this)?View.VISIBLE:View.GONE);
        trendValue.setText("CamAPS  "+(current==null?AppText.tr("Warte auf CamAPS"):current.trend!=NotificationParser.Trend.UNKNOWN
                ?MetricNotification.trendTitle(current.trend):candidate!=null && candidate.current()?AppText.tr("Trend offen"):AppText.tr("Kein Trendpfeil")));
        value.setText(current == null ? "—" : unit.number(current.value,java.util.Locale.getDefault())+(current.trend==NotificationParser.Trend.UNKNOWN?"":" "+current.trend.symbol));
        value.setContentDescription(current==null?AppText.tr("Noch kein Empfang"):unit.number(current.value,java.util.Locale.getDefault())+" "+unit.label);
        boolean currentFresh=current!=null && SpeechPolicy.canSpeak(enabled(),current.receivedElapsed,SystemClock.elapsedRealtime(),current.postedWall,System.currentTimeMillis());
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
        if (!enabled() || captured==null || !captured.current()) { render(); return; }
        ImageView preview=new ImageView(this);
        preview.setImageBitmap(captured.preview);
        preview.setBackgroundColor(0xffb0b0b0);
        int size=(int)(112*getResources().getDisplayMetrics().density);
        preview.setMinimumHeight(size); preview.setAdjustViewBounds(true);
        preview.setContentDescription(AppText.tr("Isolierte CamAPS-Vektorgrafik zur manuellen Zuordnung"));
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        text(content,AppText.tr("Ordne diese Vorschau zu. Bei einer neuen Quellmeldung bitte neu laden."),16);
        content.addView(preview,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,size));
        final NotificationParser.Trend[] choices=new NotificationParser.Trend[]{NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.UP,NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,NotificationParser.Trend.DOUBLE_DOWN};
        String[] labels=new String[choices.length];
        for (int i=0;i<choices.length;i++) labels[i]=choices[i].symbol+"  "+MetricNotification.trendTitle(choices[i]);
        final int[] selected={-1};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(AppText.tr("Pfeilgrafik bestätigen"))
                .setView(content).setSingleChoiceItems(labels,-1,(d,index) -> {
                    selected[0]=index; ((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled() && VectorReference.canConfirm(captured,ProbeState.vectorCandidate));
                }).setNeutralButton(AppText.tr("Neu laden"),(d,w) -> handler.post(this::labelVector)).setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Speichern"),(d,which) -> {
                    VectorReference.Candidate current=ProbeState.vectorCandidate;
                    if (!enabled() || selected[0]<0 || !VectorReference.canConfirm(captured,current)) {
                        ProbeState.status=AppText.tr("Grafik inzwischen geändert oder zu alt; bitte neu prüfen"); render(); return;
                    }
                    android.content.SharedPreferences references=getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE);
                    android.content.SharedPreferences shapes=getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE);
                    boolean canExact=references.contains(captured.key) || references.getAll().size()<64;
                    boolean canShape=captured.shapeKey!=null && (shapes.contains(captured.shapeKey) || shapes.getAll().size()<64);
                    if (!canExact && !canShape) {
                        ProbeState.status=AppText.tr("Referenzspeicher voll; alte Pfeilzuordnungen zuerst löschen"); render(); return;
                    }
                    if (canExact) references.edit().putString(captured.key,choices[selected[0]].name()).apply();
                    if (canShape) shapes.edit().putString(captured.shapeKey,
                            ShapeReference.registration(shapes.getAll().get(captured.shapeKey),choices[selected[0]])).apply();
                    boolean historySaved=false;
                    try {
                        android.content.SharedPreferences history=getSharedPreferences(VectorReference.HISTORY_PREFS,MODE_PRIVATE);
                        String encoded=VectorHistory.append(history.getString("entries",""),new VectorHistory.Entry(captured.key,captured.shapeKey,choices[selected[0]]));
                        historySaved=history.edit().putString("entries",encoded).commit();
                    } catch (IllegalArgumentException | ClassCastException e) { /* Preserve unknown history; reference storage is independent. */ }
                    ProbeState.latest=null;
                    if (audio!=null) audio.stop();
                    ProbeState.status=AppText.tr("Pfeilreferenz gespeichert; warte auf ein neues CamAPS-Update")+(historySaved?"":AppText.tr(". Bestätigungshistorie nicht verfügbar."));
                    render();
                }).create();
        Runnable guard=new Runnable() { @Override public void run() {
            if (!dialog.isShowing()) return;
            boolean fresh=enabled() && VectorReference.canConfirm(captured,ProbeState.vectorCandidate);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(fresh && selected[0]>=0);
            if (!fresh) dialog.setTitle(AppText.tr("Vorschau veraltet · neu laden"));
            handler.postDelayed(this,500);
        }};
        dialog.setOnShowListener(d -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); handler.post(guard); });
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
        super.onResume(); if(!SafetyConsent.ready(this))return; RuntimeStateBridge.refresh(getFilesDir(),RuntimeClock.read(this)); handler.post(refresh);
        if (history!=null && !diagnosticsPage()) history.resume(); if (modeToggles!=null) modeToggles.render();
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
