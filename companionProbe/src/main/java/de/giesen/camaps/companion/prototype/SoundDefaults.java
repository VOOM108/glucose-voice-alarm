// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;

/** User-requested defaults: replace the former system default once, preserve custom tones and all rule/output flags. */
final class SoundDefaults {
    static final String PREFS="sound_defaults_v1";
    static String preferredSet(Context context) {
        String value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("set",ToneCatalog.DEFAULT_SET);
        return ToneCatalog.SETS.contains(value)?value:ToneCatalog.DEFAULT_SET;
    }
    static String uri(Context context,AlarmEngine.Kind kind) { return ToneCatalog.uri(preferredSet(context),kind); }
    static synchronized void install(Context context) throws IOException {
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if (prefs.getBoolean("installed",false)) return;
        replace(context,preferredSet(context),true);
        if (!prefs.edit().putBoolean("installed",true).commit()) throw new IOException("Tonstandard konnte nicht gespeichert werden");
    }
    static synchronized void useSet(Context context,String set) throws IOException {
        ToneCatalog.setName(set); install(context); replace(context,set,false);
        if (!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("set",set).commit()) throw new IOException("Klangset konnte nicht gespeichert werden");
        PreviewAudio.configurationChanged(); LiveAlarmService.settingsChanged();
    }
    private static void replace(Context context,String set,boolean legacyOnly) throws IOException {
        LevelStore store=new LevelStore(context.getFilesDir()); LevelStore.Snapshot saved=store.load();
        List<AlarmLevel> updated=new ArrayList<>(); boolean changed=false;
        for (AlarmLevel level:saved.levels) {
            boolean replace=legacyOnly?AlarmLevel.DEFAULT_TONE.equals(level.toneUri):ToneCatalog.setOf(level.toneUri)!=null;
            String uri=replace?ToneCatalog.uri(set,level.rule.kind):level.toneUri;
            updated.add(new AlarmLevel(level.rule,level.name,uri,level.speechVariants,level.vibrationPattern,level.suppressRecoveryRepeats)); changed|=!uri.equals(level.toneUri);
        }
        if (changed) { store.save(saved.revision,updated); RuntimeStateBridge.refresh(context.getFilesDir(),RuntimeClock.read(context)); }
    }
    private SoundDefaults() {}
}
