// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.graphics.*;
import android.provider.Settings;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchPlatformTest {
    android.app.Application c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("watch_safety_v1",0).edit().clear().commit();WatchStore.clear(c);Settings.Global.putInt(c.getContentResolver(),"boot_count",1);}
    @Test public void transportCannotGrantWatchConsent()throws Exception{assertFalse(WatchStore.accept(c,WatchRenderer.demo(System.currentTimeMillis()).encode()));assertNull(WatchStore.read(c));}
    @Test public void firstAcknowledgmentDoesNotUnlockData(){WatchConsent.accept(c,1);assertFalse(WatchConsent.ready(c));}
    @Test public void olderPacketCannotOverwriteCurrentPacket()throws Exception{WatchConsent.accept(c,1);WatchConsent.accept(c,2);long now=System.currentTimeMillis();assertTrue(WatchStore.accept(c,WatchRenderer.demo(now).encode()));assertFalse(WatchStore.accept(c,WatchRenderer.demo(now-1).encode()));}
    @Test public void rebootCannotRenewCachedFreshness()throws Exception{WatchConsent.accept(c,1);WatchConsent.accept(c,2);WatchStore.accept(c,WatchRenderer.demo(System.currentTimeMillis()).encode());Settings.Global.putInt(c.getContentResolver(),"boot_count",2);assertFalse(WatchStore.read(c).fresh(c,System.currentTimeMillis()));}
    @Test public void cacheClearDoesNotAlterLegalAcknowledgments()throws Exception{WatchConsent.accept(c,1);WatchConsent.accept(c,2);WatchStore.accept(c,WatchRenderer.demo(System.currentTimeMillis()).encode());WatchStore.clear(c);assertNull(WatchStore.read(c));assertTrue(WatchConsent.ready(c));}
    @Test public void renderActiveAmbientAndStale()throws Exception{
        long now=1800000000000L;WatchSnapshot s=WatchRenderer.demo(now);WatchRenderer renderer=new WatchRenderer(c);
        File folder=new File(System.getProperty("gva.watch.renders"));assertTrue(folder.isDirectory()||folder.mkdirs());
        for(String mode:new String[]{"active","ambient","stale","no-data","two-hours","ambient-two-hours","long-delta","zero-age"}){
            boolean ambient=mode.startsWith("ambient"),fresh=!mode.equals("stale")&&!mode.equals("no-data");
            int hours=mode.contains("two-hours")?2:1;
            WatchSnapshot view=mode.equals("long-delta")?new WatchSnapshot(s.generated,s.received,s.value,s.trend,true,true,-12.34*18.0182,s.low,s.high,s.points):s;
            Bitmap b=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);
            if(mode.equals("zero-age"))renderer.draw(new Canvas(b),600,view,fresh,ambient,hours,true,false,now,"0");
            else renderer.draw(new Canvas(b),600,mode.equals("no-data")?null:view,fresh,ambient,hours,true,false,now);
            try(FileOutputStream out=new FileOutputStream(new File(folder,mode+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
            if(ambient){assertEquals(Color.BLACK,b.getPixel(300,250));assertNotEquals(Color.BLACK,b.getPixel(300,51));assertEquals(Color.BLACK,b.getPixel(300,550));}
            if(mode.equals("active"))assertNotEquals(Color.BLACK,b.getPixel(300,250));
        }
    }
    @Test public void everyConfirmedRedArrowIsRedInActiveAmbientAndTileButGreyWhenStale()throws Exception {
        long now=1800000000000L;WatchSnapshot d=WatchRenderer.demo(now);WatchRenderer renderer=new WatchRenderer(c);
        File folder=new File(System.getProperty("gva.watch.renders"));assertTrue(folder.isDirectory()||folder.mkdirs());
        for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW})for(WatchSnapshot.Trend trend:WatchSnapshot.Trend.values())if(trend!=WatchSnapshot.Trend.UNKNOWN){
            WatchSnapshot s=new WatchSnapshot(d.generated,d.received,d.value,trend,d.mmol,true,d.rate,d.low,d.high,d.points,color);
            for(boolean ambient:new boolean[]{false,true}){
                Bitmap b=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);renderer.draw(new Canvas(b),600,s,true,ambient,1,true,false,now);
                int red=0;for(int y=100;y<230;y++)for(int x=350;x<478;x++)if(b.getPixel(x,y)==(ambient?color.ambient:color.watch))red++;
                assertTrue(trend.name(),red>150);
                try(FileOutputStream out=new FileOutputStream(new File(folder,color.name().toLowerCase(java.util.Locale.ROOT)+"-"+trend.name()+"-"+(ambient?"ambient":"active")+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
            }
            Bitmap tile=renderer.tileImage(s,true,1,now);int tileRed=0;for(int y=75;y<173;y++)for(int x=262;x<359;x++)if(tile.getPixel(x,y)==color.watch)tileRed++;assertTrue(tileRed>50);
            Bitmap stale=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);renderer.draw(new Canvas(stale),600,s,false,false,1,true,false,now);
            int gray=0;for(int y=100;y<230;y++)for(int x=350;x<478;x++){assertNotEquals(color.watch,stale.getPixel(x,y));if(stale.getPixel(x,y)==0xff899298)gray++;}assertTrue(gray>150);
        }
    }
    @Test public void translatedClockAndCachePreserveRedWithoutRenewingTheReceipt()throws Exception {
        WatchConsent.accept(c,1);WatchConsent.accept(c,2);long now=System.currentTimeMillis();WatchSnapshot d=WatchRenderer.demo(now+3000);
        WatchSnapshot s=new WatchSnapshot(d.generated,d.generated-1000,d.value,d.trend,d.mmol,true,d.rate,d.low,d.high,d.points,ArrowColor.RED);
        assertTrue(WatchStore.accept(c,s.encode()));WatchStore.Entry e=WatchStore.read(c);assertEquals(ArrowColor.RED,e.snapshot.arrowColor);assertEquals(s.trend,e.snapshot.trend);
        assertEquals(1000,e.snapshot.generated-e.snapshot.received);assertEquals(s.generated,e.sourceGenerated);
    }
    @Test public void existingUnitChoiceIsUsedForNumbersAndDelta(){assertEquals("7,0",WatchRenderer.number(126.1274,true,false).replace('.',','));assertEquals("+0,07",WatchRenderer.number(1.2,true,true).replace('.',','));}
}
