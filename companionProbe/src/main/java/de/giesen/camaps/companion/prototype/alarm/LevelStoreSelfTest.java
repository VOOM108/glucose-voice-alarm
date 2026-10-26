// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Device filesystem check in an isolated temporary directory; never opens actual user settings. */
public final class LevelStoreSelfTest {
    public static int run(File cache) throws IOException {
        Path folder=Files.createTempDirectory(cache.toPath(),"level-editor-selftest-");
        int passed=0;
        try {
            LevelStore store=new LevelStore(folder.toFile());
            LevelStore.Snapshot first=store.load(); if (first.revision==0 && first.levels.isEmpty()) passed++;
            AlarmLevel level=LevelForm.parse("synthetic",AlarmEngine.Kind.HIGH,
                    AppText.tr("Synthetische Teststufe"),"123","","3","7",false,false,false,AlarmLevel.DEFAULT_TONE,true,0,1440);
            LevelStore.Snapshot created=store.save(first.revision,Arrays.asList(level));
            LevelStore.Snapshot reopened=new LevelStore(folder.toFile()).load();
            if (reopened.levels.size()==1 && reopened.find("synthetic").rule.threshold==123 && !reopened.find("synthetic").rule.enabled) passed++;
            LevelStore.Snapshot updated=store.save(reopened.revision,Arrays.asList(level.withEnabled(true)));
            if (new LevelStore(folder.toFile()).load().find("synthetic").rule.enabled) passed++;
            byte[] before=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME));
            try { store.save(created.revision,Collections.emptyList()); }
            catch (LevelStore.Conflict expected) { if (Arrays.equals(before,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)))) passed++; }
            store.save(updated.revision,Collections.emptyList());
            if (new LevelStore(folder.toFile()).load().levels.isEmpty()) passed++;
            byte[] corrupt=Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)); corrupt[0]^=1;
            Files.write(folder.resolve(LevelStore.FILE_NAME),corrupt);
            try { store.save(updated.revision+1,Arrays.asList(level)); }
            catch (IOException expected) { if (Arrays.equals(corrupt,Files.readAllBytes(folder.resolve(LevelStore.FILE_NAME)))) passed++; }
            return passed;
        } finally {
            Files.deleteIfExists(folder.resolve(LevelStore.FILE_NAME+".new"));
            Files.deleteIfExists(folder.resolve(LevelStore.FILE_NAME)); Files.delete(folder);
        }
    }
    private LevelStoreSelfTest() {}
}
