// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReferenceAuditTest {
    private String h(int n) { return String.format(java.util.Locale.ROOT,"%064x",n); }
    @Test public void repeatedUnknownImagesAndFormVariantsAreVisibleWithoutExportingTheirIdentity() {
        String events=ReferenceAudit.append("",ReferenceAudit.Match.UNKNOWN,h(1),h(10));
        events=ReferenceAudit.append(events,ReferenceAudit.Match.UNKNOWN,h(1),h(10));
        events=ReferenceAudit.append(events,ReferenceAudit.Match.UNKNOWN,h(2),h(10));
        String report=ReferenceAudit.report("0.7.0-probe",events,"");
        assertTrue(report.contains("Wiederholt exakt unbekannt: 1")); assertTrue(report.contains("Unbekannte Formvarianten: 1"));
        assertFalse(report.contains(h(1))); assertFalse(report.contains(h(10)));
    }
    @Test public void automaticExactAndShapeHitsAreCountedWithoutManualConfirmations() {
        String events=ReferenceAudit.append("",ReferenceAudit.Match.EXACT,h(1),h(10));
        events=ReferenceAudit.append(events,ReferenceAudit.Match.SHAPE,h(2),h(10));
        String report=ReferenceAudit.report("0.7.0-probe",events,"");
        assertTrue(report.contains("Exakte Treffer: 1")); assertTrue(report.contains("Formtreffer: 1")); assertTrue(report.contains("Manuelle Bestaetigungen: 0/64"));
    }
    @Test public void eventLogIsBoundedAndRejectsArbitraryText() {
        String events=""; for (int i=0;i<200;i++) events=ReferenceAudit.append(events,ReferenceAudit.Match.NO_CANDIDATE,null,null);
        assertTrue(ReferenceAudit.report("0.7.0-probe",events,"").contains("Letzte Grafikpruefungen: 128/128"));
        try { ReferenceAudit.append("",ReferenceAudit.Match.UNKNOWN,"raw text",null); fail(); } catch (IllegalArgumentException expected) { }
    }
}
