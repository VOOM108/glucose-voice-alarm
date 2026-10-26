// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.File;
import java.io.IOException;

/** Real private persistence initialization/reconciliation only. No route enables live output. */
public final class RuntimeStateBridge {
    public enum Status { WAITING, READY, NEEDS_FRESH_DATA, STORAGE_BLOCKED, CLOCK_BLOCKED }
    public static volatile Status status=Status.WAITING;
    private static PersistentAlarmSession cached;
    private static String directory;
    private static long revision=-1;
    public static synchronized void refresh(File files,AlarmStateStore.Clock clock) {
        try {
            LevelStore.Snapshot settings=new LevelStore(files).load();
            if (cached==null || !files.getAbsolutePath().equals(directory) || settings.revision!=revision) {
                PersistentAlarmSession session=new PersistentAlarmSession(new AlarmStateStore(files),settings.rules(),
                        new HistoryAnalysis.Limits(1,Long.MAX_VALUE,0),AlarmEngine.OutputMode.TONES_ONLY);
                session.reconcile(clock); cached=session; directory=files.getAbsolutePath(); revision=settings.revision;
            }
            // This bridge only restores/filters; it never evaluates live measurements with these bounds.
            cached.view(clock); status=Status.READY;
        } catch (AlarmStateStore.ClockBlocked e) { status=Status.CLOCK_BLOCKED; }
        catch (IOException | IllegalArgumentException e) { cached=null; status=Status.STORAGE_BLOCKED; }
    }
    public static void unconfirmedInput() { if (status==Status.READY || status==Status.NEEDS_FRESH_DATA) status=Status.NEEDS_FRESH_DATA; }
    private RuntimeStateBridge() {}
}
