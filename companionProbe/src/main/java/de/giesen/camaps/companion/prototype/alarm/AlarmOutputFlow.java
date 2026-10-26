// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;

import de.giesen.camaps.companion.prototype.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock;
import java.io.IOException;
import java.util.*;

/** Durable decisions for an explicit origin; the live factory additionally requires Android permission gates. */
public final class AlarmOutputFlow {
    public enum Origin { SYNTHETIC, LIVE }
    public enum Outcome { ACCEPTED, LIVE_BLOCKED, DATA_BLOCKED, DISABLED, CONDITION_FALSE, PAUSED, SUPPRESSED, NOT_DUE, OUTPUT_UNAVAILABLE, STORAGE_BLOCKED, ATTEMPT_FAILED }
    public interface Port {
        boolean available(Plan plan);
        boolean start(Plan plan);
        void stop();
    }
    public static final class Plan {
        public final String ruleId,toneUri,speech,vibrationPattern;
        public final Kind kind;
        public final boolean synthetic;
        public final List<String> ruleIds,toneUris,vibrationPatterns;
        private Plan(String id,Kind kind,String tone,String speech,boolean synthetic) { this(id,kind,tone,speech,synthetic,null); }
        private Plan(String id,Kind kind,String tone,String speech,boolean synthetic,String vibration) {
            this(id,kind,tone,speech,synthetic,vibration,id==null?Collections.emptyList():Collections.singletonList(id),tone==null?Collections.emptyList():Collections.singletonList(tone),vibration==null?Collections.emptyList():Collections.singletonList(vibration));
        }
        private Plan(String id,Kind kind,String tone,String speech,boolean synthetic,String vibration,List<String> ids,List<String> tones,List<String> vibrations) {
            vibrationPattern=vibration; ruleId=id; this.kind=kind; toneUri=tone; this.speech=speech; this.synthetic=synthetic;
            ruleIds=Collections.unmodifiableList(new ArrayList<>(ids)); toneUris=Collections.unmodifiableList(new ArrayList<>(tones)); vibrationPatterns=Collections.unmodifiableList(new ArrayList<>(vibrations));
        }
        public static Plan gap(GapSettings gap,OutputPreferences p,String text) {
            return new Plan("gap_warning",null,gap.toneAllowed(p)?gap.toneUri:null,gap.speechAllowed(p)?text:null,false,gap.vibrationAllowed(p)?gap.vibrationPattern:null);
        }
    }
    private final PersistentAlarmSession session;
    private final List<AlarmLevel> levels;
    private final OutputPreferences preferences;
    private final HistoryAnalysis.Limits limits;
    private final Port port;
    private final OutputGuard guard;
    private final Origin allowedOrigin;
    private OutputGuard.Reason suppression=OutputGuard.Reason.NONE;
    private final Map<String,Integer> variants=new HashMap<>();
    private Result decision;
    private HistoryAnalysis.Point latest;
    private NotificationParser.Trend trend=NotificationParser.Trend.UNKNOWN;
    private Clock checked;
    private List<HistoryAnalysis.Point> checkedPoints;
    private Long sourceStart;
    private int minuteOffset;
    private long nextValueElapsed=-1;
    private long valueQuietUntil;
    public void deferValuesUntil(long elapsed) { valueQuietUntil=Math.max(valueQuietUntil,elapsed); }
    private boolean paused;
    private de.giesen.camaps.companion.prototype.GlucoseUnit outputUnit;
    public void setOutputUnit(de.giesen.camaps.companion.prototype.GlucoseUnit unit) { outputUnit=java.util.Objects.requireNonNull(unit); }
    public AlarmOutputFlow(AlarmStateStore store,List<AlarmLevel> levels,HistoryAnalysis.Limits limits,OutputPreferences preferences,Port port) {
        this(store,levels,limits,preferences,port,OutputGuard.disabled());
    }
    public AlarmOutputFlow(AlarmStateStore store,List<AlarmLevel> levels,HistoryAnalysis.Limits limits,OutputPreferences preferences,Port port,OutputGuard guard) {
        this(store,levels,limits,preferences,port,guard,Origin.SYNTHETIC);
    }
    /** Explicit live factory; Android callers must additionally require the user's live switch and ready system permissions. */
    public static AlarmOutputFlow liveReceipts(AlarmStateStore store,List<AlarmLevel> levels,HistoryAnalysis.Limits limits,OutputPreferences preferences,Port port,OutputGuard guard) {
        return new AlarmOutputFlow(store,levels,limits,preferences,port,guard,Origin.LIVE);
    }
    private AlarmOutputFlow(AlarmStateStore store,List<AlarmLevel> levels,HistoryAnalysis.Limits limits,OutputPreferences preferences,Port port,OutputGuard guard,Origin origin) {
        if (store==null || levels==null || limits==null || preferences==null || port==null || guard==null) throw new IllegalArgumentException();
        this.guard=guard; allowedOrigin=origin;
        this.levels=Collections.unmodifiableList(new ArrayList<>(levels)); this.preferences=preferences; this.limits=limits; this.port=port;
        List<Rule> rules=new ArrayList<>(); for (AlarmLevel level:levels) rules.add(level.effectiveRule());
        session=new PersistentAlarmSession(store,rules,limits,preferences.engineMode(),
                origin==Origin.LIVE?HistoryAnalysis.TimeBasis.RECEIPT:HistoryAnalysis.TimeBasis.MEASUREMENT);
    }
    private boolean allowed(AlarmEngine.Rule rule,boolean recovering,boolean periodic) {
        try { suppression=guard.check(new OutputGuard.Input(rule,latest.mgdl,trend,recovering,periodic)); }
        catch (RuntimeException e) { suppression=OutputGuard.Reason.POLICY_NOT_CONFIGURED; }
        if (suppression==null) suppression=OutputGuard.Reason.POLICY_NOT_CONFIGURED;
        if (suppression==OutputGuard.Reason.NONE) return true;
        port.stop(); decision=null; checked=null; return false;
    }
    public OutputGuard.Reason suppressionReason() { return suppression; }
    public Outcome evaluate(Origin origin,List<HistoryAnalysis.Point> points,Clock now,int minuteOfDay,Long sourceStart,NotificationParser.Trend trend) {
        decision=null; latest=null; checked=null;
        if (origin!=allowedOrigin) { port.stop(); return Outcome.LIVE_BLOCKED; }
        try {
            Result result=session.evaluate(points,now,minuteOfDay,sourceStart,null);
            if (result.status==Status.DISABLED) { port.stop(); return Outcome.DISABLED; }
            if (result.status!=Status.EVALUATED) { port.stop(); return Outcome.DATA_BLOCKED; }
            this.decision=result; this.latest=points.get(points.size()-1); this.checked=now;
            checkedPoints=new ArrayList<>(points); this.sourceStart=sourceStart; minuteOffset=minuteOfDay-calendarMinute(now.wallMs);
            this.trend=trend==null?NotificationParser.Trend.UNKNOWN:trend;
            if (preferences.periodicAllowed() && nextValueElapsed<0) nextValueElapsed=Math.addExact(now.elapsedMs,preferences.valueIntervalMinutes*60000L);
            return Outcome.ACCEPTED;
        } catch (IOException e) { port.stop(); return Outcome.STORAGE_BLOCKED; }
        catch (IllegalArgumentException | ArithmeticException e) { port.stop(); return Outcome.DATA_BLOCKED; }
    }
    private static int calendarMinute(long wall) {
        Calendar calendar=Calendar.getInstance(); calendar.setTimeInMillis(wall);
        return calendar.get(Calendar.HOUR_OF_DAY)*60+calendar.get(Calendar.MINUTE);
    }
    private boolean fresh(Clock now) {
        if (decision==null || latest==null || checked==null || now.boot!=checked.boot || now.elapsedMs<checked.elapsedMs) return false;
        long elapsed=now.elapsedMs-checked.elapsedMs;
        // A wall-clock jump after evaluation requires another evaluation before any output.
        try {
            long offset=Math.subtractExact(Math.subtractExact(now.wallMs,checked.wallMs),elapsed);
            return offset>=-1000 && offset<=1000 && now.wallMs>=latest.timestampMs
                    && now.wallMs-latest.timestampMs<=limits.maximumAgeMs;
        } catch (ArithmeticException e) { return false; }
    }
    public Outcome alarm(String id,Clock now) {
        if (paused) return Outcome.PAUSED;
        if (!fresh(now)) { port.stop(); return Outcome.DATA_BLOCKED; }
        AlarmLevel level=null; for (AlarmLevel candidate:levels) if (candidate.rule.id.equals(id)) level=candidate;
        if (level==null || !level.rule.enabled) return Outcome.DISABLED;
        Decision match=null; for (Decision candidate:decision.decisions) if (candidate.rule.id.equals(id)) match=candidate;
        if (match==null || !match.conditionMet || (LevelForm.hasSnooze(level.rule.kind) && !id.equals(decision.selectedThresholdRule))) return Outcome.CONDITION_FALSE;
        if(LevelForm.isRate(level.rule.kind)) {
            AlarmLevel strongest=strongestRate(level.rule.kind);
            if(strongest!=null && !strongest.rule.id.equals(id)) return Outcome.CONDITION_FALSE;
        }
        boolean tone=preferences.alarmToneAllowed(level),speech=preferences.alarmSpeechAllowed(level);
        boolean vibration=preferences.alarmVibrationAllowed(level);
        if (!tone && !speech && !vibration) return Outcome.DISABLED;
        if (!allowed(level.rule,match.recovering,false)) return Outcome.SUPPRESSED;
        try {
            if (!session.deliveryReady(id,now)) return Outcome.NOT_DUE;
            if(recoverySuppressed(level,match,now)) { suppression=OutputGuard.Reason.RECOVERING_TREND; return Outcome.SUPPRESSED; }
            int variant=variants.containsKey(id)?variants.get(id):0;
            long duration=0;
            AlarmStateStore.Entry state=session.view(now).entries.get(id);
            if (state!=null && state.highSince>0) duration=(now.wallMs-state.highSince)/60000;
            String spoken=speech?SpeechTemplate.render(level.speechText(variant,preferences.speechLanguage),level,
                    new SpeechTemplate.Values(latest.mgdl,trend,(now.wallMs-latest.timestampMs)/1000,duration,preferences.trendSpeech,preferences.speechLanguage,outputUnit==null?preferences.unitMode.resolve(null):outputUnit),false,allowedOrigin==Origin.SYNTHETIC):null;
            Plan plan=new Plan(id,level.rule.kind,tone?level.toneUri:null,spoken,allowedOrigin==Origin.SYNTHETIC,vibration?level.vibrationPattern:null);
            List<AlarmLevel> group=new ArrayList<>(); group.add(level);
            if(LevelForm.hasSnooze(level.rule.kind)) {
                AlarmLevel strongest=null;
                for(Kind kind:new Kind[]{Kind.FALLING,Kind.RISING}) {
                    AlarmLevel candidate=strongestRate(kind);
                    if(candidate!=null && session.deliveryReady(candidate.rule.id,now)) { strongest=candidate; break; }
                }
                if(strongest!=null) group.add(strongest);
            }
            if(group.size()>1) plan=combined(group,now);
            if (!port.available(plan)) return Outcome.OUTPUT_UNAVAILABLE;
            if (!session.deliveryReady(id,now)) return Outcome.NOT_DUE;
            // Recheck schedules/conditions at actual delivery, without writing on every background tick.
            Result current=session.evaluate(checkedPoints,now,Math.floorMod(calendarMinute(now.wallMs)+minuteOffset,1440),sourceStart,null);
            decision=current; checked=now;
            boolean stillEligible=false;
            for (Decision candidate:current.decisions) if (candidate.rule.id.equals(id) && candidate.conditionMet
                    && (!LevelForm.hasSnooze(candidate.rule.kind) || id.equals(current.selectedThresholdRule))) stillEligible=true;
            if (!stillEligible) return Outcome.CONDITION_FALSE;
            // Check every bundled condition again; reserve each before sending any output.
            for(AlarmLevel member:group) {
                boolean valid=false; for(Decision d:current.decisions) if(d.rule.id.equals(member.rule.id) && d.conditionMet) valid=true;
                if(!valid || !session.deliveryReady(member.rule.id,now)) return Outcome.CONDITION_FALSE;
            }
            List<AlarmLevel> reserved=new ArrayList<>(group);
            for(AlarmLevel member:group) if(LevelForm.isRate(member.rule.kind)) for(Decision d:current.decisions) {
                AlarmLevel weaker=find(d.rule.id);
                if(weaker!=null && weaker.rule.kind==member.rule.kind && d.conditionMet && hasOutput(weaker)
                        && weaker.rule.threshold<=member.rule.threshold && !reserved.contains(weaker) && session.deliveryReady(weaker.rule.id,now)) reserved.add(weaker);
            }
            for(AlarmLevel member:reserved) session.claimDelivery(member.rule.id,now);
            for(AlarmLevel member:group) { int sequence=variants.getOrDefault(member.rule.id,0); variants.put(member.rule.id,sequence==Integer.MAX_VALUE?0:sequence+1); }
            boolean accepted=port.start(plan);
            if(accepted) for(AlarmLevel member:group) session.accepted(member.rule.id,now);
            if (accepted && preferences.valueQuietMinutes>0) deferValuesUntil(Math.addExact(now.elapsedMs,preferences.valueQuietMinutes*60000L));
            return accepted?Outcome.ACCEPTED:Outcome.ATTEMPT_FAILED;
        } catch (IOException e) { port.stop(); return Outcome.STORAGE_BLOCKED; }
        catch (RuntimeException e) { port.stop(); return Outcome.ATTEMPT_FAILED; }
    }
    private AlarmLevel strongestRate(Kind kind) {
        AlarmLevel strongest=null;
        for(Decision d:decision.decisions) if(d.conditionMet && d.rule.kind==kind) {
            AlarmLevel candidate=find(d.rule.id); if(candidate!=null && hasOutput(candidate)
                    && (strongest==null || candidate.rule.threshold>strongest.rule.threshold)) strongest=candidate;
        }
        return strongest;
    }
    /** Refresh text for a still-valid ongoing output, without claiming or replaying any signal. */
    public Plan refreshPending(Plan previous,Clock now) {
        if(previous==null || previous.ruleIds.isEmpty() || !fresh(now)) return null;
        List<AlarmLevel> group=new ArrayList<>();
        for(String id:previous.ruleIds) {
            AlarmLevel level=find(id); if(level==null || !hasOutput(level)) continue;
            boolean met=false; for(Decision d:decision.decisions) if(d.rule.id.equals(id) && d.conditionMet) met=true;
            if(!met || LevelForm.hasSnooze(level.rule.kind) && !id.equals(decision.selectedThresholdRule)) continue;
            group.add(level);
        }
        if(group.isEmpty()) return null;
        try { return combined(group,now,true); } catch(IOException e) { return null; }
    }
    private AlarmLevel find(String id) { for(AlarmLevel level:levels) if(level.rule.id.equals(id)) return level; return null; }
    private boolean hasOutput(AlarmLevel level) { return preferences.alarmToneAllowed(level) || preferences.alarmSpeechAllowed(level) || preferences.alarmVibrationAllowed(level); }
    private boolean recoverySuppressed(AlarmLevel level,Decision decision,Clock now) throws IOException {
        if(!level.suppressRecoveryRepeats || !decision.recovering) return false;
        AlarmStateStore.Entry state=session.view(now).entries.get(level.rule.id);
        if(state==null || !state.acceptedInEpisode) return false;
        return level.rule.kind==Kind.LOW && (trend==NotificationParser.Trend.UP_RIGHT || trend==NotificationParser.Trend.UP || trend==NotificationParser.Trend.DOUBLE_UP)
                || level.rule.kind==Kind.HIGH && (trend==NotificationParser.Trend.DOWN_RIGHT || trend==NotificationParser.Trend.DOWN || trend==NotificationParser.Trend.DOUBLE_DOWN);
    }
    private Plan combined(List<AlarmLevel> group,Clock now) throws IOException {
        return combined(group,now,false);
    }
    private Plan combined(List<AlarmLevel> group,Clock now,boolean refreshing) throws IOException {
        List<String> ids=new ArrayList<>(),tones=new ArrayList<>(),vibrations=new ArrayList<>(),words=new ArrayList<>();
        for(AlarmLevel level:group) {
            ids.add(level.rule.id); if(preferences.alarmToneAllowed(level)) tones.add(level.toneUri); if(preferences.alarmVibrationAllowed(level)) vibrations.add(level.vibrationPattern);
            AlarmStateStore.Entry state=session.view(now).entries.get(level.rule.id);
            long duration=state!=null && state.highSince>0?(now.wallMs-state.highSince)/60000:0;
            int variant=variants.getOrDefault(level.rule.id,0);
            if(refreshing) variant=variant==0?Integer.MAX_VALUE:variant-1;
            if(preferences.alarmSpeechAllowed(level)) words.add(SpeechTemplate.render(level.speechText(variant,preferences.speechLanguage),level,
                    new SpeechTemplate.Values(latest.mgdl,trend,(now.wallMs-latest.timestampMs)/1000,duration,preferences.trendSpeech,preferences.speechLanguage,outputUnit==null?preferences.unitMode.resolve(null):outputUnit),false,allowedOrigin==Origin.SYNTHETIC));
        }
        AlarmLevel first=group.get(0); String speech=words.isEmpty()?null:String.join(". ",words);
        return new Plan(first.rule.id,first.rule.kind,tones.isEmpty()?null:tones.get(0),speech,allowedOrigin==Origin.SYNTHETIC,vibrations.isEmpty()?null:vibrations.get(0),ids,tones,vibrations);
    }
    public Outcome value(Clock now) {
        if (paused) return Outcome.PAUSED;
        if (!preferences.periodicAllowed()) return Outcome.DISABLED;
        if (!fresh(now)) { port.stop(); return Outcome.DATA_BLOCKED; }
        if (now.elapsedMs<valueQuietUntil) return Outcome.NOT_DUE;
        if (nextValueElapsed<0 || now.elapsedMs<nextValueElapsed) return Outcome.NOT_DUE;
        if (!allowed(null,false,true)) return Outcome.SUPPRESSED;
        Plan plan=new Plan(null,null,null,SpeechTemplate.render(preferences.valueTemplate,null,
                new SpeechTemplate.Values(latest.mgdl,trend,(now.wallMs-latest.timestampMs)/1000,0,preferences.trendSpeech,preferences.speechLanguage,outputUnit==null?preferences.unitMode.resolve(null):outputUnit),true,allowedOrigin==Origin.SYNTHETIC),allowedOrigin==Origin.SYNTHETIC);
        try {
            if (!port.available(plan)) return Outcome.OUTPUT_UNAVAILABLE;
            // No catch-up loop; a single explicit request can start at most one announcement.
            nextValueElapsed=Math.addExact(now.elapsedMs,preferences.valueIntervalMinutes*60000L);
            return port.start(plan)?Outcome.ACCEPTED:Outcome.ATTEMPT_FAILED;
        } catch (RuntimeException e) { port.stop(); return Outcome.ATTEMPT_FAILED; }
    }
    public Outcome snooze(String id,int minutes,Clock now) {
        port.stop();
        try { session.snooze(id,minutes,now); decision=null; checked=null; return Outcome.ACCEPTED; }
        catch (IOException | IllegalArgumentException e) { return Outcome.STORAGE_BLOCKED; }
    }
    /** Test control only; a persisted global pause policy is not inferred here. */
    public void pause(boolean value) { paused=value; port.stop(); if (!value) { decision=null; checked=null; nextValueElapsed=-1; } }
    public void stop() { port.stop(); decision=null; checked=null; nextValueElapsed=-1; }
    public Result lastDecision() { return decision; }
}
