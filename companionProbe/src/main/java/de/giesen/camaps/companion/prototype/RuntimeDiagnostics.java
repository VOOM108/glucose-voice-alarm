// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.AlarmStateSelfTest;
import java.io.File;
import java.io.IOException;

/** Fixed test/availability flags only. Actual alarm ids, pauses and timestamps never leave private storage. */
public final class RuntimeDiagnostics {
    public static volatile int passed=-1;
    private static boolean running;
    public static synchronized void start(File cache,String version) {
        if (running) return;
        running=true; passed=-1;
        Thread worker=new Thread(() -> {
            try { passed=AlarmStateSelfTest.run(cache); } catch (IOException | RuntimeException e) { passed=0; }
            try { DiagnosticCache.writeRuntime(cache,version); } catch (IOException e) { /* No real state is modified. */ }
            finally { synchronized(RuntimeDiagnostics.class) { running=false; } }
        },"alarm-runtime-selftest");
        worker.setDaemon(true); worker.start();
    }
    public static String report(String version) {
        if (version==null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-(?:probe|beta)")) throw new IllegalArgumentException("Invalid version");
        int count=passed; if (count< -1 || count>8) throw new IllegalStateException("Invalid state diagnostic");
        return "Prototyp "+version+"\nAlarmzustands-Speicher: Schema 2\nSynthetische Neustartpruefungen: "
                +(count<0?"ausstehend":count+"/8")+"\nZustandsanbindung: "+RuntimeStateBridge.status.name()
                +"\nLive-Alarme: deaktiviert\nDatenbasis: nur Testdaten\n";
    }
    private RuntimeDiagnostics() {}
}
