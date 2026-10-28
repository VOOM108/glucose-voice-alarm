// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import java.util.ArrayList;
/** One atomic private display cache. Reboot, clock change or age never renews freshness. */
final class WatchStore {
    static final class Entry {
        final WatchSnapshot snapshot;final long sourceGenerated,wall,elapsed;final int boot;
        Entry(WatchSnapshot s,long w,long e,int b){
            sourceGenerated=s.generated;wall=w;elapsed=e;boot=b;
            // Map a bounded ahead-of-watch source clock once to this stored receipt.
            // Source age (generated-received) is preserved; repeated packets stay ordered
            // by the original phone generation timestamp, never by this translation.
            long shift=s.generated>w&&s.generated-w<=WatchSnapshot.FUTURE?w-s.generated:0;
            if(shift==0)snapshot=s;
            else {
                ArrayList<WatchSnapshot.Point> points=new ArrayList<>();
                for(WatchSnapshot.Point p:s.points)points.add(new WatchSnapshot.Point(p.time+shift,p.value));
                snapshot=new WatchSnapshot(s.generated+shift,s.received+shift,s.value,s.trend,s.mmol,s.sourceAvailable,s.rate,s.low,s.high,points);
            }
        }
        boolean clockValid(Context c,long now){
            long current=SystemClock.elapsedRealtime();
            return boot>=0&&boot==boot(c)&&elapsed>=0&&current>=elapsed&&now>=wall
                    &&Math.abs((now-wall)-(current-elapsed))<=WatchSnapshot.FUTURE;
        }
        boolean fresh(Context c,long now){return boot>=0&&boot==boot(c)&&snapshot.fresh(now,wall,elapsed,SystemClock.elapsedRealtime());}
    }
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);}
    static synchronized boolean accept(Context c,byte[] packet)throws java.io.IOException {
        if(!WatchConsent.ready(c))return false;
        long now=System.currentTimeMillis();WatchSnapshot next=WatchSnapshot.decode(packet,now);Entry old=read(c);
        if(old!=null&&next.generated<=old.sourceGenerated)return false;
        return c.getSharedPreferences("watch_display_v1",0).edit().putString("packet",Base64.encodeToString(packet,Base64.NO_WRAP))
                .putLong("wall",now).putLong("elapsed",SystemClock.elapsedRealtime()).putInt("boot",boot(c)).commit();
    }
    static synchronized Entry read(Context c) {
        if(!WatchConsent.ready(c))return null;
        SharedPreferences p=c.getSharedPreferences("watch_display_v1",0);String b=p.getString("packet",null);
        if(b==null||b.length()>6000)return null;
        try{return new Entry(WatchSnapshot.decode(Base64.decode(b,Base64.NO_WRAP),System.currentTimeMillis()),p.getLong("wall",0),p.getLong("elapsed",-1),p.getInt("boot",-2));}
        catch(java.io.IOException|IllegalArgumentException e){return null;}
    }
    static synchronized void clear(Context c){c.getSharedPreferences("watch_display_v1",0).edit().clear().commit();}
    static int hours(Context c){return c.getSharedPreferences("watch_view_v1",0).getInt("hours",1)==2?2:1;}
    static void hours(Context c,int h){c.getSharedPreferences("watch_view_v1",0).edit().putInt("hours",h==2?2:1).commit();}
}
