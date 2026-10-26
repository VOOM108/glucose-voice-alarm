// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock;
import java.util.*;

/** Session-local context. Boot/reload needs a real new receipt; no extrapolation and no history catch-up. */
public final class GapMonitor {
    public enum Direction { NORMAL, RISING, FALLING, UNKNOWN }
    private Clock last;
    private Direction direction=Direction.UNKNOWN;
    private Kind threshold;
    private long next=-1;
    private boolean claimed,observedDuringGap;
    public void clear() { last=null; next=-1; claimed=false; threshold=null; direction=Direction.UNKNOWN; observedDuringGap=false; }
    public void receive(List<HistoryAnalysis.Point> points,Clock now,NotificationParser.Trend arrow,Result result) {
        if(points==null || points.isEmpty() || points.get(points.size()-1).timestampMs!=now.wallMs) return;
        last=now; next=-1; claimed=false; observedDuringGap=false; threshold=null;
        Direction numeric=Direction.NORMAL;
        if(result!=null && result.status==Status.EVALUATED) for(Decision decision:result.decisions) if(decision.conditionMet) {
            if(decision.rule.id.equals(result.selectedThresholdRule)) threshold=decision.rule.kind;
            if(decision.rule.kind==Kind.FALLING) numeric=Direction.FALLING;
            if(decision.rule.kind==Kind.RISING) numeric=Direction.RISING;
        }
        Direction source=arrow==NotificationParser.Trend.UP || arrow==NotificationParser.Trend.DOUBLE_UP?Direction.RISING:
                arrow==NotificationParser.Trend.DOWN || arrow==NotificationParser.Trend.DOUBLE_DOWN?Direction.FALLING:Direction.NORMAL;
        direction=numeric!=Direction.NORMAL?numeric:source;
        // A contradictory arrow must not create a confident directional warning.
        if(numeric!=Direction.NORMAL && source!=Direction.NORMAL && numeric!=source) direction=Direction.UNKNOWN;
        if(arrow==NotificationParser.Trend.UNKNOWN && numeric==Direction.NORMAL) direction=Direction.UNKNOWN;
    }
    public boolean waiting() { return observedDuringGap; }
    public boolean due(GapSettings settings,Clock now) {
        if(!settings.enabled || last==null) return false;
        if(now.boot!=last.boot || now.elapsedMs<last.elapsedMs) { clear(); return false; }
        long elapsed=now.elapsedMs-last.elapsedMs;
        if(Math.abs((now.wallMs-last.wallMs)-elapsed)>1000) { clear(); return false; }
        if(elapsed<settings.delayMinutes*60000L) return false;
        observedDuringGap=true;
        if(settings.onlyAfterFast && direction!=Direction.RISING && direction!=Direction.FALLING) return false;
        return !claimed || settings.repeatMinutes>0 && now.elapsedMs>=next;
    }
    public void reserve(GapSettings settings,Clock now) { claimed=true; next=now.elapsedMs+settings.repeatMinutes*60000L; }
    public String message(Clock now) {
        return message(now,"de");
    }
    public String message(Clock now,String language) {
        return message(now,language,"");
    }
    public String message(Clock now,String language,String template) {
        if(last==null) throw new IllegalStateException("Keine Quellbeobachtung");
        long minutes=Math.max(1,(now.elapsedMs-last.elapsedMs)/60000L);
        boolean en="en".equals(language);
        String trend=direction==Direction.FALLING?(en?"Last trend: falling fast.":"Zuletzt schnell fallend."):direction==Direction.RISING?(en?"Last trend: rising fast.":"Zuletzt schnell steigend."):"";
        String state=threshold==Kind.LOW?(en?"The last low condition has not been confirmed to have ended.":"Die letzte Niedrigbedingung ist noch nicht als beendet bestätigt."):threshold==Kind.HIGH?(en?"The last high condition has not been confirmed to have ended.":"Die letzte Hochbedingung ist noch nicht als beendet bestätigt."):"";
        return GapSpeechTemplate.render(template,language,minutes,trend,state);
    }
}
