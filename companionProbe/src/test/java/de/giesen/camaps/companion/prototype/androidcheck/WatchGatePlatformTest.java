// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class WatchGatePlatformTest {
    android.app.Application c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("gva_watch_link_v1",0).edit().clear().commit();c.getSharedPreferences("gva_safety_ack_v1",0).edit().clear().commit();ProbeState.latest=null;}
    @Test public void linkIsOffByDefault(){assertFalse(WatchPublisher.enabled(c));}
    @Test public void pendingLegalGateCannotEnableLink(){WatchPublisher.set(c,true);assertFalse(WatchPublisher.enabled(c));assertFalse(c.getSharedPreferences("gva_watch_link_v1",0).getBoolean("enabled",false));}
    @Test public void storedToggleCannotBypassLegalGate(){c.getSharedPreferences("gva_watch_link_v1",0).edit().putBoolean("enabled",true).commit();assertFalse(WatchPublisher.enabled(c));}
    @Test public void absentReceiptNeverCreatesSnapshot()throws Exception{assertNull(WatchPublisher.snapshot(c));}
}
