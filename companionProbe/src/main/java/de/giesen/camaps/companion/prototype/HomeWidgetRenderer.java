// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.graphics.*;
import de.giesen.gva.watch.WatchSnapshot;
import java.util.*;

/** Native drawing for a bounded widget bitmap; no forecast or alarm input. */
final class HomeWidgetRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static Typeface rounded;
    // Midpoint between the last two device-tested weights.
    private static final float CURVE_STROKE=(3.25f+64f*15f/141f)/2;
    /** Render only the numerals locally: launcher RemoteViews cannot load app fonts reliably.
     * Tight bounds and FIT_START preserve the typeface, adjacency and leading alignment
     * across process boundaries without a bitmap sized from unreliable host heights.
     * The parent data region supplies the complete accessible text and click target. */
    static Bitmap numerals(android.content.Context context,String value,String arrow,int color,boolean small) {
        return numerals(context,value,arrow,color,small,320);
    }
    static Bitmap numerals(android.content.Context context,String value,String arrow,int color,boolean small,int widgetWidthDp) {
        Typeface font;
        synchronized(HomeWidgetRenderer.class){if(rounded==null)rounded=context.getResources().getFont(R.font.gva_rounded_widget);font=rounded;}
        Paint digits=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);digits.setTypeface(font);digits.setTextSize(small?128:256);digits.setColor(color);
        Paint trend=new Paint(digits);trend.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));trend.setTextSize(digits.getTextSize()*.85f);
        Rect a=new Rect(),b=new Rect();digits.getTextBounds(value,0,value.length(),a);
        float arrowX=digits.measureText(value)+digits.measureText("\u2009");
        RectF bounds=new RectF(a);Rect zero=new Rect();digits.getTextBounds("0",0,1,zero);
        // A missing-value dash must occupy a numeral's line box; fitting its tiny
        // ink height to the entire view would turn it into a thick bar.
        bounds.top=Math.min(bounds.top,zero.top);bounds.bottom=Math.max(bounds.bottom,zero.bottom);
        if(!arrow.isEmpty()){trend.getTextBounds(arrow,0,arrow.length(),b);bounds.union(arrowX+b.left,b.top,arrowX+b.right,b.bottom);}
        if(!small){
            // A short value must not become larger just because it has fewer
            // digits or a narrower arrow. Reserve one common three-digit box.
            float referenceX=digits.measureText("888")+digits.measureText("\u2009"),referenceRight=referenceX;
            for(String direction:new String[]{"⇈","↑","↗","→","↘","↓","⇊"}){
                trend.getTextBounds(direction,0,direction.length(),b);referenceRight=Math.max(referenceRight,referenceX+b.right);
            }
            bounds.right=Math.max(bounds.right,referenceRight);
        }
        // Reserve trailing space rather than letting FIT_START enlarge the ink
        // to fill the entire reading region. Leading alignment stays unchanged.
        float occupancy=small?.9f:.8f;
        float scale=Math.min(1,Math.min(956f*occupancy/Math.max(1,bounds.width()),380f*occupancy/Math.max(1,bounds.height())));
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(bounds.width()*scale/occupancy)+4),Math.max(1,(int)Math.ceil(bounds.height()*scale/occupancy)+4),Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);bitmap.setHasMipMap(true);
        Canvas canvas=new Canvas(bitmap);canvas.translate(2,2);canvas.scale(scale,scale);canvas.translate(-bounds.left,-bounds.top);
        canvas.drawText(value,0,0,digits);
        if(!arrow.isEmpty()) {
            if(!small) {
                // Keep the existing bitmap frame and numeral scale. Use its spare
                // trailing space for the arrow instead of expanding the reference box.
                float growth=1.3f+.55f*Math.max(0,Math.min(1,(widgetWidthDp-220f)/180f));
                trend.setTextSize(trend.getTextSize()*growth);trend.getTextBounds(arrow,0,arrow.length(),b);
                float availableRight=bounds.left+(bitmap.getWidth()-6)/scale;
                float fit=Math.min(1,Math.min(Math.max(1,availableRight-arrowX)/Math.max(1,b.width()),
                        zero.height()*.95f/Math.max(1,b.height())));
                trend.setTextSize(trend.getTextSize()*fit);trend.getTextBounds(arrow,0,arrow.length(),b);
                canvas.drawText(arrow,arrowX-b.left,zero.exactCenterY()-b.exactCenterY(),trend);
            } else canvas.drawText(arrow,arrowX,0,trend);
        }
        return bitmap;
    }
    static int historyMinutes(float width){float chartWidth=(width-8)/2.25f;return chartWidth>=160?120:chartWidth>=100?60:30;}
    static String historyLabel(int minutes){return minutes==120?"2 h":minutes==60?"1 h":"30 min";}
    Bitmap image(WatchSnapshot s,boolean fresh,boolean dark,int logicalWidth,float density,int minutes,long now) {
        // Launcher size hints can differ from the actual view height. A stable chart
        // coordinate system keeps an underestimated hint from creating a huge stroke.
        int logicalHeight=64;
        float scale=Math.min(Math.max(1,Math.min(4,density)),Math.min(960f/Math.max(1,logicalWidth),256f/logicalHeight));
        Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(logicalWidth*scale)),Math.max(1,Math.round(logicalHeight*scale)),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.scale((float)b.getWidth()/logicalWidth,(float)b.getHeight()/logicalHeight);
        if(s!=null)chart(c,s,now,dark,0,logicalWidth,0,logicalHeight,fresh,minutes);
        return b;
    }
    static Bitmap toggle(boolean enabled,boolean dark) {
        Bitmap b=Bitmap.createBitmap(72,36,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        AppAppearance.Colors colors=new AppAppearance.Colors(dark);p.setColor(enabled?colors.accent:colors.line);
        c.drawRoundRect(new RectF(1,1,71,35),17,17,p);p.setColor(enabled?colors.onAccent:colors.muted);c.drawCircle(enabled?54:18,18,12,p);return b;
    }
    private void chart(Canvas c,WatchSnapshot s,long now,boolean dark,float left,float right,float top,float bottom,boolean fresh,int minutes) {
        long span=minutes*60000L,from=now-span;List<DiagnosticSeries.Sample> points=new ArrayList<>();double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(WatchSnapshot.Point q:s.points)if(q.time>=from&&q.time<=now){points.add(new DiagnosticSeries.Sample(q.time,q.value));lo=Math.min(lo,q.value);hi=Math.max(hi,q.value);}
        if(points.isEmpty())return;
        if(s.low!=null){lo=Math.min(lo,s.low);hi=Math.max(hi,s.low);}if(s.high!=null){lo=Math.min(lo,s.high);hi=Math.max(hi,s.high);}
        double padding=Math.max(10,(hi-lo)*.15);lo=Math.max(0,lo-padding);hi+=padding;
        c.save();Path clip=new Path();clip.addRoundRect(new RectF(left,top,right,bottom),12,12,Path.Direction.CW);c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);p.setColor(dark?0xff183a2d:0xffe1f2e9);c.drawRect(left,top,right,bottom,p);
        if(s.high!=null){p.setColor(dark?0xff44351d:0xfffff0d6);c.drawRect(left,top,right,y(s.high,lo,hi,top,bottom),p);}
        if(s.low!=null){p.setColor(dark?0xff482328:0xffffe5e5);c.drawRect(left,y(s.low,lo,hi,top,bottom),right,bottom,p);}
        List<DiagnosticSeries.Sample> segment=new ArrayList<>();
        for(DiagnosticSeries.Sample q:points){if(!segment.isEmpty()&&q.time-segment.get(segment.size()-1).time>WatchSnapshot.GAP){segment(c,segment,s,from,span,lo,hi,left,right,top,bottom,dark,fresh);segment.clear();}segment.add(q);}
        segment(c,segment,s,from,span,lo,hi,left,right,top,bottom,dark,fresh);c.restore();
    }
    private void segment(Canvas c,List<DiagnosticSeries.Sample> points,WatchSnapshot s,long from,long span,double lo,double hi,float left,float right,float top,float bottom,boolean dark,boolean fresh){
        if(points.isEmpty())return;Path path=new Path();DiagnosticSeries.Sample first=points.get(0);path.moveTo(x(first.time,from,span,left,right),y(first.value,lo,hi,top,bottom));
        for(double[] a:ChartCurve.controls(points))path.cubicTo(x(a[0],from,span,left,right),y(a[1],lo,hi,top,bottom),x(a[2],from,span,left,right),y(a[3],lo,hi,top,bottom),x(a[4],from,span,left,right),y(a[5],lo,hi,top,bottom));
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(CURVE_STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(fresh?(dark?0xffd4f6e7:0xff156747):0xff8b9993);c.drawPath(path,p);
        if(fresh&&s.high!=null){c.save();c.clipRect(left,top,right,y(s.high,lo,hi,top,bottom));p.setColor(dark?0xffffd28c:0xff936000);c.drawPath(path,p);c.restore();}
        if(fresh&&s.low!=null){c.save();c.clipRect(left,y(s.low,lo,hi,top,bottom),right,bottom);p.setColor(dark?0xffff5c5c:0xffc02737);c.drawPath(path,p);c.restore();}
        p.setStyle(Paint.Style.FILL);if(points.size()==1){p.setColor(!fresh?0xff8b9993:s.low!=null&&first.value<s.low?(dark?0xffff5c5c:0xffc02737):s.high!=null&&first.value>s.high?(dark?0xffffd28c:0xff936000):(dark?0xffd4f6e7:0xff156747));c.drawCircle(x(first.time,from,span,left,right),y(first.value,lo,hi,top,bottom),CURVE_STROKE/2,p);}
    }
    private float x(double time,long from,long span,float left,float right){return (float)(left+(right-left)*(time-from)/span);}
    private float y(double value,double lo,double hi,float top,float bottom){return (float)(bottom-(bottom-top)*(value-lo)/(hi-lo));}
}
