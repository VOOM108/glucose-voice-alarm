// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

/** Exact, explicitly labelled references. No similarity search or inferred direction. */
public final class TrendReference {
    public static String fingerprint(String profile, int[] pixels) {
        if (profile == null || profile.isEmpty() || pixels == null || pixels.length != 64*64)
            throw new IllegalArgumentException("Invalid reference input");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(profile.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte)0);
            for (int pixel : pixels) {
                digest.update((byte)(pixel >>> 24)); digest.update((byte)(pixel >>> 16));
                digest.update((byte)(pixel >>> 8)); digest.update((byte)pixel);
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format(Locale.ROOT,"%02x",b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static NotificationParser.Trend lookup(Map<String,?> references, String key) {
        if (key == null || !key.matches("[a-f0-9]{64}")) return NotificationParser.Trend.UNKNOWN;
        Object stored = references.get(key);
        try { return NotificationParser.Trend.valueOf(stored instanceof String ? ((String)stored).replaceFirst("^(RED_|YELLOW_)", "") : "UNKNOWN"); }
        catch (IllegalArgumentException e) { return NotificationParser.Trend.UNKNOWN; }
    }
    /** Colour is explicit and pixel-exact; a shape match never inherits it. */
    public static String label(NotificationParser.Trend trend,ArrowColor color) {
        if(trend==null || trend==NotificationParser.Trend.UNKNOWN || color==null) throw new IllegalArgumentException("Unknown arrow");
        return (color==ArrowColor.NORMAL?"":color.name()+"_")+trend.name();
    }
    public static ArrowColor color(Map<String,?> references,String key,NotificationParser.Trend resolved) {
        if(key!=null && key.matches("[a-f0-9]{64}") && resolved!=null && resolved!=NotificationParser.Trend.UNKNOWN)
            for(ArrowColor color:ArrowColor.values())if(color.colored()&&label(resolved,color).equals(references.get(key)))return color;
        return ArrowColor.NORMAL;
    }
    public static NotificationParser.Result combine(NotificationParser.Result text, NotificationParser.Trend graphic) {
        if (text.mgdl == null || graphic == NotificationParser.Trend.UNKNOWN) return text;
        if ("Widersprüchliche Trendangaben".equals(text.reason)) return text;
        if (text.trend != NotificationParser.Trend.UNKNOWN && text.trend != graphic)
            return text.withTrend(NotificationParser.Trend.UNKNOWN,"Widersprüchliche Trendangaben");
        if (text.trend != NotificationParser.Trend.UNKNOWN) return text;
        return text.withTrend(graphic,"CamAPS-Trend aus bestätigter Grafikreferenz");
    }
    private TrendReference() {}
}
