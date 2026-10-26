// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

/** User settings, kept separate from measurement history and active alarm state. */
public final class AlarmLevel {
    public static final String DEFAULT_TONE="content://settings/system/alarm_alert";
    public final AlarmEngine.Rule rule;
    public final String name,toneUri,vibrationPattern;
    public final java.util.List<String> speechVariants;
    public final boolean suppressRecoveryRepeats;
    public AlarmLevel(AlarmEngine.Rule rule,String name,String toneUri) {
        this(rule,name,toneUri,java.util.Collections.emptyList());
    }
    public AlarmLevel(AlarmEngine.Rule rule,String name,String toneUri,java.util.List<String> variants) {
        this(rule,name,toneUri,variants,null);
    }
    public AlarmLevel(AlarmEngine.Rule rule,String name,String toneUri,java.util.List<String> variants,String vibrationPattern) {
        this(rule,name,toneUri,variants,vibrationPattern,false);
    }
    public AlarmLevel(AlarmEngine.Rule rule,String name,String toneUri,java.util.List<String> variants,String vibrationPattern,boolean suppressRecoveryRepeats) {
        this.suppressRecoveryRepeats=suppressRecoveryRepeats;
        if(suppressRecoveryRepeats && !LevelForm.hasSnooze(rule.kind)) throw new IllegalArgumentException(AppText.tr("Erholungsregel nur für Hoch-/Niedrigstufen"));
        this.vibrationPattern=VibrationCatalog.validate(vibrationPattern);
        if (rule==null || name==null || name.trim().isEmpty() || name.length()>80
                || name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(AppText.tr("Ungültiger Stufenname"));
        if (toneUri==null || toneUri.length()>2048 || toneUri.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(AppText.tr("Ungültiger Ton"));
        java.net.URI uri;
        try { uri=java.net.URI.create(toneUri); } catch (IllegalArgumentException e) { throw new IllegalArgumentException(AppText.tr("Ungültiger Ton")); }
        if (!("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())))
            throw new IllegalArgumentException(AppText.tr("Bitte einen lokalen Android-Ton auswählen"));
        if (rule.outputs.repeatMinutes<(LevelForm.hasRepeat(rule.kind)?1:0)
                || rule.outputs.snoozeMinutes<(LevelForm.hasSnooze(rule.kind)?1:0)
                || rule.outputs.repeatMinutes>Integer.MAX_VALUE/60000 || rule.outputs.snoozeMinutes>Integer.MAX_VALUE/60000)
            throw new IllegalArgumentException(AppText.tr("Ungültige Wiederholungs- oder Snoozezeit"));
        if ((!LevelForm.isRate(rule.kind) && (rule.threshold!=Math.rint(rule.threshold) || rule.threshold>Integer.MAX_VALUE))
                || rule.durationMinutes>Integer.MAX_VALUE/60000 || (rule.kind!=AlarmEngine.Kind.PERSISTENT_HIGH && rule.durationMinutes!=0))
            throw new IllegalArgumentException(AppText.tr("Ungültige Grenz- oder Dauerangabe"));
        if (variants==null || variants.size()>4) throw new IllegalArgumentException(AppText.tr("Ungültige Sprachvarianten"));
        for (String variant:variants) SpeechTemplate.validate(variant,rule.kind,false);
        this.rule=rule; this.name=name.trim(); this.toneUri=toneUri;
        speechVariants=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(variants));
    }
    public AlarmLevel withEnabled(boolean enabled) {
        AlarmEngine.Rule r=rule;
        return new AlarmLevel(new AlarmEngine.Rule(r.id,r.kind,enabled,r.threshold,r.durationMinutes,r.allDay,
                r.startMinute,r.endMinute,r.outputs),name,toneUri,speechVariants,vibrationPattern,suppressRecoveryRepeats);
    }
    public String speechText(int sequence) {
        return speechVariants.isEmpty()?SpeechTemplate.defaultAlarm(rule.kind):speechVariants.get(Math.floorMod(sequence,speechVariants.size()));
    }
    public String speechText(int sequence,String language) { return speechVariants.isEmpty()?SpeechTemplate.defaultAlarm(rule.kind,language):speechVariants.get(Math.floorMod(sequence,speechVariants.size())); }
    /** Legacy irrelevant values remain readable on disk but cannot reach future output planning. */
    public AlarmEngine.Rule effectiveRule() {
        AlarmEngine.Rule r=rule;
        return new AlarmEngine.Rule(r.id,r.kind,r.enabled,r.threshold,r.durationMinutes,r.allDay,r.startMinute,r.endMinute,
                new AlarmEngine.Outputs(r.outputs.tone,r.outputs.speech,LevelForm.hasRepeat(r.kind)?r.outputs.repeatMinutes:0,
                        LevelForm.hasSnooze(r.kind)?r.outputs.snoozeMinutes:0));
    }
}
