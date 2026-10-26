// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;

import de.giesen.camaps.companion.prototype.HistoryAnalysis;
import de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType;
import de.giesen.camaps.companion.prototype.alarm.xdrip.BgReading;
import de.giesen.camaps.companion.prototype.alarm.xdrip.PersistentHigh;
import de.giesen.camaps.companion.prototype.alarm.xdrip.XDripRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data -> pinned xDrip decisions -> decision port. No audio/notifications/sensor initialization. */
public final class AlarmEngine {
    public enum Kind { HIGH, LOW, PERSISTENT_HIGH, RISING, FALLING }
    public enum Status { DISABLED, DATA_BLOCKED, CLOCK_BLOCKED, EVALUATED }
    public enum OutputMode { TONES_ONLY, SPOKEN_ALARMS, SPOKEN_ALARMS_AND_VALUES }
    public interface DecisionSink { void onDecision(Result result); }

    public static final class Outputs {
        public final boolean tone, speech;
        public final int repeatMinutes, snoozeMinutes;
        public Outputs(boolean tone,boolean speech,int repeatMinutes,int snoozeMinutes) {
            if (repeatMinutes<0 || snoozeMinutes<0) throw new IllegalArgumentException("Invalid output interval");
            this.tone=tone; this.speech=speech; this.repeatMinutes=repeatMinutes; this.snoozeMinutes=snoozeMinutes;
        }
    }
    /** Multiple independently configured levels are permitted for every kind. No built-in thresholds. */
    public static final class Rule {
        public final String id;
        public final Kind kind;
        public final boolean enabled, allDay;
        public final double threshold;
        public final int durationMinutes,startMinute,endMinute;
        public final Outputs outputs;
        public Rule(String id,Kind kind,boolean enabled,double threshold,int durationMinutes,
                    boolean allDay,int startMinute,int endMinute,Outputs outputs) {
            if (id==null || !id.matches("[A-Za-z0-9_-]{1,64}") || kind==null || outputs==null
                    || !Double.isFinite(threshold) || threshold<=0 || durationMinutes<0
                    || startMinute<0 || startMinute>1440 || endMinute<0 || endMinute>1440)
                throw new IllegalArgumentException("Invalid explicit alarm rule");
            if ((kind==Kind.RISING || kind==Kind.FALLING) && (!Float.isFinite((float)threshold) || (float)threshold<=0))
                throw new IllegalArgumentException("Rate outside source float range");
            this.id=id; this.kind=kind; this.enabled=enabled; this.threshold=threshold;
            this.durationMinutes=durationMinutes; this.allDay=allDay; this.startMinute=startMinute; this.endMinute=endMinute;
            this.outputs=outputs;
        }
    }
    public static final class Decision {
        public final Rule rule;
        public final boolean conditionMet, recovering, toneRequested, speechRequested, resetNotificationState;
        private Decision(Rule rule,boolean condition,boolean recovering,boolean resetNotificationState,OutputMode mode) {
            this.rule=rule; conditionMet=condition; this.recovering=recovering;
            this.resetNotificationState=resetNotificationState;
            toneRequested=condition && rule.outputs.tone;
            speechRequested=condition && rule.outputs.speech && mode!=OutputMode.TONES_ONLY;
        }
    }
    public static final class Result {
        public final Status status;
        public final HistoryAnalysis.Quality quality;
        public final List<Decision> decisions;
        /** xDrip priority among HIGH/LOW candidates; arbitration with other kinds remains separate. */
        public final String selectedThresholdRule;
        /** This integration stage has no platform delivery route. Requested outputs are not played. */
        public final boolean platformDeliveryEnabled=false;
        private Result(Status status,HistoryAnalysis.Quality quality,List<Decision> decisions,String selected) {
            this.status=status; this.quality=quality;
            this.decisions=Collections.unmodifiableList(new ArrayList<>(decisions)); selectedThresholdRule=selected;
        }
        public int matchedRules() { int count=0; for (Decision decision:decisions) if (decision.conditionMet) count++; return count; }
    }

