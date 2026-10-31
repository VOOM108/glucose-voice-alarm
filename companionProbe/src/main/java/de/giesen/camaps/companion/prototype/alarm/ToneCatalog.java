// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.util.*;

/** Stable, name-based resource URIs survive APK updates; generated WAVs are disposable build outputs. */
public final class ToneCatalog {
    public static final String DEFAULT_SET="samt";
    public static final int MAX_BOOST_DB=6;
    /** Matches generated peak 0.96; zero boost restores the original peak 0.48. */
    public static float playbackGain(int volumePercent,int boostDb) {
        if (volumePercent<0 || volumePercent>100 || boostDb<0 || boostDb>MAX_BOOST_DB) throw new IllegalArgumentException(AppText.tr("Ungültiger Glockentonpegel"));
        return (float)(volumePercent/100.0*.5*Math.pow(10,boostDb/20.0));
    }
    public static final List<String> SETS=Collections.unmodifiableList(Arrays.asList("samt","klar","rund"));
    private static final String PREFIX="android.resource://de.giesen.camaps.companion.prototype/raw/xcam_";
    public static String setName(String set) {
        switch(set) { case "samt": return AppText.tr("Samt"); case "klar": return AppText.tr("Klar"); case "rund": return AppText.tr("Rund"); default: throw new IllegalArgumentException(AppText.tr("Unbekanntes Klangset")); }
    }
    public static String uri(String set,AlarmEngine.Kind kind) {
        setName(set); if(kind==AlarmEngine.Kind.SOURCE_LOW)kind=AlarmEngine.Kind.LOW; return PREFIX+set+"_"+kind.name().toLowerCase(Locale.ROOT);
    }
    public static String setOf(String uri) {
        for (String set:SETS) for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) if (uri(set,kind).equals(uri)) return set;
        return null;
    }
    public static String title(String uri) {
        String set=setOf(uri); if (set==null) return null;
        for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) if (uri(set,kind).equals(uri)) return setName(set)+" · "+LevelForm.kindLabel(kind);
        return null;
    }
    public static int durationMs(String uri) {
        String set=setOf(uri); if (set==null) return 0;
        for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) if (uri(set,kind).equals(uri))
            return kind==AlarmEngine.Kind.PERSISTENT_HIGH?760:LevelForm.isRate(kind)?500:360;
        return 0;
    }
    private ToneCatalog() {}
}
