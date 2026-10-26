// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
/** Display boundaries from enabled HIGH/LOW levels; no invented clinical targets. */
final class ChartBands {
    final List<Double> lows=new ArrayList<>(),highs=new ArrayList<>();
    static ChartBands from(List<AlarmLevel> levels) {
        ChartBands result=new ChartBands();
        for(AlarmLevel level:levels) if(level.rule.enabled) {
            if(level.rule.kind==AlarmEngine.Kind.LOW) result.lows.add(level.rule.threshold);
            if(level.rule.kind==AlarmEngine.Kind.HIGH) result.highs.add(level.rule.threshold);
        }
        Collections.sort(result.lows); Collections.sort(result.highs); return result;
    }
}
