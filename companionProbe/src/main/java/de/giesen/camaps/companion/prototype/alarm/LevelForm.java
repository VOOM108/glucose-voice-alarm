// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import java.math.BigDecimal;

/** Explicit field validation; no implicit medical thresholds or time defaults. */
public final class LevelForm {
    public static String kindLabel(AlarmEngine.Kind kind) {
        switch(kind) {
            case HIGH: return AppText.tr("Hoch");
            case LOW: return AppText.tr("Niedrig");
            case SOURCE_LOW: return AppText.tr("Bald niedrig");
            case PERSISTENT_HIGH: return AppText.tr("Dauerhaft hoch");
            case RISING: return AppText.tr("Kritische Anstiegsrate");
            case FALLING: return AppText.tr("Kritische Fallrate");
            default: throw new IllegalArgumentException(AppText.tr("Unbekannte Alarmart"));
        }
    }
    public static boolean isRate(AlarmEngine.Kind kind) { return kind==AlarmEngine.Kind.RISING || kind==AlarmEngine.Kind.FALLING; }
    public static boolean hasRepeat(AlarmEngine.Kind kind) { return !isRate(kind) && kind!=AlarmEngine.Kind.SOURCE_LOW; }
    public static boolean hasSnooze(AlarmEngine.Kind kind) { return kind==AlarmEngine.Kind.HIGH || kind==AlarmEngine.Kind.LOW; }
    public static String number(double input) { return BigDecimal.valueOf(input).stripTrailingZeros().toPlainString().replace('.',','); }
    private static double threshold(String raw,boolean rate) {
        String text=raw==null?"":raw.trim();
        if (!text.matches(rate?"[0-9]+(?:[.,][0-9]+)?":"[0-9]+"))
            throw new IllegalArgumentException(rate?AppText.tr("Bitte eine positive Änderungsrate eingeben (Komma oder Punkt möglich)"):AppText.tr("Bitte den Grenzwert als ganze Zahl in mg/dl eingeben"));
        double value;
        try { value=new BigDecimal(text.replace(',','.')).doubleValue(); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(AppText.tr("Ungültiger Grenzwert")); }
        if (!Double.isFinite(value) || value<=0 || (!rate && value>Integer.MAX_VALUE)
                || (rate && (!Float.isFinite((float)value) || (float)value<=0)))
            throw new IllegalArgumentException(AppText.tr("Grenzwert ist nicht als positive Zahl speicherbar"));
        return value;
    }
    private static int minutes(String raw,String label,boolean zeroAllowed) {
        if (raw==null || !raw.trim().matches("[0-9]+")) throw new IllegalArgumentException(label+AppText.tr(": bitte ganze Minuten eingeben"));
        try {
            int value=Integer.parseInt(raw.trim());
            // Prevent upstream int minute-to-ms arithmetic from overflowing; this is a technical bound.
            if (value<(zeroAllowed?0:1) || value>Integer.MAX_VALUE/60000) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(label+AppText.tr(": Wert außerhalb des speicherbaren Minutenbereichs")); }
    }
    public static AlarmLevel parse(String id,AlarmEngine.Kind kind,String name,String threshold,
                                   String duration,String repeat,String snooze,boolean enabled,boolean tone,boolean speech,
                                   String toneUri,boolean allDay,int startMinute,int endMinute) {
        return canonicalLevel(id,kind,name,kind==AlarmEngine.Kind.SOURCE_LOW?1:threshold(threshold,isRate(kind)),duration,repeat,snooze,enabled,tone,speech,toneUri,allDay,startMinute,endMinute);
    }
    public static AlarmLevel parseWithUnit(String id,AlarmEngine.Kind kind,String name,String threshold,
                                   String duration,String repeat,String snooze,boolean enabled,boolean tone,boolean speech,
                                   String toneUri,boolean allDay,int startMinute,int endMinute,de.giesen.camaps.companion.prototype.GlucoseUnit unit,Double unchanged) {
        double value=kind==AlarmEngine.Kind.SOURCE_LOW?1:unchanged==null?unit.parse(threshold):unchanged;
        if(!Double.isFinite(value) || value<=0 || value>Integer.MAX_VALUE)throw new IllegalArgumentException(AppText.tr("Grenzwert ist nicht als positive Zahl speicherbar"));
        return canonicalLevel(id,kind,name,value,duration,repeat,snooze,enabled,tone,speech,toneUri,allDay,startMinute,endMinute);
    }
    private static AlarmLevel canonicalLevel(String id,AlarmEngine.Kind kind,String name,double value,
                                   String duration,String repeat,String snooze,boolean enabled,boolean tone,boolean speech,
                                   String toneUri,boolean allDay,int startMinute,int endMinute) {
        if(!allDay && (startMinute<0 || endMinute<0 || startMinute==endMinute)) throw new IllegalArgumentException(AppText.tr("Bitte Beginn und Ende wählen. Für den ganzen Tag „Ganztägig“ einschalten."));
        int span=kind==AlarmEngine.Kind.PERSISTENT_HIGH?minutes(duration,"Dauer",true):0;
        int repeatMinutes=hasRepeat(kind)?minutes(repeat,AppText.tr("Wiederholung"),false):0;
        int snoozeMinutes=hasSnooze(kind)?minutes(snooze,"Snooze",false):0;
        return new AlarmLevel(new AlarmEngine.Rule(id,kind,enabled,value,span,allDay,startMinute,endMinute,
                new AlarmEngine.Outputs(tone,speech,repeatMinutes,snoozeMinutes)),name,toneUri);
    }
    public static String timingSummary(AlarmEngine.Rule rule) {
        if (rule.kind==AlarmEngine.Kind.SOURCE_LOW) return AppText.tr("Einmal pro roter Pfeilphase · keine Wiederholung oder Snoozezeit");
        if (isRate(rule.kind)) return AppText.tr("Ereignisregel nach xDrip · keine einstellbare Wiederholungs-/Snoozezeit");
        return "Wiederholung: "+rule.outputs.repeatMinutes+" Min."
                +(hasSnooze(rule.kind)?" · Snooze: "+rule.outputs.snoozeMinutes+" Min.":"");
    }
    private LevelForm() {}
}
