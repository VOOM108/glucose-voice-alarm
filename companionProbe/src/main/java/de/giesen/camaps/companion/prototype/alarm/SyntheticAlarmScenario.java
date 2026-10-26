// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import de.giesen.camaps.companion.prototype.*;
import java.util.*;

/** A fixed fixture set for isolated demo/cache tests; these are not editable/live defaults. */
public final class SyntheticAlarmScenario {
    public static final long BASE=10000000L,MINUTE=60000L;
    public final AlarmLevel level;
    public final AlarmEngine.Kind kind;
    public SyntheticAlarmScenario(AlarmEngine.Kind kind) {
        this.kind=kind;
        AlarmEngine.Rule rule=new AlarmEngine.Rule("synthetic_"+kind.name(),kind,true,
                LevelForm.isRate(kind)?2:kind==AlarmEngine.Kind.LOW?80:150,kind==AlarmEngine.Kind.PERSISTENT_HIGH?1:0,
                true,0,1440,new AlarmEngine.Outputs(true,true,LevelForm.hasRepeat(kind)?3:0,LevelForm.hasSnooze(kind)?5:0));
        level=new AlarmLevel(rule,AppText.tr("Synthetischer Test: ")+LevelForm.kindLabel(kind),AlarmLevel.DEFAULT_TONE);
    }
    public AlarmStateStore.Clock clock(long offset) { return new AlarmStateStore.Clock(BASE+offset,1000000+offset,1); }
    public List<HistoryAnalysis.Point> points(long offset,boolean condition) {
        List<HistoryAnalysis.Point> result=new ArrayList<>();
        for (int i=0;i<4;i++) {
            int value=condition?kind==AlarmEngine.Kind.LOW?65:160:100;
            if (kind==AlarmEngine.Kind.RISING) value=100+(condition?3*i:0);
            if (kind==AlarmEngine.Kind.FALLING) value=109-(condition?3*i:0);
            if (kind==AlarmEngine.Kind.PERSISTENT_HIGH) value=condition?157+i:100;
            result.add(new HistoryAnalysis.Point(value,BASE+offset-(3-i)*MINUTE));
        }
        return Collections.unmodifiableList(result);
    }
    public NotificationParser.Trend trend(boolean condition) {
        if (!condition) return NotificationParser.Trend.FLAT;
        return kind==AlarmEngine.Kind.FALLING || kind==AlarmEngine.Kind.LOW?NotificationParser.Trend.DOWN:NotificationParser.Trend.UP;
    }
    public static HistoryAnalysis.Limits limits() { return new HistoryAnalysis.Limits(1,2*MINUTE,MINUTE); }
}
