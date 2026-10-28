// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import java.time.Instant;
import java.util.Collections;

/** Independent slots use the same accepted snapshot, clock, consent and freshness as the composite. */
abstract class IndividualComplication extends ComplicationDataSourceService {
    enum Part { VALUE,TREND,DELTA,CURVE }
    abstract Part part();
    private boolean supports(ComplicationType type) {
        return part()==Part.CURVE?type==ComplicationType.SMALL_IMAGE:
                type==ComplicationType.SHORT_TEXT||(part()==Part.TREND&&type==ComplicationType.MONOCHROMATIC_IMAGE);
    }
    @Override public void onComplicationRequest(ComplicationRequest request,ComplicationRequestListener listener) {
        WatchStore.Entry entry=WatchStore.read(this);long now=System.currentTimeMillis();
        WatchSnapshot s=entry!=null&&entry.clockValid(this,now)&&now>=entry.snapshot.received&&now-entry.snapshot.received<=WatchSnapshot.WINDOW?entry.snapshot:null;
        try {
            if(supports(request.getComplicationType())&&s!=null&&entry.fresh(this,now)) {
                TimelineEntry active=new TimelineEntry(new TimeInterval(Instant.ofEpochMilli(s.received),Instant.ofEpochMilli(s.received+WatchSnapshot.FRESH)),data(request.getComplicationType(),s,true,false,now));
                listener.onComplicationDataTimeline(new ComplicationDataTimeline(data(request.getComplicationType(),s,false,false,now),Collections.singletonList(active)));
            } else listener.onComplicationData(data(request.getComplicationType(),s,false,false,now));
        }catch(android.os.RemoteException e){/* Requesting face closed. Never log display data. */}
    }
    @Override public ComplicationData getPreviewData(ComplicationType type) {
        long now=1800000000000L;return data(type,WatchRenderer.demo(now),true,true,now);
    }
    ComplicationData data(ComplicationType type,WatchSnapshot s,boolean fresh,boolean preview,long now) {
        if(!supports(type))return new NoDataComplicationData();
        int hours=preview?1:WatchStore.hours(this);
        String unit=s!=null&&s.mmol?"mmol/l":"mg/dl";
        String description=part()==Part.VALUE?WatchConsent.text("Glukosewert","Glucose reading"):
                part()==Part.TREND?WatchConsent.text("CamAPS-Trend","CamAPS trend"):
                part()==Part.DELTA?WatchConsent.text("Änderung pro Minute","Change per minute"):WatchConsent.text("Glukoseverlauf","Glucose history");
        String value="—";
        if(s!=null) {
            if(part()==Part.VALUE)value=WatchRenderer.number(s.value,s.mmol,false)+(fresh?"":" !");
            else if(part()==Part.TREND&&fresh&&s.trend!=WatchSnapshot.Trend.UNKNOWN)value=DisplayComplication.symbol(s.trend);
            else if(part()==Part.DELTA&&fresh&&s.rate!=null)value=WatchRenderer.number(s.rate,s.mmol,true);
            description+=(fresh?". ":WatchConsent.text(". Veraltet. ",". Stale. "));
            if(part()==Part.VALUE)description+=value+" "+unit;
            else if(part()==Part.TREND)description+=trendDescription(s.trend);
            else if(part()==Part.DELTA)description+=value+" "+unit+WatchConsent.text(" pro Minute"," per minute");
            else description+=hours+(hours==1?WatchConsent.text(" Stunde"," hour"):WatchConsent.text(" Stunden"," hours"));
        } else description+=WatchConsent.text(". Keine Daten.",". No data.");
        PlainComplicationText desc=new PlainComplicationText.Builder(description).build();
        TimeRange validity=s!=null&&!preview?new TimeRange(Instant.EPOCH,Instant.ofEpochMilli(s.received+WatchSnapshot.WINDOW)):TimeRange.ALWAYS;
        PendingIntent tap=PendingIntent.getActivity(this,0,new Intent(this,WatchActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(type==ComplicationType.SHORT_TEXT)
            return new ShortTextComplicationData.Builder(new PlainComplicationText.Builder(value).build(),desc).setTapAction(tap).setValidTimeRange(validity).build();
        if(s==null||(part()==Part.TREND&&(!fresh||s.trend==WatchSnapshot.Trend.UNKNOWN)))return new NoDataComplicationData();
        WatchRenderer renderer=new WatchRenderer(this);
        if(type==ComplicationType.MONOCHROMATIC_IMAGE) {
            MonochromaticImage image=new MonochromaticImage.Builder(icon(renderer.trendImage(s.trend,false))).setAmbientImage(icon(renderer.trendImage(s.trend,true))).build();
            return new MonochromaticImageComplicationData.Builder(image,desc).setTapAction(tap).setValidTimeRange(validity).build();
        }
        SmallImage image=new SmallImage.Builder(icon(renderer.curveImage(s,fresh,false,hours,now)),SmallImageType.ICON)
                .setAmbientImage(icon(renderer.curveImage(s,fresh,true,hours,now))).build();
        return new SmallImageComplicationData.Builder(image,desc).setTapAction(tap).setValidTimeRange(validity).build();
    }
    private static Icon icon(Bitmap bitmap) {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("Complication encoding failed");
        byte[] png=out.toByteArray();return Icon.createWithData(png,0,png.length);
    }
    private static String trendDescription(WatchSnapshot.Trend trend) {
        String[] de={"Sehr schnell steigend","Schnell steigend","Steigend","Stabil","Fallend","Schnell fallend","Sehr schnell fallend","Unbekannt"};
        String[] en={"Very quickly rising","Quickly rising","Rising","Stable","Falling","Quickly falling","Very quickly falling","Unknown"};
        return (WatchConsent.de()?de:en)[trend.ordinal()];
    }
}
