// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;

import android.content.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

/** Presentation storage, separate from speech/output revisions and live alarm state. */
final class DisplayTrendPreferences {
    static final class Snapshot {
        final long revision; final TrendDisplayWords words;
        Snapshot(long revision,TrendDisplayWords words) { this.revision=revision;this.words=words; }
    }
    static Snapshot load(Context c) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            try {
                SharedPreferences prefs=c.getSharedPreferences(AppAppearance.PREFS,0);
                long revision=prefs.getLong("trend_revision",0);if(revision<0)throw new IllegalArgumentException();
                List<String> words=new ArrayList<>();
                for(NotificationParser.Trend trend:TrendDisplayWords.TRENDS)words.add(prefs.getString("trend_"+trend.name(),""));
                List<String> red=new ArrayList<>(),yellow=new ArrayList<>();List<Boolean> r=new ArrayList<>(),y=new ArrayList<>();
                for(NotificationParser.Trend trend:TrendDisplayWords.TRENDS) {
                    red.add(prefs.getString("trend_RED_"+trend.name(),""));r.add(prefs.getBoolean("trend_RED_"+trend.name()+"_enabled",false));
                    yellow.add(prefs.getString("trend_YELLOW_"+trend.name(),""));y.add(prefs.getBoolean("trend_YELLOW_"+trend.name()+"_enabled",false));
                }
                return new Snapshot(revision,new TrendDisplayWords(words,new TrendColorOverrides(red,r,yellow,y)));
            } catch(IllegalArgumentException | ClassCastException e) { throw new IOException(AppText.tr("Anzeigetexte nicht lesbar; gespeicherte Daten bleiben erhalten."),e); }
        }
    }
    static String title(Context c,NotificationParser.Trend trend) {
        try { return load(c).words.title(trend); }
        catch(IOException e) { return MetricNotification.trendTitle(trend); }
    }
    static String title(Context c,NotificationParser.Trend trend,ArrowColor color) {
        try { return load(c).words.title(trend,color); }
        catch(IOException e) { return MetricNotification.trendTitle(trend); }
    }
    static String colorTitle(NotificationParser.Trend trend,ArrowColor color) {
        if(!color.colored() || trend==NotificationParser.Trend.UNKNOWN)return MetricNotification.trendTitle(trend);
        String stem;
        switch(trend) {
            case DOUBLE_UP:stem="Doppelt aufwärts";break;
            case UP:stem="Aufwärts";break;
            case UP_RIGHT:stem="Schräg aufwärts";break;
            case FLAT:stem="Waagerecht";break;
            case DOWN_RIGHT:stem="Schräg abwärts";break;
            case DOWN:stem="Abwärts";break;
            case DOUBLE_DOWN:stem="Doppelt abwärts";break;
            default:return MetricNotification.trendTitle(trend);
        }
        return AppText.tr(stem+(color==ArrowColor.RED?" rot":" gelb"));
    }
    static void save(Context c,long expectedRevision,TrendDisplayWords words) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            SettingsTransaction.requireComplete(c.getFilesDir());
            Snapshot current=load(c);
            if(current.revision!=expectedRevision)throw new IOException(AppText.tr("Anzeigetexte wurden inzwischen geändert. Bitte neu öffnen."));
            commit(c,null,words,current.revision);
        }
    }
    static void applyProfile(Context c,String mode,TrendDisplayWords words) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            // A validated profile may replace unreadable presentation preferences as well.
            long revision;
            try { revision=c.getSharedPreferences(AppAppearance.PREFS,0).getLong("trend_revision",0); }
            catch(ClassCastException e) { revision=0; }
            commit(c,mode,words,Math.max(0,revision));
        }
    }
    private static void commit(Context c,String mode,TrendDisplayWords words,long revision) throws IOException {
        Objects.requireNonNull(words);
        if(revision==Long.MAX_VALUE)throw new IOException(AppText.tr("Anzeigetexte können nicht gespeichert werden."));
        SharedPreferences.Editor edit=c.getSharedPreferences(AppAppearance.PREFS,0).edit().putLong("trend_revision",revision+1);
        if(mode!=null)edit.putString("mode",mode);
        for(int i=0;i<TrendDisplayWords.COUNT;i++)edit.putString("trend_"+TrendDisplayWords.TRENDS.get(i).name(),words.overrides.get(i));
        for(int i=0;i<TrendDisplayWords.COUNT;i++) {
            String trend=TrendDisplayWords.TRENDS.get(i).name();
            edit.putString("trend_RED_"+trend,words.colors.red.get(i)).putBoolean("trend_RED_"+trend+"_enabled",words.colors.redEnabled.get(i));
            edit.putString("trend_YELLOW_"+trend,words.colors.yellow.get(i)).putBoolean("trend_YELLOW_"+trend+"_enabled",words.colors.yellowEnabled.get(i));
        }
        if(!edit.commit())throw new IOException(AppText.tr("Anzeigetexte können nicht gespeichert werden."));
    }
    private DisplayTrendPreferences() {}
}
