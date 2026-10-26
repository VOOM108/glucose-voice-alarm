// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;
import de.giesen.camaps.companion.prototype.alarm.*;

final class HistoryChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private DiagnosticSeries series;
    private ChartBands bands=ChartBands.from(Collections.emptyList());
    HistoryChartView(Context context) { super(context); setBackgroundColor(AppAppearance.colors(context).surface); }
    void setAlarmLevels(List<AlarmLevel> levels) { bands=ChartBands.from(levels); invalidate(); }
    void setSeries(DiagnosticSeries series) {
        this.series=series; setContentDescription(AppText.tr("Diagnosekurve der letzten sechs Stunden, ")+series.rawPoints+AppText.tr(" empfangene Werte, ")+series.gaps.size()+AppText.tr(" Unterbrechungen. Keine Sensor-Messzeitachse.")); invalidate();
    }
    private float dp(float value) { return value*getResources().getDisplayMetrics().density; }
    @Override protected void onMeasure(int width,int height) { setMeasuredDimension(MeasureSpec.getSize(width),resolveSize((int)dp(320),height)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (series==null) return;
        float left=dp(42),top=dp(32),right=getWidth()-dp(24),bottom=getHeight()-dp(30);
        if (right<=left || bottom<=top) return;
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for (List<DiagnosticSeries.Sample> segment:series.segments) for (DiagnosticSeries.Sample point:segment) { low=Math.min(low,point.value); high=Math.max(high,point.value); }
        if(Double.isFinite(low)) {
            for(double limit:bands.lows) { low=Math.min(low,limit); high=Math.max(high,limit); }
            for(double limit:bands.highs) { low=Math.min(low,limit); high=Math.max(high,limit); }
        }
        if (!Double.isFinite(low)) { low=0; high=100; }
        double padding=Math.max(10,(high-low)*0.12); low=Math.max(0,Math.floor((low-padding)/10)*10); high=Math.ceil((high+padding)/10)*10;
        if (high<=low) high=low+20;
        AppAppearance.Colors colors=AppAppearance.colors(getContext());
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(dp(10)); paint.setColor(colors.muted); paint.setTextAlign(Paint.Align.RIGHT); canvas.drawText("mg/dl",left-dp(7),dp(12),paint);
        if(!bands.lows.isEmpty()) {
            float limit=y(bands.lows.get(bands.lows.size()-1),low,high,top,bottom); paint.setColor(AppAppearance.dark(getContext())?0x28e78e9d:0x16d85b72); canvas.drawRect(left,Math.max(top,limit),right,bottom,paint);
        }
        if(!bands.highs.isEmpty()) {
            float limit=y(bands.highs.get(0),low,high,top,bottom); paint.setColor(AppAppearance.dark(getContext())?0x28dfb26c:0x19d09a3c); canvas.drawRect(left,top,right,Math.min(bottom,limit),paint);
        }
        for (int i=0;i<=4;i++) {
            float y=bottom-(bottom-top)*i/4;
            paint.setColor(colors.line); paint.setStrokeWidth(dp(.7f)); canvas.drawLine(left,y,right,y,paint);
            paint.setColor(colors.muted); canvas.drawText(Integer.toString((int)Math.round(low+(high-low)*i/4)),left-dp(7),y+dp(3),paint);
        }
        long span=series.to-series.from; if (span<=0) return;
        SimpleDateFormat format=new SimpleDateFormat("HH:mm",Locale.GERMANY); paint.setTextAlign(Paint.Align.CENTER);
        for (int i=0;i<=6;i++) {
            long time=series.from+span*i/6; float x=left+(right-left)*i/6;
            paint.setColor(colors.line); canvas.drawLine(x,top,x,bottom,paint);
            if(i%2==0) { paint.setColor(colors.muted); canvas.drawText(format.format(new Date(time)),x,bottom+dp(20),paint); }
        }
        paint.setStyle(Paint.Style.STROKE); paint.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(4)},0));
        for(double limit:bands.lows) { paint.setColor(AppAppearance.dark(getContext())?0xffc98594:0xffba6b7c); float y=y(limit,low,high,top,bottom); if(y>=top && y<=bottom) canvas.drawLine(left,y,right,y,paint); }
        for(double limit:bands.highs) { paint.setColor(AppAppearance.dark(getContext())?0xffbba071:0xffad8b50); float y=y(limit,low,high,top,bottom); if(y>=top && y<=bottom) canvas.drawLine(left,y,right,y,paint); }
        for (DiagnosticSeries.Gap gap:series.gaps) {
            float start=left+(right-left)*(gap.from-series.from)/span,end=left+(right-left)*(gap.to-series.from)/span;
            paint.setColor(colors.muted); paint.setAlpha(110); canvas.drawLine(start,top,start,bottom,paint); canvas.drawLine(end,top,end,bottom,paint); paint.setAlpha(255);
        }
        paint.setPathEffect(null); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        for (List<DiagnosticSeries.Sample> segment:series.segments) {
            if(segment.isEmpty()) continue;
            Path path=new Path(); DiagnosticSeries.Sample first=segment.get(0);
            path.moveTo(x(first.time,series.from,span,left,right),y(first.value,low,high,top,bottom));
            for(double[] c:ChartCurve.controls(segment)) path.cubicTo(x(c[0],series.from,span,left,right),y(c[1],low,high,top,bottom),x(c[2],series.from,span,left,right),y(c[3],low,high,top,bottom),x(c[4],series.from,span,left,right),y(c[5],low,high,top,bottom));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2.2f)); paint.setColor(colors.accent); canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
            if(segment.size()==1) canvas.drawCircle(x(first.time,series.from,span,left,right),y(first.value,low,high,top,bottom),dp(2.4f),paint);
        }
    }
    private float x(double time,long from,long span,float left,float right) { return (float)(left+(right-left)*(time-from)/span); }
    private float y(double value,double low,double high,float top,float bottom) { return (float)(bottom-(bottom-top)*(value-low)/(high-low)); }
}
