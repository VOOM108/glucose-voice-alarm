// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.wear.protolayout.*;
import androidx.wear.tiles.TileBuilders;
import java.io.ByteArrayOutputStream;
import static androidx.wear.protolayout.DimensionBuilders.dp;

/** Bounded elapsed-time timeline, never predicted glucose or minute background jobs. */
final class WatchTileLayout {
    static final long MINUTE=60000L;
    static TileBuilders.Tile build(Context c,ProtoLayoutScope scope,float width,float height,WatchStore.Entry entry,long now) {
        // A directly invoked tile must observe the same consent gate as every other entry point.
        if(!WatchConsent.ready(c))entry=null;
        float size=Math.max(100,Math.min(500,Math.min(width,height)));
        WatchRenderer renderer=new WatchRenderer(c);int hours=WatchStore.hours(c);
        TimelineBuilders.Timeline.Builder timeline=new TimelineBuilders.Timeline.Builder();
        ResourceBuilders.ImageResource empty=resource(renderer.tileImage(null,false,hours,now));
        ResourceBuilders.ImageResource unknown=resource(renderer.tileAge("—"));
        timeline.addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
            .setLayout(layout(c,scope,size,empty,unknown,WatchConsent.text("Kein aktueller Wert. GVA öffnen.","No current reading. Open GVA."))).build());
        if(entry!=null&&entry.clockValid(c,now)&&now>=entry.snapshot.received&&now-entry.snapshot.received<=WatchSnapshot.WINDOW) {
            WatchSnapshot s=entry.snapshot;
            boolean fresh=entry.fresh(c,now);
            ResourceBuilders.ImageResource active=fresh?resource(renderer.tileImage(s,true,hours,now)):null;
            ResourceBuilders.ImageResource stale=resource(renderer.tileImage(s,false,hours,now));
            int first=(int)((now-s.received)/MINUTE);
            for(int age=first;age<=120;age++) {
                long start=s.received+age*MINUTE,end=start+MINUTE;
                boolean current=fresh&&age*MINUTE<WatchSnapshot.FRESH;
                String description=(current?"":WatchConsent.text("Veraltet. ","Stale. "))+
                    WatchRenderer.number(s.value,s.mmol,false)+" "+(s.mmol?"mmol/l":"mg/dl")+". "+
                    WatchConsent.text("Alter in Minuten: ","Age in minutes: ")+age+". "+
                    WatchConsent.text("Verlauf in Stunden: ","History in hours: ")+hours;
                timeline.addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(new TimelineBuilders.TimeInterval.Builder().setStartMillis(start).setEndMillis(end).build())
                    .setLayout(layout(c,scope,size,current?active:stale,resource(renderer.tileAge(Integer.toString(age))),description)).build());
            }
        }
        return new TileBuilders.Tile.Builder().setTileTimeline(timeline.build()).setFreshnessIntervalMillis(5*MINUTE).build();
    }
    static ResourceBuilders.ImageResource resource(Bitmap bitmap) {
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("Tile render failed");
        bitmap.recycle();
        return new ResourceBuilders.ImageResource.Builder().setInlineResource(new ResourceBuilders.InlineImageResource.Builder()
            .setWidthPx(width).setHeightPx(height).setData(out.toByteArray()).build()).build();
    }
    private static LayoutElementBuilders.Layout layout(Context c,ProtoLayoutScope scope,float size,ResourceBuilders.ImageResource frame,ResourceBuilders.ImageResource age,String description) {
        LayoutElementBuilders.Image body=new LayoutElementBuilders.Image.Builder(scope).setImageResource(frame)
            .setWidth(dp(size)).setHeight(dp(size)).setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT).build();
        LayoutElementBuilders.Image number=new LayoutElementBuilders.Image.Builder(scope).setImageResource(age)
            .setWidth(dp(size*176/600)).setHeight(dp(size*112/600)).setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_FIT).build();
        LayoutElementBuilders.Box ageLayer=new LayoutElementBuilders.Box.Builder().setWidth(dp(size)).setHeight(dp(size))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT).setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
            .setModifiers(new ModifiersBuilders.Modifiers.Builder().setPadding(new ModifiersBuilders.Padding.Builder()
                .setStart(dp(size*119/600)).setTop(dp(size*394/600)).setRtlAware(false).build()).build())
            .addContent(number).build();
        ActionBuilders.AndroidActivity activity=new ActionBuilders.AndroidActivity.Builder().setPackageName(c.getPackageName()).setClassName(WatchActivity.class.getName()).build();
        ModifiersBuilders.Modifiers modifiers=new ModifiersBuilders.Modifiers.Builder()
            .setClickable(new ModifiersBuilders.Clickable.Builder().setId("gva-open").setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build()).build())
            .setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(description).build()).build();
        return new LayoutElementBuilders.Layout.Builder().setRoot(new LayoutElementBuilders.Box.Builder()
            .setWidth(dp(size)).setHeight(dp(size)).setModifiers(modifiers).addContent(body).addContent(ageLayer).build()).build();
    }
}