    private final List<Rule> rules;
    private final HistoryAnalysis.Limits limits;
    private final OutputMode outputMode;
    private final HistoryAnalysis.TimeBasis timeBasis;
    private final Map<String,Map<String,Long>> states=new HashMap<>();
    private long previousNow;
    public AlarmEngine(List<Rule> rules,HistoryAnalysis.Limits limits,OutputMode mode) {
        this(rules,limits,mode,HistoryAnalysis.TimeBasis.MEASUREMENT);
    }
    public AlarmEngine(List<Rule> rules,HistoryAnalysis.Limits limits,OutputMode mode,HistoryAnalysis.TimeBasis timeBasis) {
        if (rules==null || limits==null || mode==null || timeBasis==null) throw new IllegalArgumentException("Missing engine configuration");
        this.timeBasis=timeBasis;
        Set<String> ids=new HashSet<>();
        for (Rule rule:rules) if (rule==null || !ids.add(rule.id)) throw new IllegalArgumentException("Duplicate/missing rule");
        this.rules=Collections.unmodifiableList(new ArrayList<>(rules)); this.limits=limits; outputMode=mode;
    }
    public static AlarmEngine inactive() {
        // No rules: these technical bounds do not become active alarm defaults.
        return new AlarmEngine(Collections.emptyList(),new HistoryAnalysis.Limits(1,Long.MAX_VALUE,0),OutputMode.TONES_ONLY);
    }
    public synchronized void reset() { states.clear(); previousNow=0; }
    public synchronized Map<String,Long> persistentTimers() {
        Map<String,Long> result=new HashMap<>();
        for (Rule rule:rules) if (rule.enabled && rule.kind==Kind.PERSISTENT_HIGH) {
            Map<String,Long> state=states.get(rule.id); Long since=state==null?null:state.get("persistent_high_since");
            if (since!=null && since>0) result.put(rule.id,since);
        }
        return Collections.unmodifiableMap(result);
    }
    public synchronized void restorePersistentTimers(Map<String,Long> timers) {
        states.clear(); previousNow=0;
        for (Rule rule:rules) if (rule.enabled && rule.kind==Kind.PERSISTENT_HIGH && timers.containsKey(rule.id)) {
            long since=timers.get(rule.id); if (since<=0) throw new IllegalArgumentException("Invalid persistent timer");
            Map<String,Long> state=new HashMap<>(); state.put("persistent_high_since",since); states.put(rule.id,state);
        }
    }
    private Result blocked(Status status,HistoryAnalysis.Quality quality,DecisionSink sink) {
        states.clear(); Result result=new Result(status,quality,Collections.emptyList(),null);
        if (sink!=null) sink.onDecision(result); return result;
    }
    public synchronized Result evaluate(List<HistoryAnalysis.Point> window,long nowMs,int minuteOfDay,
                                        Long confirmedSourceSessionStart,DecisionSink sink) {
        if (window==null || nowMs<=0 || minuteOfDay<0 || minuteOfDay>=1440
                || (confirmedSourceSessionStart!=null && (confirmedSourceSessionStart<=0 || confirmedSourceSessionStart>nowMs)))
            throw new IllegalArgumentException("Invalid decision context");
        boolean enabled=false; for (Rule rule:rules) enabled|=rule.enabled;
        if (!enabled) return blocked(Status.DISABLED,null,sink);
        if (previousNow>nowMs) { previousNow=nowMs; return blocked(Status.CLOCK_BLOCKED,null,sink); }
        previousNow=nowMs;
        if (window.size()>2000) return blocked(Status.DATA_BLOCKED,null,sink);
        HistoryAnalysis.Result quality=HistoryAnalysis.analyze(window,nowMs,limits,timeBasis);
        if (!quality.usable() && quality.quality!=HistoryAnalysis.Quality.TOO_SHORT)
            return blocked(Status.DATA_BLOCKED,quality.quality,sink);
        List<BgReading> data=new ArrayList<>();
        for (HistoryAnalysis.Point point:window) {
            if (!data.isEmpty() && data.get(data.size()-1).timestamp==point.timestampMs) continue;
            BgReading converted=new BgReading(); converted.timestamp=point.timestampMs;
            converted.calculated_value=point.mgdl; converted.hide_slope=!quality.usable(); data.add(converted);
        }
        List<BgReading> rateData=timeBasis==HistoryAnalysis.TimeBasis.RECEIPT?ReceiptRateHistory.samples(data):data;
        boolean rateReady=quality.usable() && rateData.size()>=4;
        List<Decision> decisions=new ArrayList<>();
        AlertType selected=null; String selectedId=null;
        synchronized (XDripRuntime.class) {
            for (Rule rule:rules) {
                if (!rule.enabled) { decisions.add(new Decision(rule,false,false,true,outputMode)); continue; }
                Map<String,Long> state=states.get(rule.id);
                if (state==null) { state=new HashMap<>(); states.put(rule.id,state); }
                boolean rateRule=LevelForm.isRate(rule.kind);
                XDripRuntime.Environment env=new XDripRuntime.Environment(nowMs,minuteOfDay,confirmedSourceSessionStart,rateRule?rateData:data,state);
                env.preferences.put("alerts_disabled_until",0L);
                env.preferences.put("persistent_high_alert_enabled",rule.kind==Kind.PERSISTENT_HIGH);
                env.preferences.put("high_value_is_persistent_high_threshold",false);
                env.preferences.put("persistent_high_threshold",Double.toString(rule.threshold));
                env.preferences.put("persistent_high_threshold_mins",Integer.toString(rule.durationMinutes));
                env.preferences.put("rising_alert",rule.kind==Kind.RISING && rateReady);
                env.preferences.put("falling_alert",rule.kind==Kind.FALLING && rateReady);
                env.preferences.put("rising_bg_val",Float.toString((float)rule.threshold));
                env.preferences.put("falling_bg_val",Float.toString((float)rule.threshold));
                XDripRuntime.enter(env);
                try {
                    boolean condition=false,recovering=false,resetNotification=false;
                    boolean inTime=AlertType.s_in_time_frame(rule.allDay,rule.startMinute,rule.endMinute);
                    if (rule.kind==Kind.HIGH || rule.kind==Kind.LOW) {
                        AlertType candidate=new AlertType(); candidate.active=true; candidate.threshold=rule.threshold;
                        candidate.above=rule.kind==Kind.HIGH; candidate.all_day=rule.allDay;
                        candidate.start_time_minutes=rule.startMinute; candidate.end_time_minutes=rule.endMinute;
                        // get_highest_active_alert excludes xDrip's <=14 special codes before should_alarm.
                        condition=data.get(data.size()-1).calculated_value>14 && candidate.should_alarm(data.get(data.size()-1).calculated_value);
                        recovering=quality.usable() && BgReading.trendingToAlertEnd(new XDripRuntime.Context(),candidate.above);
                        if (condition) {
                            AlertType choice=selected==null?candidate:AlertType.HigherAlert(selected,candidate);
                            if (choice==candidate) { selected=choice; selectedId=rule.id; }
                        }
                    } else if (rule.kind==Kind.PERSISTENT_HIGH) {
                        PersistentHigh.persistentHighThreshold=rule.threshold;
                        // The imported reset/timer path runs even when an optional outer schedule is closed.
                        PersistentHigh.checkForPersistentHigh(); condition=inTime && env.persistentRaised;
                        resetNotification=env.persistentCancelled || data.get(data.size()-1).calculated_value<=rule.threshold;
                    } else if (rule.kind==Kind.RISING) {
                        BgReading.checkForRisingAllert(new XDripRuntime.Context()); condition=inTime && env.risingRaised
                                && (timeBasis!=HistoryAnalysis.TimeBasis.RECEIPT || ReceiptRateHistory.meets(rateData,rule.threshold,false));
                        resetNotification=rateReady && !condition;
                    } else {
                        BgReading.checkForDropAllert(new XDripRuntime.Context()); condition=inTime && env.fallingRaised
                                && (timeBasis!=HistoryAnalysis.TimeBasis.RECEIPT || ReceiptRateHistory.meets(rateData,rule.threshold,true));
                        resetNotification=rateReady && !condition;
                    }
                    if (env.configurationWarnings!=0) throw new IllegalStateException("Unexpected source configuration fallback");
                    decisions.add(new Decision(rule,condition,recovering,resetNotification,outputMode));
                } finally { XDripRuntime.leave(); }
            }
        }
        Result result=new Result(Status.EVALUATED,quality.quality,decisions,selectedId);
        if (sink!=null) sink.onDecision(result); return result;
    }
}
