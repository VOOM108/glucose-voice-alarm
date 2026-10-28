// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.provider.Settings;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import java.time.Instant;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IndividualComplicationTest {
    Application c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();WatchConsent.accept(c,1);WatchConsent.accept(c,2);WatchStore.clear(c);WatchStore.hours(c,1);Settings.Global.putInt(c.getContentResolver(),"boot_count",1);}
    private <T extends IndividualComplication>T service(Class<T> type){return Robolectric.buildService(type).create().get();}
    private WatchSnapshot sample(long age,boolean mmol,boolean available){
        long now=System.currentTimeMillis();WatchSnapshot s=WatchRenderer.demo(now);
        return new WatchSnapshot(now,now-age,s.value,s.trend,mmol,available,s.rate,s.low,s.high,s.points);
    }
    private ComplicationRetentionTest.Result request(Class<? extends IndividualComplication> source,ComplicationType type){
        ComplicationRetentionTest.Result r=new ComplicationRetentionTest.Result();service(source).onComplicationRequest(new ComplicationRequest(1,type,false),r);return r;
    }
    private String text(ComplicationData data){return ((ShortTextComplicationData)data).getText().getTextAt(c.getResources(),Instant.ofEpochMilli(System.currentTimeMillis())).toString();}
    private ComplicationData fresh(ComplicationRetentionTest.Result r){assertNotNull(r.timeline);return r.timeline.getTimelineEntries().iterator().next().getComplicationData();}
    @Test public void individualValueDoesNotIncludeAnArrowAndKeepsOriginalExpiry()throws Exception {
        WatchSnapshot s=sample(0,false,true);assertTrue(WatchStore.accept(c,s.encode()));
        ComplicationRetentionTest.Result r=request(ValueComplication.class,ComplicationType.SHORT_TEXT);
        assertEquals("126",text(fresh(r)));assertEquals("126 !",text(r.timeline.getDefaultComplicationData()));
        assertEquals(new TimeRange(Instant.EPOCH,Instant.ofEpochMilli(s.received+WatchSnapshot.WINDOW)),fresh(r).getValidTimeRange());
    }
    @Test public void deltaUsesThePhoneSelectedUnitAndRemainsAPerMinuteRate()throws Exception {
        for(boolean mmol:new boolean[]{false,true}){
            WatchStore.clear(c);WatchSnapshot s=sample(0,mmol,true);assertTrue(WatchStore.accept(c,s.encode()));
            ComplicationRetentionTest.Result r=request(DeltaComplication.class,ComplicationType.SHORT_TEXT);
            assertEquals(WatchRenderer.number(1.2,mmol,true),text(fresh(r)));assertEquals("—",text(r.timeline.getDefaultComplicationData()));
            assertEquals(WatchRenderer.number(126,mmol,false),text(fresh(request(ValueComplication.class,ComplicationType.SHORT_TEXT))));
        }
    }
    @Test public void allSevenKnownCamArrowsAreIndependentOfNumericRate(){
        TrendComplication service=service(TrendComplication.class);long now=System.currentTimeMillis();WatchSnapshot demo=WatchRenderer.demo(now);
        String[] expected={"⇈","↑","↗","→","↘","↓","⇊","—"};
        for(WatchSnapshot.Trend trend:WatchSnapshot.Trend.values()){
            WatchSnapshot s=new WatchSnapshot(now,now,126,trend,false,true,-9.,80.,180.,demo.points);
            assertEquals(expected[trend.ordinal()],text(service.data(ComplicationType.SHORT_TEXT,s,true,true,now)));
            ComplicationData icon=service.data(ComplicationType.MONOCHROMATIC_IMAGE,s,true,true,now);
            assertEquals(trend!=WatchSnapshot.Trend.UNKNOWN,icon instanceof MonochromaticImageComplicationData);
        }
    }
    @Test public void staleOrUnavailableSourceCannotKeepDeltaOrTrendCurrent()throws Exception {
        for(WatchSnapshot s:Arrays.asList(sample(5*60000,false,true),sample(0,false,false))){
            WatchStore.clear(c);assertTrue(WatchStore.accept(c,s.encode()));
            for(Class<? extends IndividualComplication> source:Arrays.asList(TrendComplication.class,DeltaComplication.class)){
                ComplicationRetentionTest.Result r=request(source,ComplicationType.SHORT_TEXT);assertNull(r.timeline);assertEquals("—",text(r.ordinary));
            }
            assertTrue(request(TrendComplication.class,ComplicationType.MONOCHROMATIC_IMAGE).ordinary instanceof NoDataComplicationData);
            assertEquals("126 !",text(request(ValueComplication.class,ComplicationType.SHORT_TEXT).ordinary));
        }
    }
    @Test public void rebootExpiryConsentAndClearHideTheSeparateValues()throws Exception {
        WatchSnapshot s=sample(0,false,true);assertTrue(WatchStore.accept(c,s.encode()));
        Settings.Global.putInt(c.getContentResolver(),"boot_count",2);assertEquals("—",text(request(ValueComplication.class,ComplicationType.SHORT_TEXT).ordinary));
        Settings.Global.putInt(c.getContentResolver(),"boot_count",1);WatchStore.clear(c);
        assertTrue(WatchStore.accept(c,sample(121*60000,false,true).encode()));assertEquals("—",text(request(ValueComplication.class,ComplicationType.SHORT_TEXT).ordinary));
        WatchStore.clear(c);assertEquals("—",text(request(ValueComplication.class,ComplicationType.SHORT_TEXT).ordinary));
        assertTrue(WatchStore.accept(c,sample(0,false,true).encode()));c.getSharedPreferences("watch_safety_v1",0).edit().clear().commit();
        // A separate source must consult the same consent gate as the composite.
        assertFalse(WatchConsent.ready(c));assertEquals("—",text(request(ValueComplication.class,ComplicationType.SHORT_TEXT).ordinary));
    }
    @Test public void freshTrendTimelineHasAnEmptyFallbackAndFiniteFreshInterval()throws Exception {
        WatchSnapshot s=sample(0,false,true);assertTrue(WatchStore.accept(c,s.encode()));
        ComplicationRetentionTest.Result r=request(TrendComplication.class,ComplicationType.MONOCHROMATIC_IMAGE);
        assertTrue(fresh(r) instanceof MonochromaticImageComplicationData);assertTrue(r.timeline.getDefaultComplicationData() instanceof NoDataComplicationData);
        assertEquals(new TimeInterval(Instant.ofEpochMilli(s.received),Instant.ofEpochMilli(s.received+WatchSnapshot.FRESH)),r.timeline.getTimelineEntries().iterator().next().getValidity());
    }
    @Test public void curveIsIndependentLandscapeImageWithAnAmbientVersionAndSmallParcel()throws Exception {
        assertTrue(WatchStore.accept(c,sample(0,false,true).encode()));ComplicationRetentionTest.Result r=request(CurveComplication.class,ComplicationType.SMALL_IMAGE);
        long total=0;int images=0;
        for(ComplicationData item:Arrays.asList(fresh(r),r.timeline.getDefaultComplicationData())){
            SmallImage si=((SmallImageComplicationData)item).getSmallImage();
            for(android.graphics.drawable.Icon icon:Arrays.asList(si.getImage(),si.getAmbientImage())){
                assertNotNull(icon);Bitmap b=((BitmapDrawable)icon.loadDrawable(c)).getBitmap();assertEquals(320,b.getWidth());assertEquals(120,b.getHeight());
                int visible=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if((b.getPixel(x,y)&0xffffff)!=0)visible++;
                assertTrue(visible>100);android.os.Parcel parcel=android.os.Parcel.obtain();try{icon.writeToParcel(parcel,0);total+=parcel.dataSize();}finally{parcel.recycle();}images++;
            }
        }
        assertEquals(4,images);assertTrue(total<256*1024);
    }
    @Test public void unsupportedSlotTypesDoNotAccidentallyReturnAnotherPart(){
        assertTrue(service(ValueComplication.class).getPreviewData(ComplicationType.SMALL_IMAGE) instanceof NoDataComplicationData);
        assertTrue(service(CurveComplication.class).getPreviewData(ComplicationType.SHORT_TEXT) instanceof NoDataComplicationData);
    }
    @Test public void manifestUsesThePlatformIconNameAndAllSourcesArePermissionProtected()throws Exception {
        Class<?>[] sources={DisplayComplication.class,AgeComplication.class,ValueComplication.class,TrendComplication.class,DeltaComplication.class,CurveComplication.class};
        String[] types={"SHORT_TEXT,SMALL_IMAGE","SHORT_TEXT","SHORT_TEXT","SHORT_TEXT,ICON","SHORT_TEXT","SMALL_IMAGE"};
        for(int i=0;i<sources.length;i++){
            ServiceInfo info=c.getPackageManager().getServiceInfo(new ComponentName(c,sources[i]),PackageManager.GET_META_DATA);
            assertTrue(info.exported);assertEquals("com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER",info.permission);
            assertEquals(types[i],info.metaData.getString("android.support.wearable.complications.SUPPORTED_TYPES"));
        }
    }
    @Test public void changingWindowChangesOnlyTheSeparateCurveAndAllPreviewsRender()throws Exception {
        long now=1800000000000L;WatchSnapshot s=WatchRenderer.demo(now);WatchRenderer renderer=new WatchRenderer(c);
        Bitmap one=renderer.curveImage(s,true,false,1,now),two=renderer.curveImage(s,true,false,2,now);assertFalse(one.sameAs(two));
        File dir=new File(System.getProperty("gva.watch.renders"));dir.mkdirs();
        save(one,new File(dir,"complication-curve-one-hour.png"));save(two,new File(dir,"complication-curve-two-hours.png"));
        save(renderer.curveImage(s,false,true,1,now),new File(dir,"complication-curve-stale.png"));
        save(renderer.trendImage(WatchSnapshot.Trend.DOUBLE_UP,false),new File(dir,"complication-trend-double-up.png"));
        // Documentation examples come from the real providers, never a device capture.
        DisplayComplication composite=Robolectric.buildService(DisplayComplication.class).create().get();
        SmallImage image=((SmallImageComplicationData)composite.getPreviewData(ComplicationType.SMALL_IMAGE)).getSmallImage();
        save(((BitmapDrawable)image.getImage().loadDrawable(c)).getBitmap(),new File(dir,"complication-composite.png"));
        for(Class<? extends IndividualComplication> type:Arrays.asList(ValueComplication.class,TrendComplication.class,DeltaComplication.class)){
            ComplicationData preview=service(type).getPreviewData(ComplicationType.SHORT_TEXT);
            saveText(text(preview),new File(dir,"complication-text-"+type.getSimpleName().replace("Complication","").toLowerCase(java.util.Locale.ROOT)+".png"));
        }
        AgeComplication age=Robolectric.buildService(AgeComplication.class).create().get();
        saveText(text(age.getPreviewData(ComplicationType.SHORT_TEXT)),new File(dir,"complication-text-age.png"));
    }
    private void saveText(String value,File file)throws Exception {
        Bitmap b=Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.BLACK);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);paint.setTypeface(c.getResources().getFont(R.font.roboto_flex_footer));paint.setTextSize(72);paint.setTextAlign(Paint.Align.CENTER);
        float width=paint.measureText(value);if(width>288)paint.setTextSize(72*288/width);Paint.FontMetrics metrics=paint.getFontMetrics();
        canvas.drawText(value,160,80-(metrics.ascent+metrics.descent)/2,paint);save(b,file);
    }
    private void save(Bitmap b,File f)throws Exception {try(FileOutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}}
}
