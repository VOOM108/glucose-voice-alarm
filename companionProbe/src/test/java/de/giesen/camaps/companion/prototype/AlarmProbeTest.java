// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.io.File;

public class AlarmProbeTest {
    @Test public void allFiveImportedAlarmKindsPassSyntheticRuntimeCheck() {
        AlarmProbe.Ingress before=AlarmProbe.ingress;
        assertEquals(5,AlarmProbe.runSyntheticSelfTest());
        assertEquals(before,AlarmProbe.ingress);
    }
    @Test public void liveIngressRemainsInactiveWithUnconfirmedMeasurementTime() {
        AlarmProbe.receive(123,10000000,720);
        assertEquals(AlarmProbe.Ingress.INACTIVE,AlarmProbe.ingress);
        AlarmProbe.receive(null,10000000,720);
        assertEquals(AlarmProbe.Ingress.NO_VALUE,AlarmProbe.ingress);
        AlarmProbe.clear(AlarmProbe.Ingress.OFF);
        assertEquals(AlarmProbe.Ingress.OFF,AlarmProbe.ingress);
    }
    @Test public void coreReportHasNoGlucoseOrMeasurementTime() throws Exception {
        AlarmProbe.selfTestPassed=5;
        AlarmProbe.receive(123,10000000,720);
        File folder=Files.createTempDirectory("core-report-synthetic").toFile();
        try {
            DiagnosticCache.writeCore(folder,"0.4.0-probe");
            String report=new String(Files.readAllBytes(new File(folder,DiagnosticCache.CORE_NAME).toPath()),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(report.contains("Synthetische Alarmarten: 5/5"));
            assertTrue(report.contains("Plattformausgabe: deaktiviert"));
            assertFalse(report.contains("123")); assertFalse(report.contains("10000000"));
            assertFalse(new File(folder,DiagnosticCache.CORE_NAME+".new").exists());
        } finally { Files.deleteIfExists(new File(folder,DiagnosticCache.CORE_NAME).toPath()); Files.delete(folder.toPath()); }
    }
}
