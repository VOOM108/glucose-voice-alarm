// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Recoverable multi-file apply. No configuration reader can observe a half-applied core. */
public final class SettingsTransaction {
    public static final String FILE_NAME="profile-apply-v1.bin";
    public static boolean pending(File directory) { return new File(directory,FILE_NAME).exists(); }
    public static void requireComplete(File directory) throws IOException { if(pending(directory)) throw new IOException(AppText.tr("Profil wird noch geladen. Bitte die App erneut öffnen.")); }
    public static void begin(File directory,ConfigurationSnapshot desired) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            requireComplete(directory);
            LevelStore.Snapshot levels=new LevelStore(directory).load(); OutputPreferenceStore.Snapshot output=new OutputPreferenceStore(directory).load();
            if(levels.revision==Long.MAX_VALUE || output.revision==Long.MAX_VALUE) throw new IOException("Einstellungs-Revisionsgrenze erreicht");
            ConfigurationSnapshot next=new ConfigurationSnapshot(new LevelStore.Snapshot(levels.revision+1,desired.levels.levels),new OutputPreferenceStore.Snapshot(output.revision+1,desired.output.preferences),desired.appearance,desired.soundSet,desired.nativeDisplay,desired.publicValue);
            ConfigurationFiles.write(new File(directory,FILE_NAME),next.encode());
        }
    }
    public static ConfigurationSnapshot recoverCore(File directory) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            File pending=new File(directory,FILE_NAME); if(!pending.exists()) return null;
            // Validate the entire target before replacing either live file. A corrupt journal blocks all reads.
            ConfigurationSnapshot desired=ConfigurationSnapshot.decode(ConfigurationFiles.read(pending,ConfigurationSnapshot.MAX_BYTES));
            replaceIfDifferent(new File(directory,LevelStore.FILE_NAME),LevelStore.encode(desired.levels));
            replaceIfDifferent(new File(directory,OutputPreferenceStore.FILE_NAME),OutputPreferenceStore.encode(desired.output));
            return desired; // Android presentation preferences must commit before the journal is removed.
        }
    }
    private static void replaceIfDifferent(File file,byte[] bytes) throws IOException {
        if(file.exists() && file.length()==bytes.length && Arrays.equals(ConfigurationFiles.read(file,bytes.length),bytes)) return;
        ConfigurationFiles.write(file,bytes);
    }
    public static void complete(File directory) throws IOException { synchronized(ConfigurationFiles.LOCK) { Files.deleteIfExists(new File(directory,FILE_NAME).toPath()); } }
    private SettingsTransaction() {}
}
