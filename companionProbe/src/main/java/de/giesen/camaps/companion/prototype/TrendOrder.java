// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.util.*;

/** Presentation order only. Legacy text/profile indices deliberately remain unchanged. */
public final class TrendOrder {
    public static final List<NotificationParser.Trend> ALL=Collections.unmodifiableList(Arrays.asList(
            NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.UP,NotificationParser.Trend.UP_RIGHT,
            NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,
            NotificationParser.Trend.DOUBLE_DOWN));
    public static int storedIndex(NotificationParser.Trend trend) { return TrendDisplayWords.TRENDS.indexOf(trend); }
    private TrendOrder() {}
}
