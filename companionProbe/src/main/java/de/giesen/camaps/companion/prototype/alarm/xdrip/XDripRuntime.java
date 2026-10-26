// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm.xdrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Explicit dependency ports for imported methods. No Android, database, logging or device output. */
public final class XDripRuntime {
    private static final ThreadLocal<Environment> ACTIVE=new ThreadLocal<>();
    public static Environment current() {
        Environment value=ACTIVE.get();
        if (value==null) throw new IllegalStateException("xDrip rule called outside its isolated context");
        return value;
    }
    /** Calls using static imported state must additionally hold XDripRuntime.class's monitor. */
    public static void enter(Environment value) {
        if (!Thread.holdsLock(XDripRuntime.class) || ACTIVE.get()!=null) throw new IllegalStateException("Unisolated core context");
        ACTIVE.set(value);
    }
    public static void leave() { ACTIVE.remove(); }

    public static final class Environment {
        public final long now;
        public final int minuteOfDay;
        /** Source data session boundary supplied by the caller, never an invented physical sensor. */
        public final Long sourceSessionStart;
        public final List<BgReading> ascending;
        public final Map<String,Object> preferences=new HashMap<>();
        public final Map<String,Long> persistentState;
        public boolean persistentRaised, risingRaised, fallingRaised, persistentCancelled;
        public int configurationWarnings;
        public Environment(long now,int minuteOfDay,Long sourceSessionStart,List<BgReading> ascending,Map<String,Long> state) {
            this.now=now; this.minuteOfDay=minuteOfDay; this.sourceSessionStart=sourceSessionStart;
            this.ascending=ascending; persistentState=state;
        }
        List<BgReading> latest(int count) {
            List<BgReading> result=new ArrayList<>();
            for (int index=ascending.size()-1; index>=0 && result.size()<count; index--) result.add(ascending.get(index));
            return result;
        }
        List<BgReading> between(int count,long since,long until) {
            List<BgReading> result=new ArrayList<>();
            for (BgReading point:ascending) if (point.timestamp>=since && point.timestamp<=until && result.size()<count) result.add(point);
            return result;
        }
    }
    public static final class Date { public long getTime() { return current().now; } }
    public static final class Calendar {
        public static final int HOUR_OF_DAY=11,MINUTE=12;
        public static Calendar getInstance() { return new Calendar(); }
        public int get(int field) {
            if (field==HOUR_OF_DAY) return current().minuteOfDay/60;
            if (field==MINUTE) return current().minuteOfDay%60;
            throw new IllegalArgumentException("Unsupported calendar field");
        }
    }
    public static final class Context {
        public String getString(int id) { return id==R.string.space_mins?" Minuten":"Dauerhaft hoch für > "; }
    }
    public static final class SharedPreferences {
        public boolean getBoolean(String key,boolean fallback) { return Pref.getBoolean(key,fallback); }
        public long getLong(String key,long fallback) { return Pref.getLong(key,fallback); }
        public String getString(String key,String fallback) { return Pref.getString(key,fallback); }
    }
    public static final class PreferenceManager {
        public static SharedPreferences getDefaultSharedPreferences(Context ignored) { return new SharedPreferences(); }
    }
    public static final class Pref {
        public static boolean getBooleanDefaultFalse(String key) { return getBoolean(key,false); }
        public static boolean getBoolean(String key,boolean fallback) {
            Object value=current().preferences.get(key); return value instanceof Boolean?(Boolean)value:fallback;
        }
        public static String getString(String key,String fallback) {
            Object value=current().preferences.get(key); return value instanceof String?(String)value:fallback;
        }
        public static long getLong(String key,long fallback) {
            Long state=current().persistentState.get(key);
            if (state!=null) return state;
            Object value=current().preferences.get(key); return value instanceof Long?(Long)value:fallback;
        }
        public static void setLong(String key,long value) { current().persistentState.put(key,value); }
    }
    public static final class Sensor {
        public long started_at;
        public static Sensor currentSensor() {
            if (current().sourceSessionStart==null) return null;
            Sensor result=new Sensor(); result.started_at=current().sourceSessionStart; return result;
        }
    }
    public static final class JoH {
        public static long tsl() { return current().now; }
        public static long msSince(long time) { return tsl()-time; }
        public static double tolerantParseDouble(String input) { return Double.parseDouble(input); }
        public static String qs(double number) { return Double.toString(number); }
        public static String dateTimeText(long ignored) { return "source-time"; }
        public static String niceTimeScalar(long ignored) { return "source-duration"; }
    }
    public static final class Home {
        public static boolean get_follower() { return false; }
        public static double convertToMgDlIfMmol(double mgdl) { return mgdl; }
        public static void toaststaticnext(String ignored) { current().configurationWarnings++; }
    }
    public static final class Constants {
        public static final long MINUTE_IN_MS=60000, DAY_IN_MS=24*60*MINUTE_IN_MS;
    }
    public static final class xdrip { public static Context getAppContext() { return new Context(); } }
    public static final class R { public static final class string { public static final int persistent_high_for_greater_than=1,space_mins=2; } }
    public static final class Notifications {
        public static void persistentHighAlert(Context ignored,boolean active,String unusedLegacyText) {
            current().persistentRaised=active; current().persistentCancelled=!active;
        }
        public static void RisingAlert(Context ignored,boolean active) { current().risingRaised=active; }
        public static void DropAlert(Context ignored,boolean active) { current().fallingRaised=active; }
    }
    /** Original diagnostic calls are discarded; neither source data nor source logs leave this port. */
    public static final class Log {
        public static void d(String tag,String message) {}
        public static void i(String tag,String message) {}
        public static void e(String tag,String message) {}
        public static void e(String tag,String message,Throwable ignored) {}
        public static void wtf(String tag,String message) {}
        public static void ueh(String tag,String message) {}
    }
    private XDripRuntime() {}
}
