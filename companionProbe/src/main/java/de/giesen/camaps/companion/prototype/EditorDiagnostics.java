// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.LevelStoreSelfTest;
import java.io.File;
import java.io.IOException;

/** Fixed diagnostic flags only; no saved stage names, limits, tones or personal settings. */
public final class EditorDiagnostics {
    public static volatile int passed=-1;
    private static boolean running;
    public static synchronized void start(File cache,String version) {
        if (running) return;
        running=true; passed=-1;
        Thread worker=new Thread(() -> {
            try { passed=LevelStoreSelfTest.run(cache); }
            catch (IOException | RuntimeException e) { passed=0; }
            try { DiagnosticCache.writeEditor(cache,version); } catch (IOException e) { /* Diagnostics never modify user settings. */ }
            finally { synchronized(EditorDiagnostics.class) { running=false; } }
        },"level-editor-selftest");
        worker.setDaemon(true); worker.start();
    }
    public static String report(String version) {
        if (version==null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-(?:probe|beta)")) throw new IllegalArgumentException("Invalid version");
        int count=passed;
        if (count< -1 || count>6) throw new IllegalStateException("Invalid editor diagnostic");
        return "Prototyp "+version+"\nAlarmstufen-Editor: Schema 4\nSynthetische Speicherpruefungen: "
                +(count<0?"ausstehend":count+"/6")+"\nLive-Alarme: deaktiviert\nDatenbasis: nur Testdaten\n";
    }
    private EditorDiagnostics() {}
}
