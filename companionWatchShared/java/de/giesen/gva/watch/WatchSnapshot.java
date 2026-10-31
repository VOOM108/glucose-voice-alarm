// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import java.io.*;
import java.util.*;

/** Bounded display-only wire format. Never contains alarm commands or notification text. */
public final class WatchSnapshot {
    public static final String PATH="/gva/display/v1", REQUEST="/gva/request/v1", CLEAR="/gva/clear/v1";
    public static final String CAPABILITIES="/gva/display-capabilities/v1";
    public static final int MAX_POINTS=121, MAX_BYTES=4096;
    public static final long WINDOW=120*60000L, FRESH=4*60000L, GAP=2*60000L, FUTURE=60000L;
    public enum Trend { DOUBLE_UP,UP,UP_RIGHT,FLAT,DOWN_RIGHT,DOWN,DOUBLE_DOWN,UNKNOWN }
    public static final class Point {
        public final long time; public final double value;
        public Point(long time,double value) { this.time=time;this.value=value; }
    }
    public final long generated,received;
    public final double value;
    public final Double rate,low,high;
    public final boolean mmol,sourceAvailable;
    public final ArrowColor arrowColor;
    public final Trend trend;
    public final List<Point> points;
    public WatchSnapshot(long generated,long received,double value,Trend trend,boolean mmol,
                         boolean sourceAvailable,Double rate,Double low,Double high,List<Point> points) {
        this(generated,received,value,trend,mmol,sourceAvailable,rate,low,high,points,ArrowColor.NORMAL);
    }
    public WatchSnapshot(long generated,long received,double value,Trend trend,boolean mmol,
                         boolean sourceAvailable,Double rate,Double low,Double high,List<Point> points,ArrowColor color) {
        if(generated<=0 || received<=0 || received>generated || !positive(value) || trend==null
                || (rate!=null && !Double.isFinite(rate)) || (low!=null && !positive(low))
                || (high!=null && !positive(high)) || (low!=null && high!=null && low>=high)
                || points==null || points.size()>MAX_POINTS)throw new IllegalArgumentException("Invalid display snapshot");
        long previous=0;
        for(Point p:points) {
            if(p==null || p.time<=previous || p.time<Math.max(1,generated-WINDOW) || p.time>generated || !positive(p.value))
                throw new IllegalArgumentException("Invalid display history");
            previous=p.time;
        }
        this.generated=generated;this.received=received;this.value=value;this.trend=trend;this.mmol=mmol;
        this.sourceAvailable=sourceAvailable;this.arrowColor=trend==Trend.UNKNOWN?ArrowColor.NORMAL:Objects.requireNonNull(color);this.rate=rate;this.low=low;this.high=high;
        this.points=Collections.unmodifiableList(new ArrayList<>(points));
    }
    private static boolean positive(double n){return Double.isFinite(n)&&n>0&&n<=Integer.MAX_VALUE;}
    public boolean fresh(long wallNow) {
        return sourceAvailable && wallNow>=received && wallNow>=generated-FUTURE && wallNow-received<=FRESH;
    }
    public boolean fresh(long wallNow,long storedWall,long storedElapsed,long elapsedNow) {
        if(elapsedNow<storedElapsed || storedElapsed<0 || storedWall<received || wallNow<storedWall)return false;
        long elapsed=elapsedNow-storedElapsed,wall=wallNow-storedWall;
        return Math.abs(wall-elapsed)<=FUTURE && elapsed<=FRESH && storedWall-received<=FRESH-elapsed && fresh(wallNow);
    }
    public byte[] encode() throws IOException { return encodeForVersion(2); }
    /** Old peers always receive the unchanged v1 direction-only packet. */
    public byte[] encodeForVersion(int supported) throws IOException {
        if(supported!=1 && supported!=2)throw new IllegalArgumentException("Unsupported display version");
        boolean colour=arrowColor.colored() && supported>=2;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(0x47564157);out.writeInt(colour?2:1);out.writeLong(generated);out.writeLong(received);
            out.writeDouble(value);out.writeByte(trend.ordinal());if(colour)out.writeByte(arrowColor.ordinal());out.writeBoolean(mmol);out.writeBoolean(sourceAvailable);
            optional(out,rate);optional(out,low);optional(out,high);out.writeInt(points.size());
            for(Point p:points){out.writeLong(p.time);out.writeDouble(p.value);}
        }
        if(bytes.size()>MAX_BYTES)throw new IOException("Display packet too large");return bytes.toByteArray();
    }
    private static void optional(DataOutputStream out,Double n)throws IOException {out.writeBoolean(n!=null);if(n!=null)out.writeDouble(n);}
    private static Double optional(DataInputStream in)throws IOException {return in.readBoolean()?in.readDouble():null;}
    public static WatchSnapshot decode(byte[] bytes,long now)throws IOException {
        if(bytes==null || bytes.length>MAX_BYTES)throw new IOException("Invalid display packet size");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=0x47564157)throw new IOException("Unsupported display packet");
            int version=in.readInt();if(version!=1 && version!=2)throw new IOException("Unsupported display packet");
            long generated=in.readLong(),received=in.readLong();double value=in.readDouble();int trend=in.readUnsignedByte();
            int color=version==2?in.readUnsignedByte():0;
            if(color>=ArrowColor.values().length || (version==2 && color==0))throw new IOException("Invalid arrow colour");
            boolean mmol=in.readBoolean(),available=in.readBoolean();Double rate=optional(in),low=optional(in),high=optional(in);
            int count=in.readInt();if(count<0||count>MAX_POINTS||trend>=Trend.values().length)throw new IOException("Invalid display packet");
            List<Point> points=new ArrayList<>();for(int i=0;i<count;i++)points.add(new Point(in.readLong(),in.readDouble()));
            if(in.read()!=-1 || generated>now+FUTURE || generated<Math.max(1,now-WINDOW))throw new IOException("Expired or ambiguous display packet");
            return new WatchSnapshot(generated,received,value,Trend.values()[trend],mmol,available,rate,low,high,points,ArrowColor.values()[color]);
        }catch(IllegalArgumentException e){throw new IOException("Invalid display packet",e);}
    }
    /** Last actual receipt per minute. Long gaps stay empty; never extrapolate. */
    public static List<Point> minutes(List<Point> input,long now) {
        TreeMap<Long,Point> perMinute=new TreeMap<>();
        for(Point p:input)if(p!=null&&p.time>=Math.max(1,now-WINDOW)&&p.time<=now&&positive(p.value)) {
            Point old=perMinute.get(p.time/60000);
            if(old==null||old.time<p.time)perMinute.put(p.time/60000,p);
            else if(old.time==p.time&&old.value!=p.value)throw new IllegalArgumentException("Conflicting receipt time");
        }
        return new ArrayList<>(perMinute.values());
    }
}
