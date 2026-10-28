// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.drawable.Icon;
import android.graphics.Bitmap;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import java.time.Instant;
import java.util.Collections;
/** One atomic image for the custom face; short text for other faces. */
public final class DisplayComplication extends ComplicationDataSourceService {
    static void update(Context c){
        ComplicationDataSourceUpdateRequester.create(c,new ComponentName(c,DisplayComplication.class)).requestUpdateAll();
        ComplicationDataSourceUpdateRequester.create(c,new ComponentName(c,AgeComplication.class)).requestUpdateAll();
        for(Class<?> source:new Class<?>[]{ValueComplication.class,TrendComplication.class,DeltaComplication.class,CurveComplication.class})
            ComplicationDataSourceUpdateRequester.create(c,new ComponentName(c,source)).requestUpdateAll();
        androidx.wear.tiles.TileService.getUpdater(c).requestUpdate(WatchTileService.class);
    }
    @Override public void onComplicationRequest(ComplicationRequest request,ComplicationRequestListener listener) {
        WatchStore.Entry entry=WatchStore.read(this);long now=System.currentTimeMillis();
        WatchSnapshot snapshot=entry!=null&&entry.clockValid(this,now)&&now>=entry.snapshot.received&&now-entry.snapshot.received<=WatchSnapshot.WINDOW?entry.snapshot:null;
        boolean fresh=snapshot!=null&&entry.fresh(this,now);
        try {
            if(fresh&&(request.getComplicationType()==ComplicationType.SMALL_IMAGE||request.getComplicationType()==ComplicationType.SHORT_TEXT))listener.onComplicationDataTimeline(timeline(request.getComplicationType(),snapshot));
            else listener.onComplicationData(data(request.getComplicationType(),snapshot,false,false));
        }
        catch(android.os.RemoteException e){/* The requesting face may have closed. */}
    }
    ComplicationDataTimeline timeline(ComplicationType type,WatchSnapshot snapshot) {
        // A stale image is the default, so faces without timeline support fail conservatively.
        ComplicationData stale=data(type,snapshot,false,false),fresh=data(type,snapshot,true,false);
        TimelineEntry active=new TimelineEntry(new TimeInterval(Instant.ofEpochMilli(snapshot.received),
                Instant.ofEpochMilli(snapshot.received+WatchSnapshot.FRESH)),fresh);
        return new ComplicationDataTimeline(stale,Collections.singletonList(active));
    }
    @Override public ComplicationData getPreviewData(ComplicationType type) {return data(type,WatchRenderer.demo(System.currentTimeMillis()),true,true);}
    private ComplicationData data(ComplicationType type,WatchSnapshot s,boolean fresh,boolean preview) {
        PendingIntent tap=PendingIntent.getActivity(this,0,new Intent(this,WatchActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String description=s==null?WatchConsent.text("Kein aktueller Wert. GVA öffnen.","No current reading. Open GVA."):
                (fresh?"":WatchConsent.text("Veraltet. ","Stale. "))+WatchRenderer.number(s.value,s.mmol,false)+" "+(s.mmol?"mmol/l":"mg/dl")+" "+s.trend.name();
        PlainComplicationText desc=new PlainComplicationText.Builder(description).build();
        TimeRange validity=s!=null&&!preview?new TimeRange(Instant.EPOCH,Instant.ofEpochMilli(s.received+WatchSnapshot.WINDOW)):TimeRange.ALWAYS;
        if(type==ComplicationType.SHORT_TEXT) {
            String value=s==null?"—":WatchRenderer.number(s.value,s.mmol,false)+(fresh?symbol(s.trend):" !");
            return new ShortTextComplicationData.Builder(new PlainComplicationText.Builder(value).build(),desc)
                    .setTapAction(tap).setValidTimeRange(validity).build();
        }
        if(type==ComplicationType.SMALL_IMAGE) {
            WatchRenderer renderer=new WatchRenderer(this);
            SmallImage image=new SmallImage.Builder(imageIcon(renderer.image(this,s,fresh,false,WatchStore.hours(this))),SmallImageType.ICON)
                    .setAmbientImage(imageIcon(renderer.image(this,s,fresh,true,WatchStore.hours(this)))).build();
            return new SmallImageComplicationData.Builder(image,desc).setTapAction(tap).setValidTimeRange(validity).build();
        }
        return new NoDataComplicationData();
    }
    private static Icon imageIcon(Bitmap bitmap) {
        // Four active/ambient images travel together in the timeline. Lossless PNG
        // keeps their parcel small without reducing resolution or font outlines.
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("Display encoding failed");
        byte[] png=out.toByteArray();return Icon.createWithData(png,0,png.length);
    }
    static String symbol(WatchSnapshot.Trend t){return new String[]{"⇈","↑","↗","→","↘","↓","⇊",""}[t.ordinal()];}
}
