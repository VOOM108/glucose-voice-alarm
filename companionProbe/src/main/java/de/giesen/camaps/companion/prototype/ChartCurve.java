// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.util.*;
/** Monotone cubic controls for drawing only. Never fed back into the alarm series. */
public final class ChartCurve {
    public static double[][] controls(List<DiagnosticSeries.Sample> points) {
        int n=points.size(); if(n<2) return new double[0][];
        double[] slope=new double[n-1],tangent=new double[n];
        for(int i=0;i<n-1;i++) {
            double span=points.get(i+1).time-points.get(i).time;
            if(span<=0) throw new IllegalArgumentException("Chart times must increase");
            slope[i]=(points.get(i+1).value-points.get(i).value)/span;
        }
        tangent[0]=slope[0]; tangent[n-1]=slope[n-2];
        for(int i=1;i<n-1;i++) tangent[i]=slope[i-1]*slope[i]<=0?0:2/(1/slope[i-1]+1/slope[i]);
        for(int i=0;i<n-1;i++) {
            if(slope[i]==0) { tangent[i]=0; tangent[i+1]=0; continue; }
            double a=tangent[i]/slope[i],b=tangent[i+1]/slope[i],length=Math.hypot(a,b);
            if(length>3) { double scale=3/length; tangent[i]=scale*a*slope[i]; tangent[i+1]=scale*b*slope[i]; }
        }
        double[][] controls=new double[n-1][6];
        for(int i=0;i<n-1;i++) {
            DiagnosticSeries.Sample a=points.get(i),b=points.get(i+1); double third=(b.time-a.time)/3.0;
            controls[i]=new double[]{a.time+third,a.value+tangent[i]*third,b.time-third,b.value-tangent[i+1]*third,b.time,b.value};
        }
        return controls;
    }
    private ChartCurve() {}
}
