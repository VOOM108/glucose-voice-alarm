// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SyntheticSpeechTest {
    @Test public void everyKindHasAValidClearlyMarkedPreviewWithoutAnEnabledRule() {
        for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) {
            String text=SyntheticSpeech.alarm(kind,"Teststufe","",0);
            assertTrue(kind.name(),text.startsWith("Dies ist eine Testansage mit synthetischen Daten."));
            assertFalse(text.contains("{"));
            if(LevelForm.isRate(kind)) assertTrue(text.contains(kind==AlarmEngine.Kind.FALLING?"Kritische Fallrate bei 65.":"Kritische Anstiegsrate bei 230."));
            else if(kind==AlarmEngine.Kind.SOURCE_LOW) assertTrue(text.endsWith("Laut Datenquelle bald niedrig"));
            else assertTrue(text.contains("Milligramm pro Deziliter"));
        }
    }
    @Test public void previewVariantsCycleWithoutOverflowAndRetainLiteralNames() {
        String lines="Erste {stufe}\nZweite {grenze}\nDritte {wert}";
        assertTrue(SyntheticSpeech.alarm(AlarmEngine.Kind.RISING,"A $ \\ B",lines,0).endsWith("Erste A $ \\ B"));
        assertTrue(SyntheticSpeech.alarm(AlarmEngine.Kind.RISING,"Test",lines,4).endsWith("Zweite 2"));
        assertTrue(SyntheticSpeech.alarm(AlarmEngine.Kind.RISING,"Test",lines,Long.MAX_VALUE).endsWith("Zweite 2"));
    }
    @Test public void persistentPreviewSpeaksTimeAsDuration() {
        assertTrue(SyntheticSpeech.alarm(AlarmEngine.Kind.PERSISTENT_HIGH,"Dauerhoch","",0).contains("seit 11 Minuten"));
    }
    @Test public void invalidCustomTextNeverFallsBackToAnUnrequestedText() {
        try { SyntheticSpeech.alarm(AlarmEngine.Kind.HIGH,"Test","{dauer_minuten}",0); fail(); }
        catch (IllegalArgumentException expected) { }
        try { SyntheticSpeech.value("{stufe}"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void valuePreviewUsesOnlyFixedSyntheticDataAndIsExplicitlyLabeled() {
        String text=SyntheticSpeech.value("Wert {wert}, Alter {alter_sekunden}, Trend {trend}");
        assertTrue(text.startsWith("Dies ist eine Testansage mit synthetischen Daten."));
        assertTrue(text.endsWith("Wert 123, Alter 10, Trend stabil"));
    }
}
