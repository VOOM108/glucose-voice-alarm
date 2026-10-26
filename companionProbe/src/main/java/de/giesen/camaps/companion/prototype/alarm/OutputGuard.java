// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;

import de.giesen.camaps.companion.prototype.NotificationParser;

/** Explicit seam for later agreed clinical policies; no cutoff or recovery rule is inferred. */
public interface OutputGuard {
    enum Reason { NONE, GLOBAL_PAUSE, OWN_LOWER_CUTOFF, RECOVERING_TREND, POLICY_NOT_CONFIGURED }
    final class Input {
        public final AlarmEngine.Rule rule;
        public final double mgdl;
        public final NotificationParser.Trend sourceTrend;
        public final boolean recovering,periodicValue;
        Input(AlarmEngine.Rule rule,double mgdl,NotificationParser.Trend trend,boolean recovering,boolean periodicValue) {
            this.rule=rule; this.mgdl=mgdl; sourceTrend=trend; this.recovering=recovering; this.periodicValue=periodicValue;
        }
    }
    Reason check(Input input);
    static OutputGuard disabled() { return input -> Reason.NONE; }
}
