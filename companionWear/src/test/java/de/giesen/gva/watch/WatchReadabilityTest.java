// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.Application;
import android.graphics.*;
import android.provider.Settings;
import android.os.Looper;
import android.icu.util.ULocale;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import androidx.wear.protolayout.expression.DynamicBuilders.*;
import androidx.wear.protolayout.expression.pipeline.*;
import java.util.concurrent.atomic.AtomicReference;
import java.io.*;
import java.time.Instant;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchReadabilityTest {
    Application c;
    @Before public void reset(){
        c=RuntimeEnvironment.getApplication();WatchConsent.accept(c,1);WatchConsent.accept(c,2);
        WatchStore.clear(c);Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
    }
    private WatchSnapshot sample(long generated,long age){
        WatchSnapshot s=WatchRenderer.demo(generated);
        return new WatchSnapshot(generated,generated-age,s.value,s.trend,false,true,1.2,s.low,s.high,s.points);
    }
    @Test public void threeSecondPhoneLeadPreservesFreshDeltaAndSourceAge()throws Exception{
        long now=System.currentTimeMillis(),generated=now+3000;
        assertTrue(WatchStore.accept(c,sample(generated,1000).encode()));
        WatchStore.Entry e=WatchStore.read(c);
        assertTrue(e.fresh(c,System.currentTimeMillis()));assertEquals(1.2,e.snapshot.rate,0);
        assertEquals(generated,e.sourceGenerated);assertEquals(1000,e.snapshot.generated-e.snapshot.received);
        assertTrue(e.snapshot.received<=e.wall);assertTrue(e.clockValid(c,System.currentTimeMillis()));
    }
    @Test public void clockTranslationDoesNotAdmitAnOlderSourcePacket()throws Exception{
        long now=System.currentTimeMillis();assertTrue(WatchStore.accept(c,sample(now+3000,1000).encode()));
        assertFalse(WatchStore.accept(c,sample(now+2000,1000).encode()));
    }
    @Test public void translatingClockCannotRenewOldReceipt()throws Exception{
        long now=System.currentTimeMillis();assertTrue(WatchStore.accept(c,sample(now+3000,WatchSnapshot.FRESH+1).encode()));
        WatchStore.Entry e=WatchStore.read(c);assertFalse(e.fresh(c,System.currentTimeMillis()));
        assertEquals(WatchSnapshot.FRESH+1,e.snapshot.generated-e.snapshot.received);
    }
    @Test public void largerThanAllowedFutureClockRemainsRejected()throws Exception{
        long now=System.currentTimeMillis();
        assertThrows(IOException.class,()->WatchStore.accept(c,sample(now+WatchSnapshot.FUTURE+10000,1000).encode()));
    }
    @Test public void ageIsCompletedMinutesAndNeverAnAbsoluteClock(){
        long now=1800000000000L;WatchSnapshot s=sample(now,0);
        assertEquals("0",WatchRenderer.age(s,now+59999));assertEquals("1",WatchRenderer.age(s,now+60000));
        assertEquals("5",WatchRenderer.age(s,now+300000));assertEquals("—",WatchRenderer.age(s,now-1));
        assertEquals("—",WatchRenderer.age(null,now));
    }
    private String text(ComplicationData d,long now){
        return ((ShortTextComplicationData)d).getText().getTextAt(c.getResources(),Instant.ofEpochMilli(now)).toString();
    }
    private String evaluate(long received,long now)throws Exception {
        AtomicReference<String> result=new AtomicReference<>();
        DynamicString expression=AgeComplication.expression(received,DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(now)));
        DynamicTypeEvaluator evaluator=new DynamicTypeEvaluator(new DynamicTypeEvaluator.Config.Builder().build());
        BoundDynamicType bound=evaluator.bind(DynamicTypeBindingRequest.forDynamicString(expression,ULocale.US,Runnable::run,
            new DynamicTypeValueReceiver<String>(){
                @Override public void onData(String value){result.set(value);}
                @Override public void onInvalidated(){result.set("invalid");}
            }));
        try {bound.startEvaluation();Shadows.shadowOf(Looper.getMainLooper()).idle();return result.get();}
        finally {bound.close();}
    }
    @Test public void nativeExpressionIncludesZeroAndCompletedMinuteBoundaries()throws Exception{
        long received=1800000000000L;
        assertEquals("0",evaluate(received,received));assertEquals("0",evaluate(received,received+59999));
        assertEquals("1",evaluate(received,received+60000));assertEquals("—",evaluate(received,received+121*60000));
        assertEquals("0",evaluate(received+900,received+60000));
    }
    @Test public void missingOrUntrustedTimeCannotCreateDynamicAge(){
        long now=1800000000000L;AgeComplication service=Robolectric.buildService(AgeComplication.class).create().get();
        for(ComplicationData data:new ComplicationData[]{service.ageData(null,now,true),service.ageData(sample(now,61000),now,false),service.ageData(sample(now,WatchSnapshot.WINDOW+1),now,true)}){
            assertFalse(((ShortTextComplicationData)data).getText() instanceof DynamicComplicationText);assertEquals("—",text(data,now));
        }
    }
    @Test public void ordinaryCallbackDeliversDynamicTextInsteadOfTimeline()throws Exception{
        long now=System.currentTimeMillis();assertTrue(WatchStore.accept(c,sample(now,0).encode()));
        AgeComplication service=Robolectric.buildService(AgeComplication.class).create().get();
        AtomicReference<ComplicationData> result=new AtomicReference<>();
        service.onComplicationRequest(new ComplicationRequest(2,ComplicationType.SHORT_TEXT,false),new ComplicationDataSourceService.ComplicationRequestListener(){
            @Override public void onComplicationData(ComplicationData value){result.set(value);}
            @Override public void onComplicationDataTimeline(ComplicationDataTimeline value){fail("Must use ordinary data callback");}
        });
        assertTrue(((ShortTextComplicationData)result.get()).getText() instanceof DynamicComplicationText);
        assertEquals(TimeRange.ALWAYS,result.get().getValidTimeRange());
    }
    @Test public void previewShowsZeroEvenBeforeFirstData(){
        AgeComplication service=Robolectric.buildService(AgeComplication.class).create().get();
        assertEquals("0",text(service.getPreviewData(ComplicationType.SHORT_TEXT),System.currentTimeMillis()));
    }
    @Test public void staticFooterZeroHasBlackOutlinesWithoutVariationSelection()throws Exception{
        Typeface fixed=c.getResources().getFont(R.font.roboto_flex_footer);
        Paint regular=new Paint(Paint.ANTI_ALIAS_FLAG);regular.setTypeface(c.getResources().getFont(R.font.roboto_flex));
        regular.setFontVariationSettings("'wght' 400, 'wdth' 100, 'opsz' 32");regular.setTextSize(72);regular.setColor(Color.WHITE);
        Paint black=new Paint(regular);black.setTypeface(fixed);
        Paint reference=new Paint(regular);reference.setFontVariationSettings("'wght' 900, 'wdth' 100, 'opsz' 32");
        Bitmap a=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),r=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        new Canvas(a).drawText("0",10,80,regular);new Canvas(b).drawText("0",10,80,black);new Canvas(r).drawText("0",10,80,reference);
        int normal=pixels(a,Color.WHITE,0,0,100,100),thick=pixels(b,Color.WHITE,0,0,100,100),expected=pixels(r,Color.WHITE,0,0,100,100);
        assertEquals(900,fixed.getWeight());
        assertTrue("The packaged zero must have heavier outlines without setting axes",thick>normal*1.4);
        assertEquals("Static and variable Black must have matching width",reference.measureText("0123456789"),black.measureText("0123456789"),1.0);
        assertTrue("Static zero must match Roboto Flex Black rather than a fallback",Math.abs(thick-expected)<expected*.1);
    }
    private int pixels(Bitmap b,int color,int left,int top,int right,int bottom){
        int count=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(b.getPixel(x,y)==color)count++;return count;
    }
    @Test public void coloredThickCurveAndWhiteArrowRemainVisibleInBothModes()throws Exception{
        long now=1800000000000L;WatchSnapshot s=WatchRenderer.demo(now);WatchRenderer renderer=new WatchRenderer(c);
        for(boolean ambient:new boolean[]{false,true}){
            Bitmap b=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);
            renderer.draw(new Canvas(b),600,s,true,ambient,1,true,false,now);
            assertTrue(pixels(b,ambient?0xffcca870:0xffffd28c,92,245,509,387)>20);
            assertTrue(pixels(b,ambient?0xffe65353:0xffff5c5c,92,245,509,387)>20);
            assertTrue(pixels(b,ambient?0xffaac5b9:0xffd4f6e7,92,245,509,387)>100);
            assertTrue(pixels(b,ambient?0xffdddddd:Color.WHITE,370,110,465,215)>150);
            assertTrue(pixels(b,ambient?0xffd5dadc:0xfff4f6f7,140,425,270,485)>30);
            if(!ambient){
                assertTrue("Blue hour dot must remain visible",pixels(b,0xff64b5ff,0,0,600,600)>200);
                assertTrue("Orange minute dot must remain visible",pixels(b,0xffffaa55,0,0,600,600)>200);
            } else {
                assertTrue("Dim blue hour dot must be visible",pixels(b,0xff2b4e6e,0,0,600,600)>200);
                assertTrue("Dim orange minute dot must be visible",pixels(b,0xff6e4924,0,0,600,600)>200);
            }
        }
        Bitmap empty=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(empty),600,null,false,false,1,true,false,now);
        assertTrue(pixels(empty,0xfff4f6f7,140,425,270,485)>10);
        assertTrue(pixels(empty,0xff899298,330,425,465,485)>10);
    }
}
