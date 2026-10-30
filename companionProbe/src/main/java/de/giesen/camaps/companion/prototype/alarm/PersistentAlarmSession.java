// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import de.giesen.camaps.companion.prototype.HistoryAnalysis;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.*;
import de.giesen.camaps.companion.prototype.alarm.xdrip.*;
import java.io.IOException;
import java.util.*;

/** Durable decisions and explicit delivery/snooze acknowledgments. Never performs platform output. */
public final class PersistentAlarmSession {
    public static final long RATE_REARM_MS=(Integer.MAX_VALUE/100000)*1000L;
    private final AlarmStateStore store;
    private final List<Rule> rules;
    private final HistoryAnalysis.Limits limits;
    private final OutputMode mode;
    private final HistoryAnalysis.TimeBasis timeBasis;
    private Result verified;
    private Clock verifiedClock;
    private long verifiedRevision,remainingFreshMs;
    public PersistentAlarmSession(AlarmStateStore store,List<Rule> rules,HistoryAnalysis.Limits limits,OutputMode mode) {
        this(store,rules,limits,mode,HistoryAnalysis.TimeBasis.MEASUREMENT);
    }
    public PersistentAlarmSession(AlarmStateStore store,List<Rule> rules,HistoryAnalysis.Limits limits,OutputMode mode,HistoryAnalysis.TimeBasis timeBasis) {
        if (store==null || rules==null || limits==null || mode==null || timeBasis==null) throw new IllegalArgumentException();
        new AlarmEngine(rules,limits,mode,timeBasis); // Validate duplicate ids/configuration.
        this.timeBasis=timeBasis;
        this.store=store; this.rules=Collections.unmodifiableList(new ArrayList<>(rules)); this.limits=limits; this.mode=mode;
    }
    private Rule rule(String id) throws IOException {
        for (Rule rule:rules) if (rule.id.equals(id) && rule.enabled) return rule;
        throw new IOException("Alarmstufe nicht aktiv oder nicht vorhanden");
    }
    private Snapshot restored(Clock now) throws IOException { return AlarmStateStore.restore(store.load(),rules,now); }
    public synchronized Snapshot view(Clock now) throws IOException { return restored(now); }
    public synchronized boolean needsFreshData() { return verified==null; }
    /** Cleanup is repeatable after settings changes; a crash between the two files cannot restore old conditions. */
    public synchronized void reconcile(Clock now) throws IOException {
        Snapshot original=store.load(),current=AlarmStateStore.restore(original,rules,now);
        if (original.revision>0 || !current.entries.isEmpty()) store.save(original.revision,now,current.entries.values());
        verified=null;
    }
    public synchronized Result evaluate(List<HistoryAnalysis.Point> window,Clock now,int minuteOfDay,
                                        Long confirmedSourceSessionStart,DecisionSink sink) throws IOException {
        verified=null;
        Snapshot before=restored(now); Map<String,Entry> rows=new LinkedHashMap<>(before.entries);
        AlarmEngine engine=new AlarmEngine(rules,limits,mode,timeBasis); Map<String,Long> timers=new HashMap<>();
        for (Entry row:rows.values()) if (row.highSince>0) timers.put(row.id,row.highSince);
        engine.restorePersistentTimers(timers);
        Result result=engine.evaluate(window,now.wallMs,minuteOfDay,confirmedSourceSessionStart,null);
        Map<String,Long> updated=engine.persistentTimers();
        for (Rule rule:rules) if (rule.enabled && rule.kind==Kind.PERSISTENT_HIGH) {
            Entry row=rows.get(rule.id); long since=updated.containsKey(rule.id)?updated.get(rule.id):0;
            if (row!=null) rows.put(rule.id,row.withTimer(since)); else if (since>0) rows.put(rule.id,Entry.idle(rule,since));
        }
        if (result.status==Status.EVALUATED) {
            double latest=window.get(window.size()-1).mgdl;
            // GVA episode policy: only a fresh, numerical return across this level's
            // boundary resets its automatic repeat. Schedule changes and data gaps
            // are not recovery; an explicitly snoozed row keeps its deadline.
            for(Decision d:result.decisions) {
                Entry row=rows.get(d.rule.id);
                boolean normalized=latest>14 && (d.rule.kind==Kind.HIGH && latest<d.rule.threshold
                        || d.rule.kind==Kind.LOW && latest>d.rule.threshold);
                if(row!=null && row.active && !row.snoozed && normalized)rows.put(row.id,row.clearActive());
            }
            for(Decision d:result.decisions) if(!d.conditionMet && rows.containsKey(d.rule.id)) rows.put(d.rule.id,rows.get(d.rule.id).accepted(false));
            for (Decision decision:result.decisions) if (decision.resetNotificationState && rows.containsKey(decision.rule.id))
                rows.put(decision.rule.id,rows.get(decision.rule.id).clearActive());
            // Explicit snooze and unexpired repeats without numerical recovery survive.
            if (result.selectedThresholdRule==null) for (Entry row:new ArrayList<>(rows.values()))
                if (row.active && LevelForm.hasSnooze(row.kind) && ready(row,now)) rows.put(row.id,row.clearActive());
        }
        pruneEmpty(rows);
        Snapshot committed=store.save(before.revision,now,rows.values());
        if (result.status==Status.EVALUATED) {
            verified=result; verifiedClock=now; verifiedRevision=committed.revision;
            long latest=window.get(window.size()-1).timestampMs;
            remainingFreshMs=limits.maximumAgeMs-(now.wallMs-latest);
        }
        // A decision is never exposed to a downstream sender before successful durable commit.
        if (sink!=null) sink.onDecision(result);
        return result;
    }
    private static void pruneEmpty(Map<String,Entry> rows) {
        rows.values().removeIf(row -> !row.active && row.highSince==0);
    }
    private boolean isFresh(Clock now) {
        return verified!=null && now.boot==verifiedClock.boot && now.elapsedMs>=verifiedClock.elapsedMs
                && now.elapsedMs-verifiedClock.elapsedMs<=remainingFreshMs;
    }
    private Decision eligible(String id,Clock now) throws IOException {
        if (!isFresh(now)) throw new IOException(AppText.tr("Erst einen neuen gültigen Empfang prüfen"));
        for (Decision decision:verified.decisions) if (decision.rule.id.equals(id) && decision.conditionMet
                && (!LevelForm.hasSnooze(decision.rule.kind) || id.equals(verified.selectedThresholdRule))) return decision;
        throw new IOException(AppText.tr("Keine passende geprüfte Alarmbedingung"));
    }
    private static ActiveBgAlert active(Entry row) {
        ActiveBgAlert active=new ActiveBgAlert(); active.is_snoozed=row.snoozed;
        active.next_alert_at=row.nextAt; active.alert_started_at=row.startedAt; return active;
    }
    private interface Bound<T> { T call(); }
    private static <T> T bound(Clock now,Bound<T> action) {
        synchronized(XDripRuntime.class) {
            XDripRuntime.enter(new XDripRuntime.Environment(now.wallMs,0,null,Collections.emptyList(),new HashMap<>()));
            try { return action.call(); } finally { XDripRuntime.leave(); }
        }
    }
    private static boolean ready(Entry row,Clock now) {
        if (!row.active) return true;
        return LevelForm.hasSnooze(row.kind)?bound(now,() -> active(row).ready_to_alarm()):now.wallMs>=row.nextAt;
    }
    private boolean thresholdSwitchAllowed(Map<String,Entry> rows,Rule candidate,Clock now) throws IOException {
        for (Entry row:rows.values()) if (row.active && LevelForm.hasSnooze(row.kind) && !row.id.equals(candidate.id) && !ready(row,now)) {
            Rule previous=rule(row.id);
            if (previous.kind==candidate.kind) {
                de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType a=new de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType();
                de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType b=new de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType();
                a.above=previous.kind==Kind.HIGH; a.threshold=previous.threshold; b.above=candidate.kind==Kind.HIGH; b.threshold=candidate.threshold;
                if (de.giesen.camaps.companion.prototype.alarm.xdrip.AlertType.HigherAlert(a,b)==a) return false;
            }
        }
        return true;
    }
    /** Rechecks eligibility and durable revision without advancing an output deadline. */
    public synchronized boolean deliveryReady(String id,Clock now) throws IOException {
        Rule candidate=eligible(id,now).rule; Snapshot before=restored(now);
        if (before.revision!=verifiedRevision) { verified=null; throw new AlarmStateStore.Conflict(); }
        Entry row=before.entries.get(id);
        return (row==null || ready(row,now)) && (!LevelForm.hasSnooze(candidate.kind) || thresholdSwitchAllowed(before.entries,candidate,now));
    }
    /** Durable reservation before an output attempt. This is not evidence of physical playback. */
    public synchronized void claimDelivery(String id,Clock now) throws IOException { recordDelivered(id,now); }
    /** Compatibility name for the durable reservation; the output adapter must report actual acceptance separately. */
    public synchronized void recordDelivered(String id,Clock now) throws IOException {
        Rule rule=eligible(id,now).rule; Snapshot before=restored(now);
        if (before.revision!=verifiedRevision) { verified=null; throw new AlarmStateStore.Conflict(); }
        Map<String,Entry> rows=new LinkedHashMap<>(before.entries); Entry row=rows.get(id);
        if (row!=null && !ready(row,now)) throw new IOException(AppText.tr("Wiederholung oder Ereignissperre noch nicht abgelaufen"));
        if (LevelForm.hasSnooze(rule.kind) && !thresholdSwitchAllowed(rows,rule,now)) throw new IOException(AppText.tr("Höher priorisierte Stufe noch pausiert"));
        long interval=LevelForm.isRate(rule.kind)?RATE_REARM_MS:
                (rule.kind==Kind.PERSISTENT_HIGH?Math.min(1440,Math.max(1,rule.outputs.repeatMinutes)):Math.max(1,rule.outputs.repeatMinutes))*60000L;
        long next;
        try { next=Math.addExact(now.wallMs,interval); } catch (ArithmeticException e) { throw new IOException(AppText.tr("Alarmzeit außerhalb des Bereichs"),e); }
        final Entry previous=row;
        ActiveBgAlert core=bound(now,() -> {
            ActiveBgAlert value=previous!=null && previous.active?active(previous):new ActiveBgAlert();
            if (previous==null || !previous.active) { value.is_snoozed=false; value.alert_started_at=now.wallMs; }
            else if (LevelForm.hasSnooze(rule.kind)) value.getAndUpdateAlertingMinutes();
            value.updateNextAlertAt(next); return value;
        });
        if (LevelForm.hasSnooze(rule.kind)) for (Entry other:new ArrayList<>(rows.values()))
            if (!other.id.equals(id) && LevelForm.hasSnooze(other.kind) && other.kind!=rule.kind) rows.put(other.id,other.clearActive());
        rows.put(id,new Entry(id,AlarmStateStore.fingerprint(rule),rule.kind,row==null?0:row.highSince,true,false,
                core.alert_started_at,core.next_alert_at,now.wallMs,row!=null && row.acceptedInEpisode));
        pruneEmpty(rows); Snapshot saved=store.save(before.revision,now,rows.values()); verifiedRevision=saved.revision;
    }
    public synchronized void accepted(String id,Clock now) throws IOException {
        Snapshot before=restored(now); if(before.revision!=verifiedRevision) throw new AlarmStateStore.Conflict();
        Entry row=before.entries.get(id); if(row==null || !row.active) throw new IOException(AppText.tr("Kein reservierter Alarm"));
        Map<String,Entry> rows=new LinkedHashMap<>(before.entries); rows.put(id,row.accepted(true));
        verifiedRevision=store.save(before.revision,now,rows.values()).revision;
    }
    public synchronized void snooze(String id,int minutes,Clock now) throws IOException {
        Rule rule=rule(id);
        if (!LevelForm.hasSnooze(rule.kind) || minutes<1 || minutes>Integer.MAX_VALUE/60000)
            throw new IllegalArgumentException(AppText.tr("Für diese Stufe kein gültiger Snooze"));
        Snapshot before=restored(now); Entry row=before.entries.get(id);
        if (row==null || !row.active) throw new IOException(AppText.tr("Kein aktiver Alarm dieser Stufe"));
        if (now.wallMs>Long.MAX_VALUE-minutes*60000L) throw new IOException(AppText.tr("Snoozezeit außerhalb des Bereichs"));
        ActiveBgAlert core=bound(now,() -> { ActiveBgAlert value=active(row); value.snooze(minutes); return value; });
        Map<String,Entry> rows=new LinkedHashMap<>(before.entries);
        rows.put(id,new Entry(id,row.fingerprint,row.kind,row.highSince,true,true,core.alert_started_at,core.next_alert_at,row.lastDeliveredAt,row.acceptedInEpisode));
        store.save(before.revision,now,rows.values()); verified=null;
    }
}
