// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;

/** Explicitly user-enabled local monitoring. Own app only, no sensor/pump control or data export. */
public final class LiveAlarmService extends Service {
    static final String STOP="de.giesen.camaps.companion.STOP",ACK="de.giesen.camaps.companion.ACK",SNOOZE="de.giesen.camaps.companion.SNOOZE";
    private static volatile LiveAlarmService current;
    static volatile String status=AppText.tr("Live-Dienst ausgeschaltet");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ReceiptWindow window=new ReceiptWindow();
    private final GapMonitor gap=new GapMonitor();
    private OutputPreferences preferences;
    private boolean sourceValid,gapNotified;
    private volatile long referenceRevision;
    private final Map<String,AlarmLevel> levels=new LinkedHashMap<>();
    private LiveNotifications notifications;
    private PreviewAudio audio;
    private PowerManager.WakeLock outputLock;
    private AlarmOutputFlow flow;
    private Double value;
    private NotificationParser.Trend trend=NotificationParser.Trend.UNKNOWN;
    private AlarmStateStore.Clock received;
    private String audioStatus="",lastSummary="";
    private String terminalStatus;
    private boolean foreground;
    private boolean playbackForeground;
    private AlarmOutputFlow.Plan activePlan,displayedPlan;
    static final long SPEECH_COMPLETION_MS=10000;
    private AlarmOutputFlow.Plan speechCompletionPlan;
    private AlarmStateStore.Clock speechCompletionStart;
    private String displayedAlarmId;
    private final Runnable tick=new Runnable() { @Override public void run() { CamApsConnection.ensure(LiveAlarmService.this); runOutputs(); updateStatus(); handler.postDelayed(this,2000); } };
    static boolean running() { return current!=null && current.foreground; }
    static boolean live() { LiveAlarmService service=current; return service!=null && service.foreground && service.flow!=null && LivePreferences.alarms(service); }
    static String audioState() { LiveAlarmService service=current; return service==null?AppText.tr("Audio: kein aktiver Hintergrunddienst"):service.audioStatus; }
    static void receive(String key,long posted,double value,NotificationParser.Trend trend) {
        LiveAlarmService service=current;
        if (service!=null) {
            long revision=service.referenceRevision;
            service.handler.post(() -> { if(revision==service.referenceRevision)service.received(key,posted,value,trend); });
        }
    }
    /** Drop queued old interpretations without manufacturing another receipt or changing consent. */
    static void trendReferencesChanged() {
        LiveAlarmService service=current; if(service==null)return;
        long revision=++service.referenceRevision;
        service.handler.post(() -> {
            if(revision!=service.referenceRevision)return;
            service.sourceValid=false; service.trend=NotificationParser.Trend.UNKNOWN;
            service.gap.clear(); service.gapNotified=false;
            if(service.flow!=null)service.flow.stop();
            service.audio.stop(); service.notifications.clearAlarm(); service.releaseOutputLock();
            service.activePlan=null; service.displayedPlan=null; service.displayedAlarmId=null;
            status=AppText.tr("Pfeilzuordnung geändert; warte auf ein neues CamAPS-Update");
            service.lastSummary=""; service.updateStatus();
        });
    }
    static void sourceDisconnected() {
        LiveAlarmService service=current;
        if (service!=null) service.handler.post(() -> {
            service.sourceValid=false; service.window.clear(); service.value=null; service.received=null;
            if(!service.gapNotified) {
                if (service.flow!=null) service.flow.stop(); service.notifications.clearAlarm();
                service.displayedAlarmId=null; service.displayedPlan=null; service.activePlan=null;
            }
            status=AppText.tr("Warte auf ein neues CamAPS-Update"); service.updateStatus();
        });
    }
    static void invalidCurrentSource() {
        LiveAlarmService service=current;
        if (service!=null) service.handler.post(() -> {
            service.sourceValid=false;
            if(!service.gapNotified) {
                if (service.flow!=null) service.flow.stop(); service.audio.stop(); service.notifications.clearAlarm();
                service.displayedAlarmId=null; service.displayedPlan=null; service.activePlan=null;
            }
            status=AppText.tr("CamAPS-Wert derzeit nicht lesbar; warte auf ein neues Update"); service.updateStatus();
        });
    }
    static void configurationReplaced() {
        LiveAlarmService service=current; if(service==null) return;
        if(service.flow!=null) service.flow.stop(); service.audio.stop(); service.window.clear();
        service.notifications.clearAlarm(); service.displayedAlarmId=null; service.displayedPlan=null; service.activePlan=null; service.releaseOutputLock();
        settingsChanged();
    }
    static void settingsChanged() {
        LiveAlarmService service=current;
        if (service!=null) service.handler.post(() -> { service.audio.stop(); service.reload(); service.updateStatus(); HomeWidget.changed(service); });
    }
    static void displayChanged() {
        LiveAlarmService service=current;
        if (service!=null) service.handler.post(() -> { service.lastSummary=""; service.updateStatus(); });
    }
    static void pauseChanged() {
        LiveAlarmService service=current;
        if(service!=null) service.handler.post(() -> {
            if(service.flow!=null) service.flow.stop(); service.audio.stop(); service.notifications.clearAlarm();
            service.activePlan=null; service.displayedAlarmId=null; service.displayedPlan=null;
            service.status=LivePreferences.pauseUntil(service)>RuntimeClock.read(service).wallMs?AppText.tr("Eigene Ausgaben pausiert"):AppText.tr("Pause beendet; warte auf frischen Empfang");
            service.updateStatus();
        });
    }
    @Override public void onCreate() {
        AppText.configure(getResources().getConfiguration().getLocales().get(0));
        super.onCreate(); if(!SafetyConsent.ready(this)){SafetyConsent.disarm(this);stopSelf();return;} try { ProfileController.recover(this); } catch(IOException e) { LivePreferences.set(this,LivePreferences.monitor(this),false); } current=this; notifications=new LiveNotifications(this);
        audio=new PreviewAudio(this,message -> audioStatus=message,() -> foreground && LivePreferences.alarms(this) && SystemReadiness.read(this).ready());
        outputLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":alarm-output");
        outputLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!SafetyConsent.ready(this)){SafetyConsent.disarm(this);stopSelf();return START_NOT_STICKY;}
        if (intent!=null && STOP.equals(intent.getAction())) {
            LivePreferences.set(this,false,false); stopSelf(); return START_NOT_STICKY;
        }
        if (!LivePreferences.monitor(this) || !SystemReadiness.read(this).ready()) {
            status=terminalStatus=AppText.tr("Dienst nicht gestartet: Empfang oder Systemfreigaben fehlen"); stopSelf(); return START_NOT_STICKY;
        }
        CamApsConnection.ensure(this);
        try { foreground(false); }
        catch (RuntimeException e) { status=terminalStatus=AppText.tr("Android hat den Hintergrundstart abgelehnt"); stopSelf(); return START_NOT_STICKY; }
        String action=intent==null?null:intent.getAction();
        if (ACK.equals(action) || SNOOZE.equals(action)) {
            audio.stop(); notifications.clearAlarm(); activePlan=null; displayedAlarmId=null; displayedPlan=null;
            if (SNOOZE.equals(action)) snooze(intent.getStringExtra("id"));
        } else reload();
        handler.removeCallbacks(tick); handler.post(tick);
        HomeWidget.changed(this);
        return START_STICKY;
    }
    private void foreground(boolean playback) {
        if (foreground && playbackForeground==playback) return;
        DisplayStatus shown=displayStatus();
        Notification notification=notifications.status(shown.value,shown.trend,shown.received,shown.fresh,status);
        if (Build.VERSION.SDK_INT>=34) startForeground(LiveNotifications.STATUS_ID,notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE|(playback?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0));
        else startForeground(LiveNotifications.STATUS_ID,notification);
        foreground=true; playbackForeground=playback;
    }
    private File stateDirectory() { return new File(getFilesDir(),"live-receipt-runtime-v1"); }
    private void reload() {
        gap.clear(); gapNotified=false; sourceValid=false; preferences=null;speechCompletionPlan=null;speechCompletionStart=null;
        if (flow!=null) flow.stop(); flow=null; notifications.clearAlarm(); activePlan=null; displayedAlarmId=null; displayedPlan=null; levels.clear();
        if (!LivePreferences.monitor(this)) { stopSelf(); return; }
        try {
            SoundDefaults.install(this);
            LevelStore.Snapshot settings=new LevelStore(getFilesDir()).load();
            OutputPreferences output=new OutputPreferenceStore(getFilesDir()).load().preferences;
            OutputRequirements.require(settings.levels,output); preferences=output;
            for (AlarmLevel level:settings.levels) levels.put(level.rule.id,level);
            List<AlarmEngine.Rule> rules=settings.rules(); AlarmStateStore store=new AlarmStateStore(stateDirectory());
            AlarmStateStore.Clock now=RuntimeClock.read(this); AlarmStateStore.Snapshot old=store.load();
            AlarmStateStore.Snapshot restored=AlarmStateStore.restore(old,rules,now);
            boolean longGap=old.clock!=null && (old.clock.boot!=now.boot || now.wallMs-old.clock.wallMs>ReceiptWindow.MAX_AGE_MS);
            List<AlarmStateStore.Entry> rows=new ArrayList<>();
            for (AlarmStateStore.Entry entry:restored.entries.values()) rows.add(longGap?entry.withTimer(0):entry);
            if (old.revision>0 || !rows.isEmpty()) store.save(old.revision,now,rows);
            flow=AlarmOutputFlow.liveReceipts(store,new ArrayList<>(levels.values()),ReceiptWindow.limits(),output,new AlarmOutputFlow.Port() {
                @Override public boolean available(AlarmOutputFlow.Plan plan) {
                    if (audio.busy() && plan.ruleId!=null && (plan.kind==AlarmEngine.Kind.HIGH || plan.kind==AlarmEngine.Kind.LOW)
                            && activePlan!=null && !activePlan.ruleIds.contains(plan.ruleId)) {
                        audio.stop(); activePlan=null; releaseOutputLock();
                    }
                    return live() && !plan.synthetic && !audio.busy() && audio.available(plan);
                }
                @Override public boolean start(AlarmOutputFlow.Plan plan) {
                    if (!live() || plan.synthetic || !fresh()) return false;
                    try { foreground(plan.toneUri!=null || plan.speech!=null); outputLock.acquire(100000); }
                    catch (RuntimeException e) { status=AppText.tr("Audio-Hintergrundstart abgelehnt"); return false; }
                    boolean accepted=audio.start(plan);
                    activePlan=accepted?plan:null;
                    speechCompletionPlan=accepted && plan.ruleId!=null && plan.kind!=null && plan.speech!=null?plan:null;
                    speechCompletionStart=speechCompletionPlan==null?null:RuntimeClock.read(LiveAlarmService.this);
                    if (accepted && plan.ruleId!=null) {
                        LivePreferences.alarmStarted(LiveAlarmService.this);
                        AlarmLevel level=levels.get(plan.ruleId); if (level!=null && value!=null) {
                            AlarmLevel extra=plan.ruleIds.size()>1?levels.get(plan.ruleIds.get(1)):null;
                            notifications.alarm(level,value,trend,extra==null?null:LevelForm.kindLabel(extra.rule.kind)); displayedAlarmId=plan.ruleId; displayedPlan=plan;
                        }
                    }
                    if (!accepted) releaseOutputLock(); return accepted;
                }
                @Override public void stop() { speechCompletionPlan=null;speechCompletionStart=null;audio.stop(); releaseOutputLock(); }
            },input -> LivePreferences.pauseUntil(this)>RuntimeClock.read(this).wallMs?OutputGuard.Reason.GLOBAL_PAUSE:OutputGuard.Reason.NONE);
            flow.deferValuesUntil(now.elapsedMs+LivePreferences.valueQuietRemaining(this,output.valueQuietMinutes));
            status=LivePreferences.alarms(this)?AppText.tr("Ausgabe eingeschaltet; warte auf ein neues CamAPS-Update"):AppText.tr("Hintergrundanzeige eingeschaltet; Live-Alarme aus");
            if (LivePreferences.alarms(this) && output.speechAllowed()) audio.prepareSpeech();
        } catch (IOException | IllegalArgumentException e) {
            audio.stop(); status=AppText.tr("Alarmzustand/Einstellungen nicht lesbar; Alarmausgabe gesperrt");
        }
    }
    private void received(String key,long posted,double mgdl,NotificationParser.Trend sourceTrend) {
        if (!foreground || !LivePreferences.monitor(this)) return;
        AlarmStateStore.Clock now=RuntimeClock.read(this);
        ReceiptWindow.Intake intake=window.receive(key,posted,mgdl,now);
        if (intake!=ReceiptWindow.Intake.ACCEPTED && intake!=ReceiptWindow.Intake.CLOCK_RESET) return;
        if(gap.waiting() || gapNotified) { audio.stop(); notifications.clearAlarm(); activePlan=null; displayedAlarmId=null; displayedPlan=null; gapNotified=false; }
        sourceValid=true; value=mgdl; trend=sourceTrend; received=now;
        if (intake==ReceiptWindow.Intake.CLOCK_RESET && flow!=null && LivePreferences.alarms(this)) {
            try {
                AlarmStateStore store=new AlarmStateStore(stateDirectory()); AlarmStateStore.Snapshot saved=store.load();
                List<AlarmEngine.Rule> rules=new ArrayList<>(); for (AlarmLevel level:levels.values()) rules.add(level.effectiveRule());
                AlarmStateStore.Snapshot restored=AlarmStateStore.restore(saved,rules,now); List<AlarmStateStore.Entry> reset=new ArrayList<>();
                for (AlarmStateStore.Entry entry:restored.entries.values()) reset.add(entry.withTimer(0));
                if (saved.revision>0) store.save(saved.revision,now,reset);
            } catch (IOException e) { if (flow!=null) flow.stop(); status=AppText.tr("Uhr/Zustand nach Empfangsluecke blockiert"); updateStatus(); return; }
        }
        if (flow!=null && LivePreferences.alarms(this)) {
            Calendar calendar=Calendar.getInstance();
            flow.setOutputUnit(UnitPreferences.display(this,preferences));
            AlarmOutputFlow.Outcome result=flow.evaluate(AlarmOutputFlow.Origin.LIVE,window.points(),now,
                    calendar.get(Calendar.HOUR_OF_DAY)*60+calendar.get(Calendar.MINUTE),window.sessionStart(),trend);
            if (result==AlarmOutputFlow.Outcome.STORAGE_BLOCKED) status=AppText.tr("Alarmzustand nicht verfügbar; keine Ausgabe");
            else if (result==AlarmOutputFlow.Outcome.ACCEPTED) status=AppText.tr("Ausgabe aktiv · Zeitbasis Empfang");
            AlarmEngine.Result checked=flow.lastDecision();
            if (displayedAlarmId!=null && checked!=null) {
                boolean stillMet=false; for (AlarmEngine.Decision decision:checked.decisions) if ((displayedPlan!=null?displayedPlan.ruleIds.contains(decision.rule.id):displayedAlarmId.equals(decision.rule.id)) && decision.conditionMet) stillMet=true;
                if (!stillMet) { audio.stop(); activePlan=null; notifications.clearAlarm(); displayedAlarmId=null; displayedPlan=null; }
                else {
                    List<AlarmLevel> matching=new ArrayList<>();
                    for(AlarmEngine.Decision d:checked.decisions) if(d.conditionMet && (displayedPlan!=null?displayedPlan.ruleIds.contains(d.rule.id):displayedAlarmId.equals(d.rule.id))) matching.add(levels.get(d.rule.id));
                    AlarmLevel primary=matching.isEmpty()?null:matching.get(0),extra=matching.size()>1?matching.get(1):null;
                    if(primary!=null) displayedAlarmId=primary.rule.id;
                    if(primary!=null) notifications.alarm(primary,value,trend,extra==null?null:LevelForm.kindLabel(extra.rule.kind),true);
                }
                if(activePlan!=null) { AlarmOutputFlow.Plan refreshed=flow.refreshPending(activePlan,now); if(audio.refreshPending(refreshed)) activePlan=refreshed; }
            }
        } else status=AppText.tr("Wertempfang aktiv; Live-Alarme aus");
        gap.receive(window.points(),now,sourceTrend,flow==null?null:flow.lastDecision());
        runOutputs(); updateStatus();
    }
    private boolean fresh() {
        if (!sourceValid || received==null || value==null) return false;
        AlarmStateStore.Clock now=RuntimeClock.read(this);
        long elapsed=now.elapsedMs-received.elapsedMs,wall=now.wallMs-received.wallMs;
        return now.boot==received.boot && elapsed>=0 && elapsed<=ReceiptWindow.MAX_AGE_MS && Math.abs(wall-elapsed)<=1000;
    }
    private void runOutputs() {
        if (!foreground) return;
        if (!LivePreferences.monitor(this) || !SystemReadiness.read(this).ready()) {
            if (flow!=null) flow.stop(); status=terminalStatus=AppText.tr("Systemfreigabe/Empfang fehlt; Live-Dienst beendet"); stopSelf(); return;
        }
        AlarmStateStore.Clock now=RuntimeClock.read(this);
        if(completeAlarmSpeech(now))return;
        if (!audio.busy()) { activePlan=null; releaseOutputLock(); CamApsConnection.ensure(this);
        try { foreground(false); } catch (RuntimeException e) { stopSelf(); return; } }
        boolean gapDue=preferences!=null && gap.due(preferences.gap,now);
        if(LivePreferences.alarms(this) && flow!=null && gap.waiting()) {
            if(!gapNotified && (activePlan==null || !"gap_warning".equals(activePlan.ruleId))) { flow.stop(); notifications.clearAlarm(); displayedAlarmId=null; displayedPlan=null; }
            status=AppText.tr("Datenlücke · aktueller Wert unbekannt");
            if(gapDue && !audio.busy() && LivePreferences.pauseUntil(this)<=now.wallMs) startGap(now);
            return;
        }
        if (!LivePreferences.alarms(this) || flow==null || !fresh()) {
            if (!fresh()) { if (flow!=null) flow.stop(); notifications.clearAlarm(); displayedAlarmId=null; displayedPlan=null; activePlan=null; if (received!=null) status=AppText.tr("Kein frischer Empfang; eigene Alarme warten"); }
            return;
        }
        AlarmEngine.Result result=flow.lastDecision(); if (result==null) return;
        List<String> candidates=new ArrayList<>();
        // Preserve the pinned Notifications call order: threshold player, drop, rise, persistent high.
        if (result.selectedThresholdRule!=null) candidates.add(result.selectedThresholdRule);
        for (AlarmEngine.Kind kind:new AlarmEngine.Kind[]{AlarmEngine.Kind.FALLING,AlarmEngine.Kind.RISING,AlarmEngine.Kind.PERSISTENT_HIGH})
            for (AlarmEngine.Decision decision:result.decisions) if (decision.conditionMet && decision.rule.kind==kind) candidates.add(decision.rule.id);
        for (String id:candidates) {
            AlarmOutputFlow.Outcome outcome=flow.alarm(id,RuntimeClock.read(this));
            if (outcome==AlarmOutputFlow.Outcome.ACCEPTED) return;
            if (outcome==AlarmOutputFlow.Outcome.SUPPRESSED) {
                if(flow.suppressionReason()==OutputGuard.Reason.RECOVERING_TREND) { status=AppText.tr("Wiederholung bei Erholung ausgesetzt"); continue; }
                status=AppText.tr("Eigene Alarmpause aktiv"); return;
            }
            if (outcome==AlarmOutputFlow.Outcome.STORAGE_BLOCKED) { status=AppText.tr("Alarmzustand blockiert; keine Ausgabe"); return; }
        }
        if (!audio.busy()) flow.value(RuntimeClock.read(this));
    }
    private boolean completeAlarmSpeech(AlarmStateStore.Clock now){
        if(speechCompletionPlan==null)return false;
        long age=speechCompletionStart==null?-1:now.elapsedMs-speechCompletionStart.elapsedMs;
        boolean clock=speechCompletionStart!=null && now.boot==speechCompletionStart.boot && age>=0 && Math.abs((now.wallMs-speechCompletionStart.wallMs)-age)<=1000;
        boolean expired=clock && age>SPEECH_COMPLETION_MS;
        if(!clock || expired || !live() || flow==null || !sourceValid || !fresh() || LivePreferences.pauseUntil(this)>now.wallMs){
            audio.discardDeferredAlarmSpeech(expired);speechCompletionPlan=null;speechCompletionStart=null;return false;
        }
        if(!audio.readyForDeferredAlarmSpeech())return false;
        AlarmOutputFlow.Plan original=speechCompletionPlan;
        Calendar calendar=Calendar.getInstance();calendar.setTimeInMillis(now.wallMs);
        flow.setOutputUnit(UnitPreferences.display(this,preferences));
        AlarmOutputFlow.Outcome checked=flow.evaluate(AlarmOutputFlow.Origin.LIVE,window.points(),now,
                calendar.get(Calendar.HOUR_OF_DAY)*60+calendar.get(Calendar.MINUTE),window.sessionStart(),trend);
        AlarmOutputFlow.Plan updated=checked==AlarmOutputFlow.Outcome.ACCEPTED?flow.refreshPending(original,now):null;
        speechCompletionPlan=null;speechCompletionStart=null;
        if(updated==null){audio.discardDeferredAlarmSpeech(false);return false;}
        try{foreground(true);outputLock.acquire(100000);}catch(RuntimeException e){audio.discardDeferredAlarmSpeech(false);return false;}
        boolean accepted=audio.completeDeferredAlarmSpeech(updated);
        if(accepted)activePlan=updated;else{audio.discardDeferredAlarmSpeech(false);releaseOutputLock();}
        return accepted;
    }
    private void startGap(AlarmStateStore.Clock now) {
        AlarmOutputFlow.Plan plan=AlarmOutputFlow.Plan.gap(preferences.gap,preferences,gap.message(now,preferences.speechLanguage,preferences.gap.speechTemplate));
        if(!live() || !audio.available(plan)) return;
        gap.reserve(preferences.gap,now); // Finite attempt; no catch-up loop after failures.
        try { foreground(plan.toneUri!=null || plan.speech!=null); outputLock.acquire(100000); }
        catch(RuntimeException e) { status=AppText.tr("Lückenwarnung: Ausgabe nicht verfügbar"); return; }
        if(audio.start(plan)) {
            activePlan=plan; gapNotified=true; notifications.gap(gap.message(now,preferences.speechLanguage,preferences.gap.speechTemplate)); LivePreferences.alarmStarted(this);
            if(flow!=null) flow.deferValuesUntil(now.elapsedMs+preferences.valueQuietMinutes*60000L);
        } else releaseOutputLock();
    }
    private void snooze(String id) {
        if (flow==null || id==null) return; AlarmLevel level=levels.get(id);
        if (level==null || !LevelForm.hasSnooze(level.rule.kind)) return;
        AlarmOutputFlow.Outcome result=flow.snooze(id,level.rule.outputs.snoozeMinutes,RuntimeClock.read(this));
        status=result==AlarmOutputFlow.Outcome.ACCEPTED?AppText.tr("Alarm quittiert und gespeichert pausiert"):AppText.tr("Snooze konnte nicht gespeichert werden");
    }
    private static final class DisplayStatus {
        final Double value;final NotificationParser.Trend trend;final long received;final boolean fresh;
        DisplayStatus(Double v,NotificationParser.Trend t,long r,boolean f){value=v;trend=t;received=r;fresh=f;}
    }
    /** Display retention never supplies an input to fresh(), evaluate() or an audio path. */
    private DisplayStatus displayStatus() {
        ProbeState.Observation shown=ProbeState.display(this);
        if(shown!=null)return new DisplayStatus(shown.value,shown.trend,shown.receivedWall,ProbeState.displayFresh(this,shown)&&fresh());
        if(ProbeState.displayAllowed(this)&&value!=null&&received!=null) {
            AlarmStateStore.Clock now=RuntimeClock.read(this);
            long elapsed=now.elapsedMs-received.elapsedMs,wall=now.wallMs-received.wallMs;
            if(now.boot==received.boot&&elapsed>=0&&wall>=0&&elapsed<=ProbeState.DISPLAY_MAX_AGE_MS&&Math.abs(wall-elapsed)<=60000)
                return new DisplayStatus(value,trend,received.wallMs,fresh());
        }
        return new DisplayStatus(null,NotificationParser.Trend.UNKNOWN,0,false);
    }
    private void updateStatus() {
        if (notifications==null || !foreground) return;
        DisplayStatus display=displayStatus(); boolean fresh=display.fresh; long age=display.received==0?-1:Math.max(0,(System.currentTimeMillis()-display.received)/60000);
        String detail=status+(display.received>0?AppText.tr(" · Empfang vor ")+age+" Min.":"");
        NotificationParser.Trend shown=display.trend;
        String signature=detail+"|"+display.value+"|"+display.received+"|"+shown+"|"+fresh+"|"+LivePreferences.publicValue(this)+"|"+LivePreferences.nativeDisplay(this)+"|"+UnitPreferences.display(this,preferences);
        if (!signature.equals(lastSummary)) { notifications.update(display.value,shown,display.received,fresh,detail); lastSummary=signature;
            handler.postDelayed(() -> DisplayDiagnostics.write(this),300); }
    }
    private void releaseOutputLock() { if (outputLock!=null && outputLock.isHeld()) outputLock.release(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); if (flow!=null) flow.stop(); if (audio!=null) audio.close();
        releaseOutputLock(); if (notifications!=null) notifications.clearAlarm();
        stopForeground(STOP_FOREGROUND_REMOVE); foreground=false; if (current==this) current=null; status=terminalStatus==null?AppText.tr("Live-Dienst ausgeschaltet"):terminalStatus; HomeWidget.changed(this); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
