// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import de.giesen.gva.watch.ArrowColor;

/** Presentation only. A missing direction is never converted into a spoken phrase or alarm input. */
final class TrendNotice {
    static String title(Context context,NotificationParser.Trend trend,ArrowColor color,long receivedWall){
        if(trend!=NotificationParser.Trend.UNKNOWN)return DisplayTrendPreferences.title(context,trend,color);
        ProbeState.Observation shown=ProbeState.display(context);
        boolean assign=shown!=null && shown.trend==NotificationParser.Trend.UNKNOWN && shown.receivedWall==receivedWall && shown.assignmentNeeded;
        return AppText.tr(assign?"Pfeil zuordnen":"Trend offen");
    }
    static String dashboard(Context context,ProbeState.Observation shown){
        if(shown==null)return AppText.tr("offen");
        return shown.trend==NotificationParser.Trend.UNKNOWN?AppText.tr(shown.assignmentNeeded?"Pfeil zuordnen":"offen"):
                DisplayTrendPreferences.title(context,shown.trend,shown.arrowColor);
    }
    private TrendNotice(){}
}
