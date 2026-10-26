// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.NotificationParser.Trend.*;

public class TrendReferenceTest {
    private NotificationParser.Result parsed(String... text) { return NotificationParser.parse(Arrays.asList(text)); }
    @Test public void exactPixelsAndSourceVersionAreRequired() {
        int[] pixels=new int[64*64]; pixels[100]=0xff123456;
        String key=TrendReference.fingerprint("source-version-1",pixels);
        assertEquals(key,TrendReference.fingerprint("source-version-1",pixels.clone()));
        assertNotEquals(key,TrendReference.fingerprint("source-version-2",pixels));
        pixels[100]=0xff123457;
        assertNotEquals(key,TrendReference.fingerprint("source-version-1",pixels));
    }
    @Test public void onlyExplicitReferencesMatch() {
        String key=TrendReference.fingerprint("source",new int[64*64]);
        Map<String,Object> stored=new HashMap<>();
        assertEquals(UNKNOWN,TrendReference.lookup(stored,key));
        stored.put(key,FLAT.name()); assertEquals(FLAT,TrendReference.lookup(stored,key));
        assertEquals(UNKNOWN,TrendReference.lookup(stored,"another shape"));
        assertEquals(UNKNOWN,TrendReference.lookup(stored,null));
        stored.put(key,"invalid"); assertEquals(UNKNOWN,TrendReference.lookup(stored,key));
        stored.put(key,123); assertEquals(UNKNOWN,TrendReference.lookup(stored,key));
    }
    @Test public void everyDirectionNeedsItsOwnConfirmedShape() {
        Map<String,Object> stored=new HashMap<>(); int[] pixels=new int[64*64];
        for (NotificationParser.Trend trend:NotificationParser.Trend.values()) {
            if (trend==UNKNOWN) continue;
            pixels[trend.ordinal()]=0xff000000;
            String key=TrendReference.fingerprint("source",pixels);
            assertEquals(UNKNOWN,TrendReference.lookup(stored,key));
            stored.put(key,trend.name()); assertEquals(trend,TrendReference.lookup(stored,key));
        }
    }
    @Test public void referenceCannotCreateMissingValueOrChangeIt() {
        assertNull(TrendReference.combine(parsed("Insulin abgegeben"),FLAT).mgdl);
        NotificationParser.Result combined=TrendReference.combine(parsed("123 mg/dl"),FLAT);
        assertEquals(Integer.valueOf(123),combined.mgdl); assertEquals(FLAT,combined.trend);
        assertEquals("CamAPS-Trend aus bestätigter Grafikreferenz",combined.reason);
    }
    @Test public void knownAndUnknownTextHaveConservativeFallbacks() {
        assertEquals(UP,TrendReference.combine(parsed("123 ↑"),UNKNOWN).trend);
        assertEquals(UNKNOWN,TrendReference.combine(parsed("123"),UNKNOWN).trend);
        assertEquals(UP,TrendReference.combine(parsed("123 ↑"),UP).trend);
        assertEquals(UNKNOWN,TrendReference.combine(parsed("123 ↑"),FLAT).trend);
        assertEquals(UNKNOWN,TrendReference.combine(parsed("123 ↑", "↓"),FLAT).trend);
    }
    @Test public void referenceCannotResolveConflictingValues() {
        assertNull(TrendReference.combine(parsed("123", "145"),FLAT).mgdl);
    }
    @Test public void missingArrowNeverReusesThePreviousFlatReferenceOrDiscardsTheValue() {
        Map<String,Object> stored=new HashMap<>();
        String key=TrendReference.fingerprint("source",new int[64*64]); stored.put(key,FLAT.name());
        assertEquals(FLAT,TrendReference.combine(parsed("123"),TrendReference.lookup(stored,key)).trend);
        NotificationParser.Result noArrow=TrendReference.combine(parsed("123"),TrendReference.lookup(stored,null));
        assertEquals(UNKNOWN,noArrow.trend); assertEquals(Integer.valueOf(123),noArrow.mgdl);
        assertEquals(FLAT,TrendReference.combine(parsed("123"),TrendReference.lookup(stored,key)).trend);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidRenderingIsRejected() {
        TrendReference.fingerprint("source",new int[1]);
    }
}
