// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToneCatalogTest {
    @Test public void allFifteenCuesHaveStableDistinctUrisAndCorrectKindLabels() {
        Set<String> uris=new HashSet<>();
        for (String set:ToneCatalog.SETS) for (AlarmEngine.Kind kind:AlarmEngine.Kind.values()) {
            String uri=ToneCatalog.uri(set,kind); assertTrue(uris.add(uri)); assertEquals(set,ToneCatalog.setOf(uri));
            assertTrue(ToneCatalog.title(uri).contains(LevelForm.kindLabel(kind)));
            assertTrue(ToneCatalog.durationMs(uri)>0); assertTrue(ToneCatalog.durationMs(uri)<1000);
        }
        assertEquals(15,uris.size()); assertNull(ToneCatalog.setOf(AlarmLevel.DEFAULT_TONE));
        assertNull(ToneCatalog.setOf("android.resource://foreign.app/raw/xcam_samt_high"));
    }
    @Test public void longHighIsDistinctlyLongerAndCatalogDoesNotInventAnExternalToneLength() {
        assertTrue(ToneCatalog.durationMs(ToneCatalog.uri("samt",AlarmEngine.Kind.PERSISTENT_HIGH))>2*ToneCatalog.durationMs(ToneCatalog.uri("samt",AlarmEngine.Kind.HIGH)));
        assertEquals(500,ToneCatalog.durationMs(ToneCatalog.uri("samt",AlarmEngine.Kind.RISING)));
        assertEquals(0,ToneCatalog.durationMs(AlarmLevel.DEFAULT_TONE));
    }
    @Test public void boostPreservesOriginalDefaultAndProvidesNearlyDoubleTheAmplitudeWithinPcmHeadroom() {
        assertEquals(.48,.96*ToneCatalog.playbackGain(100,0),.000001);
        assertEquals(1.995262,ToneCatalog.playbackGain(100,6)/ToneCatalog.playbackGain(100,0),.00001);
        for (int boost=0;boost<=6;boost++) {
            assertTrue(ToneCatalog.playbackGain(100,boost)<=1);
            assertTrue(.96*ToneCatalog.playbackGain(100,boost)<1);
            assertEquals(0,ToneCatalog.playbackGain(0,boost),0);
        }
        assertEquals(.24,.96*ToneCatalog.playbackGain(50,0),.000001);
    }
}
