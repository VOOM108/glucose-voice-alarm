// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

/** A configured enabled level must remain audible under the selected global mode. Legacy files stay readable. */
public final class OutputRequirements {
    public static AlarmLevel missingTone(java.util.List<AlarmLevel> levels,OutputPreferences preferences) {
        for (AlarmLevel level:levels) if (level.rule.enabled && level.rule.kind!=AlarmEngine.Kind.PERSISTENT_HIGH && !preferences.alarmSpeechAllowed(level)
                && !preferences.alarmToneAllowed(level) && !preferences.alarmVibrationAllowed(level)) return level;
        return null;
    }
    public static void require(java.util.List<AlarmLevel> levels,OutputPreferences preferences) {
        preferences.gap.require(preferences);
        if(!preferences.audibleAlarmRoute()) throw new IllegalArgumentException(AppText.tr("Mindestens ein Alarmweg braucht Lautstärke über 0 % oder Vibration"));
        AlarmLevel missing=missingTone(levels,preferences);
        if (missing!=null) throw new IllegalArgumentException(AppText.tr("Für „")+missing.name+AppText.tr("“ fehlt ein aktiver Alarmweg: Ton, Sprache oder Vibration."));
    }
    private OutputRequirements() {}
}
