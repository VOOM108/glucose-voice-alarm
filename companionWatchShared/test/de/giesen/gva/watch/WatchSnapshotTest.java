// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
public class WatchSnapshotTest {
    private static final long NOW=1800000000000L;
    private WatchSnapshot sample(boolean mmol){return new WatchSnapshot(NOW,NOW-60000,126,WatchSnapshot.Trend.UP_RIGHT,mmol,true,1.2,80.,180.,Arrays.asList(new WatchSnapshot.Point(NOW-60000,126)));}
    private void bad(byte[] packet){try{WatchSnapshot.decode(packet,NOW);fail("Malformed packet accepted");}catch(IOException expected){}}
    @Test public void mgdlRoundTripPreservesCanonicalValues()throws Exception{WatchSnapshot s=WatchSnapshot.decode(sample(false).encode(),NOW);assertEquals(126,s.value,0);assertFalse(s.mmol);assertEquals(WatchSnapshot.Trend.UP_RIGHT,s.trend);}
    @Test public void mmolRoundTripDoesNotPreRound()throws Exception{WatchSnapshot s=WatchSnapshot.decode(sample(true).encode(),NOW);assertTrue(s.mmol);assertEquals(1.2,s.rate,0);}
    @Test public void everyTruncationFailsClosed()throws Exception{byte[] b=sample(false).encode();for(int i=0;i<b.length;i++)bad(Arrays.copyOf(b,i));}
    @Test public void trailingBytesAreRejected()throws Exception{byte[] b=sample(false).encode();bad(Arrays.copyOf(b,b.length+1));}
    @Test public void schemaMismatchRejected()throws Exception{byte[] b=sample(false).encode();b[7]=2;bad(b);}
    @Test public void oversizedPacketRejected(){bad(new byte[WatchSnapshot.MAX_BYTES+1]);}
    @Test public void futurePacketRejected()throws Exception{bad(new WatchSnapshot(NOW+60001,NOW,126,WatchSnapshot.Trend.FLAT,false,true,null,null,null,Collections.emptyList()).encode());}
    @Test public void stalePacketCannotRenewReadingAge(){assertFalse(sample(false).fresh(NOW+WatchSnapshot.FRESH));}
    @Test public void backwardsWallClockIsNotFresh(){assertFalse(sample(false).fresh(NOW-60001));}
    @Test public void backwardsMonotonicClockIsNotFresh(){assertFalse(sample(false).fresh(NOW,NOW,2000,1000));}
    @Test public void wallJumpIsNotFresh(){assertFalse(sample(false).fresh(NOW+120001,NOW,1000,1001));}
    @Test public void receiptAgePlusTransitAgeBothCount(){assertFalse(sample(false).fresh(NOW+180001,NOW,1000,181001));}
    @Test public void noInferredCurrentSource(){WatchSnapshot s=new WatchSnapshot(NOW,NOW-60000,126,WatchSnapshot.Trend.UNKNOWN,false,false,null,null,null,Collections.emptyList());assertFalse(s.fresh(NOW));}
    @Test(expected=IllegalArgumentException.class) public void nonFiniteGlucoseRejected(){new WatchSnapshot(NOW,NOW,Double.NaN,WatchSnapshot.Trend.FLAT,false,true,null,null,null,Collections.emptyList());}
    @Test(expected=IllegalArgumentException.class) public void unorderedHistoryRejected(){new WatchSnapshot(NOW,NOW,126,WatchSnapshot.Trend.FLAT,false,true,null,null,null,Arrays.asList(new WatchSnapshot.Point(NOW,126),new WatchSnapshot.Point(NOW-1,125)));}
    @Test(expected=IllegalArgumentException.class) public void contradictoryBandsRejected(){new WatchSnapshot(NOW,NOW,126,WatchSnapshot.Trend.FLAT,false,true,null,180.,80.,Collections.emptyList());}
    @Test public void minuteReductionKeepsLastRealReceiptAndGap(){List<WatchSnapshot.Point> p=WatchSnapshot.minutes(Arrays.asList(new WatchSnapshot.Point(NOW-300000,100),new WatchSnapshot.Point(NOW-299999,101),new WatchSnapshot.Point(NOW,126)),NOW);assertEquals(2,p.size());assertEquals(101,p.get(0).value,0);assertTrue(p.get(1).time-p.get(0).time>WatchSnapshot.GAP);}
    @Test public void maximumTwoHourMinuteHistoryIsBounded()throws Exception{List<WatchSnapshot.Point> p=new ArrayList<>();for(int i=0;i<=120;i++)p.add(new WatchSnapshot.Point(NOW-i*60000L,126));Collections.reverse(p);assertEquals(121,WatchSnapshot.minutes(p,NOW).size());assertTrue(new WatchSnapshot(NOW,NOW,126,WatchSnapshot.Trend.FLAT,false,true,null,null,null,p).encode().length<4096);}
}
