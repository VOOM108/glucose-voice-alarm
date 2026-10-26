// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.alarm.xdrip.BgReading;
import java.util.*;

/** Receipt integration only: notification bursts are not separate minute samples. */
final class ReceiptRateHistory {
    static List<BgReading> samples(List<BgReading> data) {
        List<BgReading> result=new ArrayList<>();
        for(BgReading point:data) {
            if(!result.isEmpty() && result.get(result.size()-1).timestamp/60000L==point.timestamp/60000L) result.set(result.size()-1,point);
            else result.add(point);
        }
        return result;
    }
    static boolean meets(List<BgReading> data,double threshold,boolean falling) {
        if(data.size()<4) return false;
        BgReading latest=data.get(data.size()-1),previous=data.get(data.size()-2),first=data.get(data.size()-4);
        long span=latest.timestamp-first.timestamp,lastSpan=latest.timestamp-previous.timestamp;
        if(span<=0 || lastSpan<=0) return false;
        double sign=falling?-1:1;
        double overall=sign*(latest.calculated_value-first.calculated_value)*60000.0/span;
        double last=sign*(latest.calculated_value-previous.calculated_value)*60000.0/lastSpan;
        // Imported methods truncate minutes; exact elapsed times must also satisfy the selected rate.
        return overall>=threshold && last>=threshold/2;
    }
    private ReceiptRateHistory() {}
}
