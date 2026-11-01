// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import java.io.IOException;
import java.util.*;

/** Per-instance presentation only. Never reads or writes output, alarm or profile stores. */
final class WidgetAppearance {
    enum Mode { SYSTEM, APP, CUSTOM }
    enum TextTone { AUTO, LIGHT, DARK }
    private static final Object LOCK=new Object();
    private static final String[] KEYS={"style_","opacity_","background_","text_","textTone_","controls_","chartOpacity_","historyHours_","paddingHorizontal_","paddingVertical_","revision_"};
    static final class Settings {
        final Mode mode;final int opacity,background,text,chartOpacity,historyHours,paddingHorizontal,paddingVertical;final TextTone textTone;final boolean controls;
        Settings(Mode mode,int opacity,int background,int text,TextTone textTone,boolean controls){
            this(mode,opacity,background,text,textTone,controls,100,2,8,8);
        }
        Settings(Mode mode,int opacity,int background,int text,TextTone textTone,boolean controls,int chartOpacity,int historyHours,int paddingHorizontal,int paddingVertical){
            if(mode==null || textTone==null || opacity<0 || opacity>100 || chartOpacity<0 || chartOpacity>100 || historyHours<1 || historyHours>2 || paddingHorizontal<0 || paddingHorizontal>24 || paddingVertical<0 || paddingVertical>24)throw new IllegalArgumentException("Invalid widget appearance");
            this.mode=mode;this.opacity=opacity;this.background=background|0xff000000;this.text=text|0xff000000;this.textTone=textTone;this.controls=controls;
            this.chartOpacity=chartOpacity;this.historyHours=historyHours;this.paddingHorizontal=paddingHorizontal;this.paddingVertical=paddingVertical;
        }
        static Settings defaults(Mode mode){return new Settings(mode,100,0xff19252a,0xffe9f1f2,TextTone.AUTO,true);}
        @Override public boolean equals(Object value){if(!(value instanceof Settings))return false;Settings b=(Settings)value;return mode==b.mode&&opacity==b.opacity&&background==b.background&&text==b.text&&textTone==b.textTone&&controls==b.controls&&chartOpacity==b.chartOpacity&&historyHours==b.historyHours&&paddingHorizontal==b.paddingHorizontal&&paddingVertical==b.paddingVertical;}
        @Override public int hashCode(){return Objects.hash(mode,opacity,background,text,textTone,controls,chartOpacity,historyHours,paddingHorizontal,paddingVertical);}
    }
    static final class Snapshot {final long revision;final Settings settings;Snapshot(long revision,Settings settings){this.revision=revision;this.settings=settings;}}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(HomeWidget.PREFS,0);}
    static boolean configured(Context c,int id){return prefs(c).contains("style_"+id);}
    private static int integer(SharedPreferences p,String key,int fallback){try{return p.getInt(key,fallback);}catch(ClassCastException e){return fallback;}}
    private static int bounded(SharedPreferences p,String key,int fallback,int min,int max){int n=integer(p,key,fallback);return n<min||n>max?fallback:n;}
    private static String string(SharedPreferences p,String key,String fallback){try{return p.getString(key,fallback);}catch(ClassCastException e){return fallback;}}
    static Snapshot load(Context c,int id){synchronized(LOCK){SharedPreferences p=prefs(c);Mode mode=Mode.APP;TextTone tone=TextTone.AUTO;
        try{mode=Mode.valueOf(string(p,"style_"+id,"APP"));}catch(IllegalArgumentException e){/* Legacy widgets retain their app theme. */}
        try{tone=TextTone.valueOf(string(p,"textTone_"+id,"AUTO"));}catch(IllegalArgumentException e){/* Use a readable default. */}
        int opacity=integer(p,"opacity_"+id,100);if(opacity<0||opacity>100)opacity=100;
        boolean controls=true;try{controls=p.getBoolean("controls_"+id,true);}catch(ClassCastException e){/* Default layout. */}
        long revision=0;try{revision=Math.max(0,p.getLong("revision_"+id,0));}catch(ClassCastException e){/* Invalid revision will not overwrite a later edit. */}
        return new Snapshot(revision,new Settings(mode,opacity,integer(p,"background_"+id,0xff19252a),integer(p,"text_"+id,0xffe9f1f2),tone,controls,bounded(p,"chartOpacity_"+id,100,0,100),bounded(p,"historyHours_"+id,2,1,2),bounded(p,"paddingHorizontal_"+id,8,0,24),bounded(p,"paddingVertical_"+id,8,0,24)));
    }}
    private static void put(SharedPreferences.Editor e,int id,Settings s,long revision){e.putString("style_"+id,s.mode.name()).putInt("opacity_"+id,s.opacity).putInt("background_"+id,s.background).putInt("text_"+id,s.text).putString("textTone_"+id,s.textTone.name()).putBoolean("controls_"+id,s.controls).putInt("chartOpacity_"+id,s.chartOpacity).putInt("historyHours_"+id,s.historyHours).putInt("paddingHorizontal_"+id,s.paddingHorizontal).putInt("paddingVertical_"+id,s.paddingVertical).putLong("revision_"+id,revision);}
    static void save(Context c,int id,long expected,Settings settings)throws IOException{synchronized(LOCK){
        if(!HomeWidget.owns(c,id))throw new IOException(AppText.tr("Dieses Widget ist nicht mehr vorhanden."));
        long revision=load(c,id).revision;if(revision!=expected||revision==Long.MAX_VALUE)throw new IOException(AppText.tr("Widget-Einstellungen wurden inzwischen geändert. Bitte neu öffnen."));
        SharedPreferences.Editor edit=prefs(c).edit();put(edit,id,settings,revision+1);if(!edit.commit())throw new IOException(AppText.tr("Widget-Einstellungen konnten nicht gespeichert werden."));
    }}
    static void initialize(Context c,int[] ids,Mode fallback){synchronized(LOCK){SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();boolean changed=false;
        for(int id:ids)if(!p.contains("style_"+id)){Settings old=load(c,id).settings;put(e,id,new Settings(fallback,old.opacity,old.background,old.text,old.textTone,old.controls,old.chartOpacity,old.historyHours,old.paddingHorizontal,old.paddingVertical),load(c,id).revision);changed=true;}if(changed)e.commit();
    }}
    static void delete(Context c,int[] ids){synchronized(LOCK){SharedPreferences.Editor e=prefs(c).edit();for(int id:ids)for(String key:KEYS)e.remove(key+id);e.commit();}}
    static void restore(Context c,int[] oldIds,int[] newIds){synchronized(LOCK){int size=Math.min(oldIds.length,newIds.length);List<Snapshot> old=new ArrayList<>();for(int n=0;n<size;n++)old.add(load(c,oldIds[n]));
        SharedPreferences.Editor e=prefs(c).edit();for(int n=0;n<size;n++)for(String key:KEYS)e.remove(key+oldIds[n]);for(int n=0;n<size;n++)put(e,newIds[n],old.get(n).settings,old.get(n).revision);e.commit();
    }}
    static final class Palette {
        final int surface,ink,muted,line,accent,onAccent,error,stale;final boolean dark;
        Palette(int surface,int ink,int muted,int line,int accent,int onAccent,int error,boolean dark){this.surface=surface;this.ink=ink;this.muted=muted;this.line=line;this.accent=accent;this.onAccent=onAccent;this.error=error;this.dark=dark;this.stale=new AppAppearance.Colors(dark).muted;}
        int background(int opacity){return (surface&0x00ffffff)|(Math.round(opacity*255f/100)<<24);}
    }
    static Palette palette(Context c,Settings s){
        boolean systemDark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        boolean dark=s.mode==Mode.APP?AppAppearance.dark(c):s.mode==Mode.SYSTEM?systemDark:luminance(s.background)<.45;
        AppAppearance.Colors app=new AppAppearance.Colors(dark);int bg=app.surface,ink=app.ink,muted=app.muted,line=app.line,accent=app.accent,onAccent=app.onAccent;
        if(s.mode==Mode.SYSTEM && Build.VERSION.SDK_INT>=31){
            bg=c.getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_50);
            ink=c.getColor(dark?android.R.color.system_neutral1_50:android.R.color.system_neutral1_900);
            muted=c.getColor(dark?android.R.color.system_neutral2_200:android.R.color.system_neutral2_700);
            line=c.getColor(dark?android.R.color.system_neutral2_700:android.R.color.system_neutral2_200);
            accent=c.getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600);
            onAccent=c.getColor(dark?android.R.color.system_accent1_800:android.R.color.system_accent1_0);
        }else if(s.mode==Mode.CUSTOM){bg=s.background;ink=s.text;muted=mix(ink,bg,.22);line=mix(ink,bg,.65);}
        if(s.mode!=Mode.CUSTOM && s.textTone!=TextTone.AUTO){boolean light=s.textTone==TextTone.LIGHT;ink=light?0xfff5f7f8:0xff152126;muted=light?0xffd2dce0:0xff33454d;}
        return new Palette(bg,ink,muted,line,accent,onAccent,app.error,dark);
    }
    private static int mix(int a,int b,double part){return Color.rgb((int)(Color.red(a)*(1-part)+Color.red(b)*part),(int)(Color.green(a)*(1-part)+Color.green(b)*part),(int)(Color.blue(a)*(1-part)+Color.blue(b)*part));}
    private static double luminance(int color){return (.2126*Color.red(color)+.7152*Color.green(color)+.0722*Color.blue(color))/255;}
    private WidgetAppearance(){}
}
