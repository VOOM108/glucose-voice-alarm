// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import androidx.wear.protolayout.*;
import androidx.wear.protolayout.expression.VersionBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.renderer.TileRenderer;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,qualifiers="de-rDE-w225dp-h225dp-round-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchTileTest {
    static final long NOW=1800000000000L;
    Application c;
    @Before public void reset(){
        c=RuntimeEnvironment.getApplication();WatchConsent.accept(c,1);WatchConsent.accept(c,2);
        WatchStore.clear(c);WatchStore.hours(c,1);Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
    }
    private ProtoLayoutScope scope(){return new ProtoLayoutScope(new VersionBuilders.VersionInfo.Builder().setMajor(1).setMinor(500).build());}
    private WatchStore.Entry sample(){
        WatchSnapshot d=WatchRenderer.demo(NOW);
        return new WatchStore.Entry(new WatchSnapshot(NOW,NOW,d.value,d.trend,d.mmol,d.sourceAvailable,d.rate,d.low,d.high,d.points),NOW,SystemClock.elapsedRealtime(),1);
    }
    private TimelineBuilders.TimelineEntry at(TileBuilders.Tile tile,long now){
        TimelineBuilders.TimelineEntry fallback=null;
        for(TimelineBuilders.TimelineEntry e:tile.getTileTimeline().getTimelineEntries()){
            TimelineBuilders.TimeInterval v=e.getValidity();
            if(v==null)fallback=e;
            else if(now>=v.getStartMillis()&&now<v.getEndMillis())return e;
        }
        return fallback;
    }
    private String description(TimelineBuilders.TimelineEntry e){return ((LayoutElementBuilders.Box)e.getLayout().getRoot()).getModifiers().getSemantics().getContentDescription().getValue();}
    @Test public void timelineAgesAndExpiresWithoutInventingNewReadings(){
        ProtoLayoutScope scope=scope();TileBuilders.Tile tile=WatchTileLayout.build(c,scope,225,225,sample(),NOW);
        assertEquals(122,tile.getTileTimeline().getTimelineEntries().size());
        assertTrue(description(at(tile,NOW+59999)).endsWith(": 0. Verlauf in Stunden: 1"));
        assertTrue(description(at(tile,NOW+60000)).endsWith(": 1. Verlauf in Stunden: 1"));
        assertFalse(description(at(tile,NOW+239999)).startsWith("Veraltet"));
        assertTrue(description(at(tile,NOW+240000)).startsWith("Veraltet"));
        assertTrue(description(at(tile,NOW+120*60000)).contains(": 120."));
        assertEquals("Kein aktueller Wert. GVA öffnen.",description(at(tile,NOW+121*60000)));
        int bytes=tile.toProto().getSerializedSize()+scope.collectResources().toProto().getSerializedSize();
        assertTrue("Bounded timeline and image bundle must stay under half a megabyte: "+bytes,bytes<500000);
        assertEquals(300000,tile.getFreshnessIntervalMillis());
    }
    @Test public void consentAndClockFailuresExposeOnlyTheEmptyLayout(){
        WatchStore.Entry entry=sample();
        c.getSharedPreferences("watch_safety_v1",0).edit().clear().commit();
        assertEquals(1,WatchTileLayout.build(c,scope(),225,225,entry,NOW).getTileTimeline().getTimelineEntries().size());
        WatchConsent.accept(c,1);WatchConsent.accept(c,2);Settings.Global.putInt(c.getContentResolver(),"boot_count",2);
        assertEquals(1,WatchTileLayout.build(c,scope(),225,225,entry,NOW).getTileTimeline().getTimelineEntries().size());
        Settings.Global.putInt(c.getContentResolver(),"boot_count",1);
        assertEquals(1,WatchTileLayout.build(c,scope(),225,225,entry,NOW-1).getTileTimeline().getTimelineEntries().size());
    }
    @Test public void reopeningTheTileCannotRenewReceiptAge(){
        WatchStore.Entry e=sample();
        // Move both clocks together; no new packet is accepted.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(3));
        TileBuilders.Tile tile=WatchTileLayout.build(c,scope(),225,225,e,NOW+180000);
        assertTrue(description(at(tile,NOW+180000)).contains(": 3."));
        assertEquals(NOW,e.snapshot.received);
        assertTrue(description(at(tile,NOW+240000)).startsWith("Veraltet"));
    }
    private Bitmap render(TimelineBuilders.TimelineEntry entry,ResourceBuilders.Resources resources)throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host=new FrameLayout(activity);activity.setContentView(host);
        TileRenderer renderer=new TileRenderer.Builder(activity,Runnable::run,state->{}).build();
        com.google.common.util.concurrent.ListenableFuture<View> result=renderer.inflateAsync(entry.getLayout(),resources,host);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertNotNull(result.get(5,TimeUnit.SECONDS));
        host.measure(View.MeasureSpec.makeMeasureSpec(450,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(450,View.MeasureSpec.EXACTLY));
        host.layout(0,0,450,450);host.getViewTreeObserver().dispatchOnPreDraw();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        host.measure(View.MeasureSpec.makeMeasureSpec(450,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(450,View.MeasureSpec.EXACTLY));
        host.layout(0,0,450,450);Bitmap b=Bitmap.createBitmap(450,450,Bitmap.Config.ARGB_8888);host.draw(new Canvas(b));
        activity.finish();return b;
    }
    @Test public void realTileRendererShowsSameLayoutWithoutClockAndAgesInPlace()throws Exception {
        File folder=new File(System.getProperty("gva.watch.renders"));assertTrue(folder.isDirectory()||folder.mkdirs());
        for(String name:new String[]{"tile-one-hour","tile-two-hours","tile-stale","tile-empty"}){
            WatchStore.hours(c,name.equals("tile-two-hours")?2:1);ProtoLayoutScope scope=scope();
            TileBuilders.Tile tile=WatchTileLayout.build(c,scope,225,225,name.equals("tile-empty")?null:sample(),NOW);
            long time=NOW+(name.equals("tile-stale")?300000:name.equals("tile-two-hours")?60000:0);
            Bitmap b=render(at(tile,time),scope.collectResources());
            try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
            int white=0,clock=0;
            for(int y=0;y<450;y++)for(int x=0;x<450;x++){
                int color=b.getPixel(x,y);if(color==0xfff4f6f7&&x>=90&&x<220&&y>=305&&y<380)white++;
                if(color==0xff64b5ff||color==0xffffaa55)clock++;
            }
            assertTrue("Age glyph must be visible in the actual tile renderer",white>30);
            assertEquals("Tile must not draw the time dots",0,clock);
        }
    }
}
