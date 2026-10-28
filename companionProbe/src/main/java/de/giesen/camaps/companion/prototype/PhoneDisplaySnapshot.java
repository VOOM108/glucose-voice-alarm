// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import android.os.SystemClock;
import de.giesen.gva.watch.WatchSnapshot;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;

/** Display-only copy of the accepted RAM observation. History never restores a current reading. */
final class PhoneDisplaySnapshot {
    static WatchSnapshot read(Context c)throws java.io.IOException {
        ProbeState.Observation latest=ProbeState.display(c);long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        if(latest==null||elapsed<latest.receivedElapsed||now<latest.receivedWall)return null;
        if(Math.abs((now-latest.receivedWall)-(elapsed-latest.receivedElapsed))>60000)return null;
        List<WatchSnapshot.Point> raw=new ArrayList<>();
        try(ObservationStore store=new ObservationStore(c)) {
            for(DiagnosticSeries.Sample p:store.receipts(now))raw.add(new WatchSnapshot.Point(p.time,p.value));
        }
        Double low=null,high=null;
        for(AlarmLevel level:new LevelStore(c.getFilesDir()).load().levels)if(level.rule.enabled) {
            if(level.rule.kind==AlarmEngine.Kind.LOW)low=low==null?level.rule.threshold:Math.max(low,level.rule.threshold);
            if(level.rule.kind==AlarmEngine.Kind.HIGH)high=high==null?level.rule.threshold:Math.min(high,level.rule.threshold);
        }
        if(low!=null&&high!=null&&low>=high){low=null;high=null;}
        if(latest!=ProbeState.display(c))return null;
        return new WatchSnapshot(now,latest.receivedWall,latest.value,WatchSnapshot.Trend.valueOf(latest.trend.name()),
                UnitPreferences.display(c)==GlucoseUnit.MMOL,ProbeState.displayFresh(c,latest),latest.receiptRate.at(now),
                low,high,WatchSnapshot.minutes(raw,now));
    }
    private PhoneDisplaySnapshot(){}
}
