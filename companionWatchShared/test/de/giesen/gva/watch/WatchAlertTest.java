// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WatchAlertTest {
    private WatchAlert sample(long issued){return new WatchAlert(WatchAlert.Action.SHOW,UUID.randomUUID().toString(),1,UUID.randomUUID().toString(),issued,"Synthetic alarm","Synthetic text",false);}
    @Test public void boundedEventRoundTripsWithoutDisplayHistory()throws Exception{WatchAlert a=sample(1800000000000L),b=WatchAlert.decode(a.encode(),a.issued+500);assertEquals(a.session,b.session);assertEquals(a.token,b.token);assertEquals(a.text,b.text);assertEquals(WatchAlert.Action.SHOW,b.action);assertFalse(b.test);assertTrue(a.encode().length<2048);}
    @Test public void delayedAndFutureEventsAreRejected()throws Exception{WatchAlert a=sample(1800000000000L);assertThrows(IOException.class,()->WatchAlert.decode(a.encode(),a.issued+30001));assertThrows(IOException.class,()->WatchAlert.decode(a.encode(),a.issued-60001));assertNotNull(WatchAlert.decode(a.encode(),a.issued-3000));}
    @Test public void trailingOrOversizedDataCannotBecomeAnAlert()throws Exception{byte[] a=sample(1800000000000L).encode();assertThrows(IOException.class,()->WatchAlert.decode(Arrays.copyOf(a,a.length+1),1800000000000L));assertThrows(IOException.class,()->WatchAlert.decode(new byte[2049],1800000000000L));}
    @Test public void malformedIdentifiersAndControlsAreRejected(){assertThrows(IllegalArgumentException.class,()->new WatchAlert(WatchAlert.Action.SHOW,"invalid",1,UUID.randomUUID().toString(),1,"Alarm","text",false));assertThrows(IllegalArgumentException.class,()->new WatchAlert(WatchAlert.Action.SHOW,UUID.randomUUID().toString(),1,UUID.randomUUID().toString(),1,"Alarm","bad\u0000text",false));}
    @Test public void truncatedBytesAndUnknownVersionAreRejected()throws Exception{byte[] a=sample(1800000000000L).encode();assertThrows(IOException.class,()->WatchAlert.decode(Arrays.copyOf(a,20),1800000000000L));a[7]=2;assertThrows(IOException.class,()->WatchAlert.decode(a,1800000000000L));}
    @Test public void clearContainsOnlyAnIdentifierAndNeverTestControl()throws Exception{WatchAlert a=new WatchAlert(WatchAlert.Action.CLEAR,UUID.randomUUID().toString(),2,UUID.randomUUID().toString(),1800000000000L,"","",false);assertEquals(WatchAlert.Action.CLEAR,WatchAlert.decode(a.encode(),a.issued).action);assertThrows(IllegalArgumentException.class,()->new WatchAlert(WatchAlert.Action.CLEAR,a.session,3,a.token,a.issued,"","",true));}
}
