// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import org.junit.Test;
import static org.junit.Assert.*;

public class VectorHistoryTest {
    private String hash(int value) { return String.format(java.util.Locale.ROOT,"%064x",value); }
    private VectorHistory.Entry entry(int exact,int shape) { return new VectorHistory.Entry(hash(exact),hash(shape),NotificationParser.Trend.UP_RIGHT); }
    @Test public void repeatedExactImageAndDifferentPixelsOfTheSameShapeRemainDistinguishable() {
        String history=VectorHistory.append("",entry(1,10)); history=VectorHistory.append(history,entry(1,10)); history=VectorHistory.append(history,entry(2,10));
        assertArrayEquals(new int[]{2,3,2},VectorHistory.counts(history,hash(1),hash(10)));
        assertArrayEquals(new int[]{0,0,0},VectorHistory.counts(history,hash(3),hash(30)));
    }
    @Test public void last64ConfirmationsSurviveEncodingWithoutAnUnboundedLog() {
        String history=""; for (int i=0;i<80;i++) history=VectorHistory.append(history,entry(i,10));
        assertEquals(64,VectorHistory.decode(history).size()); assertTrue(history.length()<10000);
        assertArrayEquals(new int[]{0,64,64},VectorHistory.counts(history,hash(1),hash(10)));
    }
    @Test public void unusableMaskDoesNotAcquireAFormIdentity() {
        String history=VectorHistory.append("",new VectorHistory.Entry(hash(1),null,NotificationParser.Trend.FLAT));
        assertArrayEquals(new int[]{1,0,0},VectorHistory.counts(history,hash(1),null));
    }
    @Test public void arbitraryTextCannotEnterTheHistoryAndInvalidHistoryIsNotSilentlyReplaced() {
        try { new VectorHistory.Entry("private text",null,NotificationParser.Trend.UP); fail(); } catch (IllegalArgumentException expected) { }
        try { VectorHistory.append("broken",entry(1,10)); fail(); } catch (IllegalArgumentException expected) { }
    }
}
