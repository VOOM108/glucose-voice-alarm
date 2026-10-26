// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechPolicyTest {
    @Test public void recentReceiptIsSpokenAsAReceiptWithoutInventingAMeasurementTime() {
        assertTrue(SpeechPolicy.canSpeak(true,1000,2000,5000,6000));
        assertTrue(SpeechPolicy.observationText(123,NotificationParser.Trend.UNKNOWN,1).contains("Vor 1 Sekunden empfangener Wert"));
        assertFalse(SpeechPolicy.observationText(123,NotificationParser.Trend.UNKNOWN,1).contains("nicht bestätigt"));
    }
    @Test public void disabledOldAndFutureReceiptsDoNotSpeak() {
        assertFalse(SpeechPolicy.canSpeak(false,1000,2000,5000,6000));
        assertFalse(SpeechPolicy.canSpeak(true,1000,300000,5000,6000));
        assertFalse(SpeechPolicy.canSpeak(true,1000,2000,5000,300000));
        assertFalse(SpeechPolicy.canSpeak(true,3000,2000,5000,6000));
        assertFalse(SpeechPolicy.canSpeak(true,1000,2000,7000,6000));
    }
    @Test public void missingTimestampDoesNotBecomeCurrent() { assertFalse(SpeechPolicy.canSpeak(true,1000,2000,0,6000)); }
}
