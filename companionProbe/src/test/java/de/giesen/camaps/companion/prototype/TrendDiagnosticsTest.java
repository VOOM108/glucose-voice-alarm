// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.TrendDiagnostics.Source.*;

public class TrendDiagnosticsTest {
    private NotificationParser.Result unknown() { return NotificationParser.parse(Arrays.asList("123")); }
    @Test public void summaryOmitsRawTextMeasurementsNamesAndTimes() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.text(TITLE,"Patient Max Mustermann: 123 mg/dl um 20:55 ↘",false);
        diagnostic.text(OTHER_TEXT,"Geheime Freitextnotiz 987",true);
        String report=diagnostic.summary(unknown());
        for (String token:new String[]{"Max","Mustermann","123","20:55","Geheime","987","mg/dl"}) assertFalse(token,report.contains(token));
        assertTrue(report.contains("U+2198"));
        assertTrue(report.contains("TITLE: Text=1"));
    }
    @Test public void graphicStructureDoesNotInventAnArrow() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.remote(CONTENT_VIEW,false);
        diagnostic.image(CONTENT_VIEW,TrendDiagnostics.ImageKind.BITMAP,false);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("Bild=1"));
        assertTrue(report.contains("Grafiktyp=[BITMAP]"));
        assertTrue(report.contains("CamAPS-Trend nicht lesbar"));
        assertFalse(report.contains("gleichbleibend"));
    }
    @Test public void privateFontDetailsRemainOpaque() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.text(CONTENT_VIEW,"\ue012\ue045",false);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("Privatfont=vorhanden"));
        assertFalse(report.contains("E012"));
        assertFalse(report.contains("E045"));
    }
    @Test public void failuresAndLimitsAreVisibleAndBounded() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.remote(BIG_CONTENT_VIEW,true);
        char[] chars=new char[2000]; Arrays.fill(chars,'↑');
        diagnostic.text(TEXT,new String(chars),false);
        assertTrue(diagnostic.isLimited());
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("Ansicht=nicht lesbar"));
        assertTrue(report.contains("Strukturprüfung begrenzt"));
        assertTrue(report.length()<2000);
    }
    @Test public void iconPresenceIsReportedWithoutResourceOrLevelValues() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.icons(true,false);
        diagnostic.image(TEXT,TrendDiagnostics.ImageKind.VECTOR,true);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("klein=vorhanden, groß=fehlt"));
        assertTrue(report.contains("Bildspan=1"));
    }
    @Test public void graphicIdentifiersAreStaticAndGeometryBounded() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.imageDetails(CONTENT_VIEW,"trend_arrow",TrendDiagnostics.ImageKind.VECTOR,24,24,45,0);
        diagnostic.imageDetails(CONTENT_VIEW,"raw text 123 mg/dl\npatient",TrendDiagnostics.ImageKind.BITMAP,99999,-10,Float.NaN,Float.POSITIVE_INFINITY);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("id=trend_arrow; typ=VECTOR; breite=24; hoehe=24; rotation=45.0"));
        assertTrue(report.contains("id=UNKNOWN_ID; typ=BITMAP; breite=-1; hoehe=-1"));
        assertFalse(report.contains("123")); assertFalse(report.contains("patient"));
    }
    @Test public void graphicDetailCountIsBounded() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        for (int i=0;i<30;i++) diagnostic.imageDetails(CONTENT_VIEW,"icon_"+i,TrendDiagnostics.ImageKind.VECTOR,24,24,0,0);
        assertTrue(diagnostic.isLimited());
        assertFalse(diagnostic.summary(unknown()).contains("id=icon_29;"));
    }
    @Test public void onlyFixedDirectionVocabularyIsReportedWithoutInferringMeaning() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.text(CONTENT_VIEW,"Trend arrow: not rapidly rising. Patient Jane 234.",true);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("Richtungstoken=[TREND, ARROW, RISING, RAPIDLY, NOT]"));
        assertFalse(report.contains("Jane")); assertFalse(report.contains("234"));
        assertTrue(report.contains("CamAPS-Trend nicht lesbar"));
    }
    @Test public void referenceDiagnosticHasOnlyPresenceFlags() {
        TrendDiagnostics diagnostic=new TrendDiagnostics();
        diagnostic.reference(true,false);
        assertTrue(diagnostic.summary(unknown()).contains("Grafikreferenz: Kandidat=vorhanden, bestätigt=nein"));
        diagnostic.reference(true,true);
        String report=diagnostic.summary(unknown());
        assertTrue(report.contains("Grafikreferenz: Kandidat=vorhanden, bestätigt=ja"));
        assertFalse(report.contains("123")); assertFalse(report.contains("gleichbleibend"));
        diagnostic.reference(false,true);
        assertTrue(diagnostic.summary(unknown()).contains("Grafikreferenz: Kandidat=fehlt, bestätigt=nein"));
    }
}
