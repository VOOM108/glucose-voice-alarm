// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.NotificationParser.Trend.*;

public class NotificationParserTest {
    private NotificationParser.Result parse(String... fields) { return NotificationParser.parse(Arrays.asList(fields)); }
    @Test public void allSevenArrowsRemainSourceTrends() {
        String[] symbols = {"↑↑","↑","↗","→","↘","↓","↓↓"};
        NotificationParser.Trend[] expected = {DOUBLE_UP, UP, UP_RIGHT, FLAT, DOWN_RIGHT, DOWN, DOUBLE_DOWN};
        for (int i=0;i<symbols.length;i++) {
            NotificationParser.Result result = parse("123 mg/dl " + symbols[i]);
            assertEquals(Double.valueOf(123), result.mgdl);
            assertEquals(expected[i], result.trend);
        }
    }
    @Test public void separatesValueAndAccessibleArrow() { assertEquals(DOWN_RIGHT, parse("123", "↘").trend); }
    @Test public void supportsDoubleArrowCodepoints() { assertEquals(DOUBLE_UP, parse("123 ⇈").trend); assertEquals(DOUBLE_DOWN, parse("123 ⇊").trend); }
    @Test public void stripsVariationSelectorsAndNonbreakingSpace() { assertEquals(UP, parse("123\u00a0mg/dL ⬆\ufe0f").trend); }
    @Test public void missingOrConflictingArrowIsUnknown() {
        assertEquals(UNKNOWN, parse("123").trend);
        assertEquals(UNKNOWN, parse("123 ↑", "↓").trend);
    }
    @Test public void duplicateFieldsAreNotMultipleValues() { assertEquals(Double.valueOf(123),parse("123", "123 mg/dl").mgdl); }
    @Test public void rejectsAmbiguousNumbers() { assertNull(parse("123", "145").mgdl); }
    @Test public void rejectsMmolDosesTimesAndComparisons() {
        for(String input:new String[]{"6.8 mmol/L", "12:34", "2 U", "≥123 mg/dl", "≤40", "LOW", "HIGH", "0", "-123", "1234"}) assertNull(input, parse(input).mgdl);
    }
    @Test public void noMedicalBoundsFromUpstream() { assertEquals(Double.valueOf(33),parse("33 mg/dl").mgdl); assertEquals(Double.valueOf(450),parse("450 mg/dl").mgdl); }
    @Test public void onlyKnownUnitSpecificCamApsPackages() {
        assertTrue(NotificationParser.acceptsPackage("com.camdiab.fx_alert.mgdl"));
        assertTrue(NotificationParser.acceptsPackage("com.camdiab.fx_alert.hx.mgdl"));
        assertTrue(NotificationParser.acceptsPackage("com.camdiab.fx_alert.mmoll"));
        assertFalse(NotificationParser.acceptsPackage("another.app"));
    }
    @Test public void exactDescriptionsDoNotMatchArbitraryText() { assertEquals(DOUBLE_UP,parse("123", "rapidly rising").trend); assertEquals(UNKNOWN,parse("123", "not rapidly rising").trend); }
    @Test public void trendOnlyFieldsCannotChangeWorkingValue() {
        NotificationParser.Result result=NotificationParser.parse(Arrays.asList("123 mg/dl"),Arrays.asList("123 mg/dl","4","↘"));
        assertEquals(Double.valueOf(123),result.mgdl);
        assertEquals(DOWN_RIGHT,result.trend);
    }
    @Test public void trendOnlyFieldsDoNotCreateAMeasurement() {
        assertNull(NotificationParser.parse(Arrays.asList("unlesbar"),Arrays.asList("123","↑")).mgdl);
    }
    @Test public void contradictionsFromAdditionalFieldsRemainUnknown() {
        assertEquals(UNKNOWN,NotificationParser.parse(Arrays.asList("123 ↑"),Arrays.asList("123 ↑","↓")).trend);
    }
    @Test public void deliveryStatusDoesNotDisplaceExpandedGlucoseFields() {
        // Synthetic collapsed status plus expanded value/description, as collected from both views.
        NotificationParser.Result result=parse("Insulin abgegeben", "2 U", "123 mg/dl", "→");
        assertEquals(Double.valueOf(123),result.mgdl); assertEquals(FLAT,result.trend);
    }
    @Test public void statusOnlyEventCannotReuseAnEarlierValue() {
        assertEquals(Double.valueOf(123),parse("123 mg/dl", "→").mgdl);
        NotificationParser.Result status=parse("Insulin abgegeben", "2 U", "12:34");
        assertNull(status.mgdl); assertEquals(UNKNOWN,status.trend);
    }
    @Test public void contradictoryValuesAcrossViewsRemainRejected() {
        assertNull(parse("Insulin abgegeben", "123 mg/dl", "145 mg/dl", "→").mgdl);
    }
}
