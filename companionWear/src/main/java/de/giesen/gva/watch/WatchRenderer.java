// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.content.Context;
import android.graphics.*;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Display-only round view; the WFF face adds its native minute-age text separately. */
public final class WatchRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface largeNumbers,smallNumbers;
    public WatchRenderer(Context c){
        Typeface base=c.getResources().getFont(R.font.roboto_flex);
        largeNumbers=variant(base,72,850);smallNumbers=c.getResources().getFont(R.font.roboto_flex_footer);
    }
    private static Typeface variant(Typeface base,int opticalSize,int weight){
        Paint font=new Paint();font.setTypeface(base);
        font.setFontVariationSettings("'wght' "+weight+", 'wdth' 100, 'opsz' "+opticalSize);
        return font.getTypeface();
    }
    public Bitmap image(Context c,WatchSnapshot s,boolean fresh,boolean ambient,int hours) {
        // Two 320px images remain below the Binder transaction budget together.
        Bitmap b=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
        draw(new Canvas(b),320,s,fresh,ambient,hours,false,false,System.currentTimeMillis());return b;
    }
    Bitmap tileImage(WatchSnapshot s,boolean fresh,int hours,long now) {
        Bitmap b=Bitmap.createBitmap(450,450,Bitmap.Config.ARGB_8888);
        draw(new Canvas(b),450,s,fresh,false,hours,false,false,now);return b;
    }
    Bitmap tileAge(String age) {
        Bitmap b=Bitmap.createBitmap(176,112,Bitmap.Config.ARGB_8888);
        text(new Canvas(b),88,82,age,72,0xfff4f6f7,175);return b;
    }
    /** Standalone landscape chart. Reuses the existing curve and gap rules, with no labels or readings. */
    Bitmap curveImage(WatchSnapshot s,boolean fresh,boolean ambient,int hours,long now) {
        Bitmap b=Bitmap.createBitmap(320,120,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.drawColor(Color.BLACK);
        c.scale(320f/440,120f/165);c.translate(-80,-233);
        if(s!=null)chart(c,s,now,hours,ambient||!fresh);
        return b;
    }
    Bitmap trendImage(WatchSnapshot.Trend trend,boolean ambient) {
        Bitmap b=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.translate(64-414,64-165);
        arrow(c,trend,ambient?0xffd5dadc:Color.WHITE);return b;
    }
    public void draw(Canvas c,int size,WatchSnapshot s,boolean fresh,boolean ambient,int hours,boolean clock,boolean demo,long now) {
        draw(c,size,s,fresh,ambient,hours,clock,demo,now,age(s,now));
    }
    public void draw(Canvas c,int size,WatchSnapshot s,boolean fresh,boolean ambient,int hours,boolean clock,boolean demo,long now,String age) {
        c.save();c.scale(size/600f,size/600f);c.drawColor(Color.BLACK);
        int white=ambient?0xffd5dadc:0xfff4f6f7,muted=ambient?0xff778087:0xff899298;
        text(c,245,213,s==null?"—":number(s.value,s.mmol,false),112,fresh?white:muted,245);
        if(s!=null)arrow(c,s.trend,fresh?(s.arrowColor.colored()?(ambient?s.arrowColor.ambient:s.arrowColor.watch):(ambient?0xffdddddd:Color.WHITE)):muted);
        if(s!=null)chart(c,s,now,hours,ambient);
        historyIndicator(c,hours,ambient);
        String delta=s==null||!fresh||s.rate==null?"—":number(s.rate,s.mmol,true);
        if(clock)text(c,207,476,age,72,white,175);
        text(c,394,476,delta,72,fresh?white:muted,150);
        if(clock)clock(c,now,ambient);
        if(demo)text(c,300,530,"DEMO",23,0xffe0b56b,120);
        c.restore();
    }
    static String age(WatchSnapshot s,long now) {
        return s==null||now<s.received?"—":Long.toString((now-s.received)/60000L);
    }
    private void historyIndicator(Canvas c,int hours,boolean ambient) {
        p.setStyle(Paint.Style.FILL);p.setColor(ambient?0xffaab3b8:0xffcbd2d6);
        if(hours==2){c.drawCircle(286,396,10,p);c.drawCircle(314,396,10,p);}
        else c.drawCircle(300,396,10,p);
    }
    static String number(double value,boolean mmol,boolean rate) {
        double n=mmol?value/18.0182:value;
        String result=BigDecimal.valueOf(rate?Math.abs(n):n).setScale(mmol?(rate?2:1):(rate?1:0),RoundingMode.HALF_UP).toPlainString();
        if(WatchConsent.de())result=result.replace('.',',');
        return rate?(n<0?"−":"+")+result:result;
    }
    private void text(Canvas c,float x,float y,String value,float size,int color,float maxWidth) {
        p.setTypeface(size>=80?largeNumbers:smallNumbers);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);p.setStyle(Paint.Style.FILL);p.setColor(color);
        float width=p.measureText(value);if(width>maxWidth)p.setTextSize(size*maxWidth/width);
        c.drawText(value,x,y,p);
    }
    private void arrow(Canvas c,WatchSnapshot.Trend trend,int color) {
        if(trend==WatchSnapshot.Trend.UNKNOWN)return;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(11);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);
        c.save();c.translate(414,165);
        int angle=trend==WatchSnapshot.Trend.FLAT?90:trend==WatchSnapshot.Trend.UP_RIGHT?45:
                trend==WatchSnapshot.Trend.DOWN_RIGHT?135:trend==WatchSnapshot.Trend.DOWN||trend==WatchSnapshot.Trend.DOUBLE_DOWN?180:0;
        c.rotate(angle);
        boolean twice=trend==WatchSnapshot.Trend.DOUBLE_UP||trend==WatchSnapshot.Trend.DOUBLE_DOWN;
        for(int i=0;i<(twice?2:1);i++) {
            float x=twice?(i==0?-20:20):0;
            c.drawLine(x,35,x,-35,p);c.drawLine(x-22,-13,x,-35,p);c.drawLine(x+22,-13,x,-35,p);
        }
        c.restore();p.setStyle(Paint.Style.FILL);
    }
    private void chart(Canvas c,WatchSnapshot s,long now,int hours,boolean ambient) {
        final float left=92,right=508,top=245,bottom=386;
        final long from=now-(hours==2?120:60)*60000L,span=now-from;
        List<WatchSnapshot.Point> points=new ArrayList<>();
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(WatchSnapshot.Point pt:s.points)if(pt.time>=from&&pt.time<=now){points.add(pt);lo=Math.min(lo,pt.value);hi=Math.max(hi,pt.value);}
        if(points.isEmpty())return;
        if(s.low!=null){lo=Math.min(lo,s.low);hi=Math.max(hi,s.low);}
        if(s.high!=null){lo=Math.min(lo,s.high);hi=Math.max(hi,s.high);}
        double padding=Math.max(10,(hi-lo)*.15);lo=Math.max(0,lo-padding);hi+=padding;
        c.save();Path clip=new Path();clip.addCircle(300,300,230,Path.Direction.CW);c.clipPath(clip);c.clipRect(0,top,600,bottom);
        p.setStyle(Paint.Style.FILL);
        if(!ambient) {
            p.setColor(0xff11271e);c.drawRect(0,top,600,bottom,p);
            if(s.high!=null){p.setColor(0xff30230f);c.drawRect(0,top,600,Math.max(top,Math.min(bottom,y(s.high,lo,hi))),p);}
            if(s.low!=null){p.setColor(0xff32151d);c.drawRect(0,Math.max(top,Math.min(bottom,y(s.low,lo,hi))),600,bottom,p);}
        }
        List<WatchSnapshot.Point> segment=new ArrayList<>();
        for(WatchSnapshot.Point point:points) {
            if(!segment.isEmpty()&&point.time-segment.get(segment.size()-1).time>WatchSnapshot.GAP) {
                segment(c,segment,from,span,lo,hi,ambient,s.low,s.high);segment.clear();
            }
            segment.add(point);
        }
        segment(c,segment,from,span,lo,hi,ambient,s.low,s.high);
        WatchSnapshot.Point last=points.get(points.size()-1);p.setStyle(Paint.Style.FILL);p.setColor(curveColor(last.value,s.low,s.high,ambient));
        c.drawCircle(x(last.time,from,span),y(last.value,lo,hi),8,p);c.restore();
    }
    private void segment(Canvas c,List<WatchSnapshot.Point> a,long from,long span,double lo,double hi,boolean ambient,Double low,Double high) {
        if(a.isEmpty())return;
        Path path=new Path();path.moveTo(x(a.get(0).time,from,span),y(a.get(0).value,lo,hi));
        int n=a.size();double[] tangent=new double[n],slope=new double[Math.max(0,n-1)];
        for(int i=0;i<n-1;i++)slope[i]=(a.get(i+1).value-a.get(i).value)/(a.get(i+1).time-a.get(i).time);
        if(n>1){tangent[0]=slope[0];tangent[n-1]=slope[n-2];}
        for(int i=1;i<n-1;i++)tangent[i]=slope[i-1]*slope[i]<=0?0:2/(1/slope[i-1]+1/slope[i]);
        for(int i=0;i<n-1;i++) {
            if(slope[i]==0){tangent[i]=tangent[i+1]=0;continue;}
            double length=Math.hypot(tangent[i]/slope[i],tangent[i+1]/slope[i]);
            if(length>3){double f=3/length;tangent[i]*=f;tangent[i+1]*=f;}
        }
        for(int i=0;i<n-1;i++) {
            WatchSnapshot.Point u=a.get(i),v=a.get(i+1);double third=(v.time-u.time)/3.;
            path.cubicTo(x(u.time+third,from,span),y(u.value+tangent[i]*third,lo,hi),
                    x(v.time-third,from,span),y(v.value-tangent[i+1]*third,lo,hi),x(v.time,from,span),y(v.value,lo,hi));
        }
        p.setColor(ambient?0xffaac5b9:0xffd4f6e7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(15);p.setStrokeCap(Paint.Cap.ROUND);
        c.drawPath(path,p);
        if(high!=null){c.save();c.clipRect(0,0,600,y(high,lo,hi));p.setColor(ambient?0xffcca870:0xffffd28c);c.drawPath(path,p);c.restore();}
        if(low!=null){c.save();c.clipRect(0,y(low,lo,hi),600,600);p.setColor(ambient?0xffe65353:0xffff5c5c);c.drawPath(path,p);c.restore();}
        p.setStyle(Paint.Style.FILL);
        if(n==1){p.setColor(curveColor(a.get(0).value,low,high,ambient));c.drawCircle(x(a.get(0).time,from,span),y(a.get(0).value,lo,hi),7.5f,p);}
    }
    private int curveColor(double value,Double low,Double high,boolean ambient) {
        if(high!=null&&value>high)return ambient?0xffcca870:0xffffd28c;
        if(low!=null&&value<low)return ambient?0xffe65353:0xffff5c5c;
        return ambient?0xffaac5b9:0xffd4f6e7;
    }
    private float x(double time,long from,long span){return (float)(92+416*(time-from)/span);}
    private float y(double value,double lo,double hi){return (float)(386-141*(value-lo)/(hi-lo));}
    private void clock(Canvas c,long now,boolean ambient) {
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);
        double hour=(cal.get(Calendar.HOUR)+cal.get(Calendar.MINUTE)/60.)*30,minute=cal.get(Calendar.MINUTE)*6;
        dot(c,hour,24,ambient?0x6e64b5ff:0xff64b5ff);dot(c,minute,17,ambient?0x6effaa55:0xffffaa55);
    }
    private void dot(Canvas c,double degrees,float radius,int color){double a=Math.toRadians(degrees-90);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle((float)(300+249*Math.cos(a)),(float)(300+249*Math.sin(a)),radius,p);}
    static WatchSnapshot demo(long now) {
        List<WatchSnapshot.Point> points=new ArrayList<>();double[] v={112,78,65,81,114,166,190,201,180,142,114,120,126};
        long last=now-60000;
        for(int minute=0;minute<=60;minute++) {
            int i=Math.min(11,minute/5);double t=(minute-i*5)/5.;
            points.add(new WatchSnapshot.Point(last-(60-minute)*60000L,v[i]+(v[i+1]-v[i])*t));
        }
        return new WatchSnapshot(now,last,126,WatchSnapshot.Trend.UP_RIGHT,false,true,1.2,80.,180.,points);
    }
}
