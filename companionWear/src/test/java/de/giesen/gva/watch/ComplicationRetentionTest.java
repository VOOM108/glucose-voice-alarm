// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.Application;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.provider.Settings;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ComplicationRetentionTest {
    Application c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();WatchConsent.accept(c,1);WatchConsent.accept(c,2);WatchStore.clear(c);Settings.Global.putInt(c.getContentResolver(),"boot_count",1);}
    private WatchSnapshot sample(){return sample(0);}
    private WatchSnapshot sample(long age){long now=System.currentTimeMillis();WatchSnapshot d=WatchRenderer.demo(now-age);return new WatchSnapshot(now,now-age,d.value,d.trend,d.mmol,true,d.rate,d.low,d.high,WatchSnapshot.minutes(d.points,now));}
    static final class Result implements ComplicationDataSourceService.ComplicationRequestListener {
        ComplicationData ordinary;ComplicationDataTimeline timeline;
        @Override public void onComplicationData(ComplicationData value){ordinary=value;}
        @Override public void onComplicationDataTimeline(ComplicationDataTimeline value){timeline=value;}
    }
    private Result request(ComplicationType type){
        DisplayComplication service=Robolectric.buildService(DisplayComplication.class).create().get();Result result=new Result();
        service.onComplicationRequest(new ComplicationRequest(1,type,false),result);return result;
    }
    private String text(ComplicationData d){return ((ShortTextComplicationData)d).getText().getTextAt(c.getResources(),Instant.ofEpochMilli(System.currentTimeMillis())).toString();}
    @Test public void freshImageHasATimedOverrideAndARealStaleFallbackForTheWholeWindow()throws Exception {
        WatchSnapshot s=sample();assertTrue(WatchStore.accept(c,s.encode()));Result r=request(ComplicationType.SMALL_IMAGE);
        assertNull(r.ordinary);assertNotNull(r.timeline);assertEquals(1,r.timeline.getTimelineEntries().size());
        TimelineEntry fresh=r.timeline.getTimelineEntries().iterator().next();
        assertEquals(new TimeInterval(Instant.ofEpochMilli(s.received),Instant.ofEpochMilli(s.received+WatchSnapshot.FRESH)),fresh.getValidity());
        assertTrue(r.timeline.getDefaultComplicationData() instanceof SmallImageComplicationData);
        assertEquals(new TimeRange(Instant.EPOCH,Instant.ofEpochMilli(s.received+WatchSnapshot.WINDOW)),r.timeline.getDefaultComplicationData().getValidTimeRange());
        assertNotNull(((SmallImageComplicationData)r.timeline.getDefaultComplicationData()).getSmallImage().getAmbientImage());
        long bytes=0;
        for(ComplicationData d:Arrays.asList(r.timeline.getDefaultComplicationData(),fresh.getComplicationData())) {
            SmallImage image=((SmallImageComplicationData)d).getSmallImage();
            for(android.graphics.drawable.Icon icon:Arrays.asList(image.getImage(),image.getAmbientImage())) {
                assertEquals(android.graphics.drawable.Icon.TYPE_DATA,icon.getType());
                android.os.Parcel parcel=android.os.Parcel.obtain();
                try {icon.writeToParcel(parcel,0);bytes+=parcel.dataSize();}finally {parcel.recycle();}
                Bitmap decoded=((BitmapDrawable)icon.loadDrawable(c)).getBitmap();assertEquals(320,decoded.getWidth());assertEquals(320,decoded.getHeight());
            }
        }
        assertTrue("All four lossless images must fit comfortably below Binder's transaction budget",bytes<512*1024);
    }
    @Test public void shortTextAlsoFallsBackToKnownOldValueInsteadOfAnEmptyField()throws Exception {
        assertTrue(WatchStore.accept(c,sample().encode()));Result r=request(ComplicationType.SHORT_TEXT);
        assertEquals("126 !",text(r.timeline.getDefaultComplicationData()));
        assertTrue(text(r.timeline.getTimelineEntries().iterator().next().getComplicationData()).startsWith("126"));
        assertFalse(text(r.timeline.getTimelineEntries().iterator().next().getComplicationData()).contains("!"));
    }
    @Test public void aFiveMinuteGapReturnsAnOrdinaryVisibleGrayImageWithoutRenewingReceipt()throws Exception {
        WatchSnapshot s=sample(5*60000);assertTrue(WatchStore.accept(c,s.encode()));
        Result r=request(ComplicationType.SMALL_IMAGE);assertNull(r.timeline);assertTrue(r.ordinary instanceof SmallImageComplicationData);
        assertEquals(s.received,WatchStore.read(c).snapshot.received);assertFalse(WatchStore.read(c).fresh(c,System.currentTimeMillis()));
        SmallImageComplicationData data=(SmallImageComplicationData)r.ordinary;
        Bitmap b=((BitmapDrawable)data.getSmallImage().getImage().loadDrawable(c)).getBitmap();int gray=0;
        for(int y=40;y<120;y++)for(int x=30;x<200;x++)if(b.getPixel(x,y)==0xff899298)gray++;
        assertTrue("The actual stale complication image must retain visible gray digits",gray>30);
        assertEquals("5",WatchRenderer.age(WatchStore.read(c).snapshot,System.currentTimeMillis()));
    }
    @Test public void unavailableSourcePreservesTheKnownValueButNeverGetsAFreshOverride()throws Exception {
        WatchSnapshot s=sample();WatchSnapshot old=new WatchSnapshot(s.generated,s.received,s.value,s.trend,s.mmol,false,s.rate,s.low,s.high,s.points);
        assertTrue(WatchStore.accept(c,old.encode()));Result r=request(ComplicationType.SHORT_TEXT);assertNull(r.timeline);assertEquals("126 !",text(r.ordinary));
    }
    @Test public void rebootExpiredCacheAndExplicitClearCannotReviveAReading()throws Exception {
        assertTrue(WatchStore.accept(c,sample().encode()));Settings.Global.putInt(c.getContentResolver(),"boot_count",2);
        assertEquals("—",text(request(ComplicationType.SHORT_TEXT).ordinary));Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
        WatchStore.clear(c);assertTrue(WatchStore.accept(c,sample(121*60000).encode()));assertEquals("—",text(request(ComplicationType.SHORT_TEXT).ordinary));
        WatchStore.clear(c);assertEquals("—",text(request(ComplicationType.SHORT_TEXT).ordinary));
    }
}
