// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.os.SystemClock;
import de.giesen.gva.watch.WatchSnapshot;
import static org.junit.Assert.*;
/** Synthetic phone receipt -> private history -> wire snapshot, with no transport calls. */
@RunWith(RobolectricTestRunner.class) @Config(sdk={28,34})
public class WatchReceiptPlatformTest {
    android.app.Application c;
    @Before public void reset(){
        c=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(c);
        c.getSharedPreferences("gva_watch_link_v1",0).edit().clear().commit();
        c.getSharedPreferences(UnitPreferences.PREFS,0).edit().clear().commit();
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        c.deleteDatabase("probe-observations.db");ProbeState.latest=null;
    }
    private void receipt(GlucoseUnit unit)throws Exception {
        assertTrue(UnitPreferences.accept(c,unit));
        long now=System.currentTimeMillis();
        try(ObservationStore store=new ObservationStore(c)){
            for(int i=0;i<4;i++)assertTrue(store.insert("synthetic-watch",now-(3-i)*60000L,now-(3-i)*60000L,
                    new NotificationParser.Result(120.+i*2,NotificationParser.Trend.UP_RIGHT,"synthetic",unit)));
        }
        ProbeState.latest=new ProbeState.Observation(126,NotificationParser.Trend.UP_RIGHT,SystemClock.elapsedRealtime(),now,"synthetic-watch");
        c.getSharedPreferences("gva_watch_link_v1",0).edit().putBoolean("enabled",true).commit();
    }
    @Test public void newReceiptCreatesOnlyDisplaySnapshot()throws Exception{
        receipt(GlucoseUnit.MGDL);WatchSnapshot s=WatchSnapshot.decode(WatchPublisher.snapshot(c),System.currentTimeMillis());
        assertEquals(126,s.value,0);assertEquals(WatchSnapshot.Trend.UP_RIGHT,s.trend);assertEquals(4,s.points.size());assertTrue(s.fresh(System.currentTimeMillis()));assertFalse(s.mmol);
    }
    @Test public void mmolSelectionPreservesCanonicalPayload()throws Exception{
        receipt(GlucoseUnit.MMOL);WatchSnapshot s=WatchSnapshot.decode(WatchPublisher.snapshot(c),System.currentTimeMillis());assertTrue(s.mmol);assertEquals(126,s.value,0);
    }
    @Test public void historicalDatabaseAloneCannotCreateCurrentReceipt()throws Exception{receipt(GlucoseUnit.MGDL);ProbeState.latest=null;assertNull(WatchPublisher.snapshot(c));}
    @Test public void unitConflictBlocksWatchSnapshot()throws Exception{receipt(GlucoseUnit.MGDL);UnitPreferences.markConflict(c);assertNull(WatchPublisher.snapshot(c));}
    @Test public void disabledReceptionBlocksWatchSnapshot()throws Exception{receipt(GlucoseUnit.MGDL);c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,false).commit();assertNull(WatchPublisher.snapshot(c));}
}
