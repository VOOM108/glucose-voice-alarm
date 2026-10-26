// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

/** Finite, non-repeating patterns. Null on a level means no vibration. */
public enum VibrationCatalog {
    SHORT(AppText.tr("Kurz"),180), LONG(AppText.tr("Lang"),450), SHORT_SHORT(AppText.tr("Kurz · kurz"),180,180),
    SHORT_LONG(AppText.tr("Kurz · lang"),180,450), LONG_SHORT(AppText.tr("Lang · kurz"),450,180), LONG_LONG(AppText.tr("Lang · lang"),450,450);
    public final String label;
    private final long[] pulses;
    VibrationCatalog(String label,long... pulses) { this.label=label; this.pulses=pulses; }
    public long[] timings() { return pulses.length==1?new long[]{0,pulses[0]}:new long[]{0,pulses[0],180,pulses[1]}; }
    public long duration() { long total=0; for(long time:timings()) total+=time; return total; }
    public static String validate(String pattern) { if(pattern!=null) valueOf(pattern); return pattern; }
}
