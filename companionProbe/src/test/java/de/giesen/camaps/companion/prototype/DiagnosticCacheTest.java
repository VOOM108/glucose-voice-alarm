// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.TrendDiagnostics.Source.*;

public class DiagnosticCacheTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private NotificationParser.Result reading() { return NotificationParser.parse(Arrays.asList("123 mg/dl")); }
    @Test public void persistsOnlySanitizedStructureAndBuildVersion() throws Exception {
        File dir=temporary.newFolder(); TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.text(TITLE,"Max Mustermann 123 mg/dl 20:55 ↗",false);
        DiagnosticCache.write(dir,"0.2.1-probe",diagnostic,reading());
        String text=new String(Files.readAllBytes(new File(dir,DiagnosticCache.NAME).toPath()),StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Prototyp 0.2.1-probe\nTrenddiagnose"));
        assertTrue(text.contains("U+2197"));
        for (String forbidden:new String[]{"Max","Mustermann","123","20:55","mg/dl"}) assertFalse(text.contains(forbidden));
        assertEquals(1,dir.list().length);
    }
    @Test public void replacesOneReportWithoutHistory() throws Exception {
        File dir=temporary.newFolder(); TrendDiagnostics first=new TrendDiagnostics(); first.text(TEXT,"↑",false);
        TrendDiagnostics next=new TrendDiagnostics(); next.text(TEXT,"↓",false);
        DiagnosticCache.write(dir,"0.2.1-probe",first,reading());
        DiagnosticCache.write(dir,"0.2.1-probe",next,reading());
        String text=new String(Files.readAllBytes(new File(dir,DiagnosticCache.NAME).toPath()),StandardCharsets.UTF_8);
        assertFalse(text.contains("U+2191")); assertTrue(text.contains("U+2193"));
        assertEquals(1,dir.list().length);
    }
    @Test public void clearingRemovesReportAndInterruptedTemporaryFile() throws Exception {
        File dir=temporary.newFolder();
        DiagnosticCache.write(dir,"0.2.1-probe",new TrendDiagnostics(),reading());
        Files.write(new File(dir,DiagnosticCache.NAME+".new").toPath(),new byte[]{1});
        DiagnosticCache.clear(dir); assertEquals(0,dir.list().length);
    }
    @Test public void invalidVersionCannotLeaveAStaleReport() throws Exception {
        File dir=temporary.newFolder();
        DiagnosticCache.write(dir,"0.2.1-probe",new TrendDiagnostics(),reading());
        try { DiagnosticCache.write(dir,"raw person or value",new TrendDiagnostics(),reading()); fail(); }
        catch (IOException expected) { assertEquals(0,dir.list().length); }
    }
}
