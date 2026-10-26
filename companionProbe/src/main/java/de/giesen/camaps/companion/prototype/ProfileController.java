// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** On-device settings snapshots, always loaded with live output disarmed. */
final class ProfileController {
    static ConfigurationSnapshot capture(Context c) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            SettingsTransaction.requireComplete(c.getFilesDir());
            return new ConfigurationSnapshot(new LevelStore(c.getFilesDir()).load(),new OutputPreferenceStore(c.getFilesDir()).load(),AppAppearance.mode(c).name(),SoundDefaults.preferredSet(c),LivePreferences.nativeDisplay(c),LivePreferences.publicValue(c));
        }
    }
    static void apply(Context c,ConfigurationSnapshot desired) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            // Revalidate all fields and outputs before disarming or touching the live configuration.
            desired=ConfigurationSnapshot.decode(desired.encode());
            if(desired.output.preferences.vibrationEnabled && !new PreviewVibration(c).available()) throw new IOException("Dieses Gerät bietet keine Vibration. Profil unverändert erhalten.");
            SettingsTransaction.requireComplete(c.getFilesDir());
            disarm(c); SettingsTransaction.begin(c.getFilesDir(),desired); recover(c);
        }
    }
    private static void disarm(Context c) throws IOException {
        if(!c.getSharedPreferences("live_controls_v1",0).edit().putBoolean("alarms",false).putInt("consent_version",-1).commit()) throw new IOException("Live-Ausgabe konnte nicht sicher ausgeschaltet werden");
        PreviewAudio.configurationChanged(); LiveAlarmService.configurationReplaced();
    }
    static void recover(Context c) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            if(!SettingsTransaction.pending(c.getFilesDir())) return;
            disarm(c);
            ConfigurationSnapshot desired=SettingsTransaction.recoverCore(c.getFilesDir());
            if(!c.getSharedPreferences(AppAppearance.PREFS,0).edit().putString("mode",desired.appearance).commit()
                    || !c.getSharedPreferences(SoundDefaults.PREFS,0).edit().putString("set",desired.soundSet).putBoolean("installed",true).commit()
                    || !c.getSharedPreferences("live_controls_v1",0).edit().putBoolean("native_display",desired.nativeDisplay).putBoolean("lockscreen_value",desired.publicValue).commit())
                throw new IOException("Profil noch nicht vollständig geladen. Bitte App erneut öffnen.");
            SettingsTransaction.complete(c.getFilesDir());
            LiveAlarmService.settingsChanged(); LiveAlarmService.displayChanged();
        }
    }
    private ProfileController() {}
}
