// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import de.giesen.gva.watch.WatchSnapshot;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.util.*;
import java.util.concurrent.*;

/** One coalesced display worker. No sensor connection, alarm output or persistent reading cache. */
final class HomeWidget {
    static final String PREFS="gva_widget_v1",REFRESH="de.giesen.gva.WIDGET_REFRESH";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"gva-home-widget");t.setDaemon(true);return t;});
    private static final List<Runnable> completions=new ArrayList<>();
    private static boolean running,dirty;
    static void changed(Context c){request(c,null);}
    static void request(Context context,Runnable done) {
        final Context c=context.getApplicationContext();
        synchronized(HomeWidget.class) {
            if(done!=null){if(completions.size()<32)completions.add(done);else done.run();}
            dirty=true;if(running)return;running=true;
        }
        WORK.execute(()->{
            while(true) {
                List<Runnable> finished;
                synchronized(HomeWidget.class){dirty=false;finished=new ArrayList<>(completions);completions.clear();}
                try{update(c);}catch(RuntimeException e){/* Keep fixed source timestamp; never log readings. */}
                finally{for(Runnable callback:finished)try{callback.run();}catch(RuntimeException e){/* An ended broadcast must not stop later refreshes. */}}
                synchronized(HomeWidget.class){if(!dirty){running=false;return;}}
            }
        });
    }
    static int[] ids(Context c){return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,HomeWidgetProvider.class));}
    static boolean owns(Context c,int id){AppWidgetProviderInfo info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id);return info!=null&&new ComponentName(c,HomeWidgetProvider.class).equals(info.provider);}
    static boolean controls(Context c,int id,int width,int height){return WidgetAppearance.load(c,id).settings.controls;}
    static final class Model {
        final WatchSnapshot reading;final boolean fresh,live,configured,paused,ready;final OutputPreferenceStore.Snapshot output;
        Model(WatchSnapshot s,boolean fresh,boolean live,boolean configured,boolean paused,boolean ready,OutputPreferenceStore.Snapshot output){reading=s;this.fresh=fresh;this.live=live;this.configured=configured;this.paused=paused;this.ready=ready;this.output=output;}
    }
    static Model model(Context c) {
        WatchSnapshot s=null;OutputPreferenceStore.Snapshot output=null;boolean ready=SafetyConsent.ready(c);
        if(ready)try{output=new OutputPreferenceStore(c.getFilesDir()).load();s=PhoneDisplaySnapshot.read(c);}catch(java.io.IOException|IllegalArgumentException e){/* No output configuration is invented. */}
        return new Model(s,s!=null&&s.sourceAvailable,LiveAlarmService.live(),LivePreferences.alarms(c),LivePreferences.pauseUntil(c)>System.currentTimeMillis(),ready,output);
    }
    static String tr(String de,String en){return "de".equals(AppText.language())?de:en;}
    static String status(Model m) {
        if(!m.ready)return tr("Hinweise bestätigen","Review safety notices");
        if(m.output==null)return tr("Einstellungen prüfen","Check settings");
        return m.live?(m.paused?tr("Ausgabe pausiert","Output paused"):tr("Ausgabe an","Output on")):m.configured?tr("Ausgabe prüfen","Check output"):tr("Ausgabe aus","Output off");
    }
    static RemoteViews view(Context c,int id,int width,int height,Model model) {
        return view(c,id,width,height,model,WidgetAppearance.load(c,id).settings);
    }
    static RemoteViews view(Context c,int id,int width,int height,Model model,WidgetAppearance.Settings settings) {
        return render(c,id,width,height,model,settings,true);
    }
    static RemoteViews preview(Context c,int width,int height,Model model,WidgetAppearance.Settings settings) {return render(c,0,width,height,model,settings,false);}
    private static RemoteViews render(Context c,int id,int width,int height,Model model,WidgetAppearance.Settings settings,boolean interactive) {
        WidgetAppearance.Palette colors=WidgetAppearance.palette(c,settings);boolean dark=colors.dark,show=settings.controls;
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.home_widget);
        v.setImageViewBitmap(R.id.widget_background,HomeWidgetRenderer.surface(width,height,colors.background(settings.opacity)));
        int vertical=Math.min(settings.paddingVertical,Math.max(0,(height-(show?96:48))/2));
        float density=c.getResources().getDisplayMetrics().density;
        v.setViewPadding(R.id.widget_content,Math.round(settings.paddingHorizontal*density),Math.round(vertical*density),Math.round(settings.paddingHorizontal*density),Math.round(vertical*density));
        PendingIntent dashboard=interactive?activity(c,id,new Intent(c,ProbeActivity.class),"dashboard"):null;
        if(interactive)v.setOnClickPendingIntent(R.id.widget_root,dashboard);
        int dataWidth=Math.max(160,width-2*settings.paddingHorizontal),chartWidth=Math.round((dataWidth-8)/2.25f);
        v.setImageViewBitmap(R.id.widget_chart,new HomeWidgetRenderer().image(model.reading,model.fresh,dark,chartWidth,density,settings.historyHours*60,System.currentTimeMillis(),colors.muted));
        v.setInt(R.id.widget_chart,"setImageAlpha",Math.round(settings.chartOpacity*255f/100));
        Intent camaps=interactive?CamApsAppLink.intent(c):null;
        WatchSnapshot s=model.reading;GlucoseUnit unit=s!=null&&s.mmol?GlucoseUnit.MMOL:GlucoseUnit.MGDL;
        int ink=model.fresh?colors.ink:colors.stale;
        v.setImageViewBitmap(R.id.widget_reading,HomeWidgetRenderer.numerals(c,s==null?"—":unit.number(s.value,Locale.getDefault()),s==null?"":arrow(s.trend),ink,false,width,s!=null&&model.fresh?s.arrowColor:ArrowColor.NORMAL));
        String stamp=s==null?"—":tr("Empfangen: ","Received: ")+new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(s.received))+" · "+unit.label;
        v.setTextViewText(R.id.widget_stamp,stamp);v.setTextColor(R.id.widget_stamp,s!=null&&!model.fresh?colors.error:colors.muted);
        v.setImageViewBitmap(R.id.widget_delta,HomeWidgetRenderer.numerals(c,s==null||!model.fresh||s.rate==null?"—":unit.rate(s.rate,Locale.getDefault()).replace(" "+unit.label," "),"",ink,true));
        String accessible=(interactive?"":tr("Vorschau mit Beispieldaten. ","Preview with sample data. "))+description(model).replace(tr("Dashboard öffnen.","Open dashboard."),"");
        String chartDescription=accessible+" "+tr("Zeitraum: ","History window: ")+settings.historyHours*60+tr(" Minuten."," minutes.");
        v.setContentDescription(R.id.widget_data,chartDescription);
        v.setContentDescription(R.id.widget_reading_area,accessible+(interactive?(camaps!=null?tr("CamAPS öffnen.","Open CamAPS."):tr("CamAPS nicht verfügbar. Dashboard öffnen.","CamAPS unavailable. Open dashboard.")):""));
        v.setContentDescription(R.id.widget_chart_area,chartDescription+(interactive?tr("Dashboard öffnen.","Open dashboard."):""));
        if(interactive){
            // The external app receives its ordinary launcher intent, never a GVA URI or values.
            v.setOnClickPendingIntent(R.id.widget_reading_area,camaps==null?dashboard:PendingIntent.getActivity(c,id,camaps,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            v.setOnClickPendingIntent(R.id.widget_chart_area,dashboard);
        }
        v.setViewVisibility(R.id.widget_controls,show?View.VISIBLE:View.GONE);
        v.setViewVisibility(R.id.widget_master_separator,width>=300?View.VISIBLE:View.GONE);
        v.setInt(R.id.widget_master_separator,"setBackgroundColor",colors.line);
        String[] labels={tr("Ton","Sound"),tr("Alarm","Alarm"),tr("Wert","Reading"),tr("Vib.","Vibrate")};
        String[] full={tr("Ton","Sound"),tr("Alarme sprechen","Speak alarms"),tr("Werte sprechen","Speak readings"),tr("Vibration","Vibration")};
        int[] buttons={R.id.widget_tone,R.id.widget_alarm,R.id.widget_value,R.id.widget_vibration};
        int[] tracks={R.id.widget_tone_track,R.id.widget_alarm_track,R.id.widget_value_track,R.id.widget_vibration_track};
        int[] texts={R.id.widget_tone_label,R.id.widget_alarm_label,R.id.widget_value_label,R.id.widget_vibration_label};
        OutputPreferences p=model.output==null?null:model.output.preferences;
        boolean[] enabled=p==null?new boolean[4]:new boolean[]{p.tonesEnabled,p.alarmSpeechEnabled,p.valueSpeechEnabled,p.vibrationEnabled};
        for(int i=0;i<4;i++) {
            toggle(v,texts[i],tracks[i],labels[i],enabled[i],dark,colors);v.setBoolean(buttons[i],"setEnabled",model.ready&&p!=null);
            v.setContentDescription(buttons[i],full[i]+", "+(enabled[i]?tr("an","on"):tr("aus","off")));
            if(interactive&&p!=null)v.setOnClickPendingIntent(buttons[i],control(c,id,i,!enabled[i],model.output.revision,model.configured));
        }
        toggle(v,R.id.widget_master_label,R.id.widget_master_track,model.live&&model.paused?tr("Pause","Paused"):tr("Ausgabe","Output"),model.live,dark,colors);
        v.setContentDescription(R.id.widget_master,interactive?status(model)+". "+tr("Eigene Ausgabe ","Own output ")+(model.live?tr("ausschalten","off"):tr("einschalten","on")):status(model));
        if(interactive)v.setOnClickPendingIntent(R.id.widget_master,control(c,id,-1,!model.live,model.output==null?-1:model.output.revision,model.configured));
        return v;
    }
    static String arrow(WatchSnapshot.Trend trend) {
        switch(trend){case DOUBLE_UP:return "⇈";case UP:return "↑";case UP_RIGHT:return "↗";case FLAT:return "→";case DOWN_RIGHT:return "↘";case DOWN:return "↓";case DOUBLE_DOWN:return "⇊";default:return "";}
    }
    private static void toggle(RemoteViews v,int text,int track,String label,boolean enabled,boolean dark,WidgetAppearance.Palette colors) {
        v.setTextViewText(text,label);v.setTextColor(text,enabled?colors.ink:colors.muted);
        v.setImageViewBitmap(track,HomeWidgetRenderer.toggle(enabled,colors));
    }
    static String description(Model m) {
        if(m.reading==null)return tr("Kein Anzeigestand. Dashboard öffnen.","No display data. Open dashboard.");
        WatchSnapshot s=m.reading;GlucoseUnit unit=s.mmol?GlucoseUnit.MMOL:GlucoseUnit.MGDL;
        String date=new java.text.SimpleDateFormat("dd.MM. HH:mm",Locale.getDefault()).format(new Date(s.received));
        return (m.fresh?"":tr("Veraltet. ","Stale. "))+unit.number(s.value,Locale.getDefault())+" "+unit.label+". "+date+". "+(s.arrowColor.colored()?DisplayTrendPreferences.colorTitle(NotificationParser.Trend.valueOf(s.trend.name()),s.arrowColor):MetricNotification.trendTitle(NotificationParser.Trend.valueOf(s.trend.name())))
                +(m.fresh&&s.rate!=null?". "+unit.rate(s.rate,Locale.getDefault()):"")+". "+tr("Dashboard öffnen.","Open dashboard.");
    }
    private static PendingIntent activity(Context c,int id,Intent intent,String key) {
        intent.setData(Uri.parse("gva-widget://"+id+"/"+key));
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent control(Context c,int id,int channel,boolean enabled,long revision,boolean expected) {
        boolean needsUi=WidgetControlReceiver.needsUi(c,channel,enabled);
        Intent i=new Intent(c,needsUi?WidgetControlActivity.class:WidgetControlReceiver.class)
                .setAction(WidgetControlReceiver.ACTION).putExtra("widget",id).putExtra("channel",channel).putExtra("enabled",enabled).putExtra("revision",revision).putExtra("expected",expected);
        String key="control/"+channel+"/"+revision+"/"+enabled+"/"+expected;
        if(needsUi)return activity(c,id,i,key);
        i.setData(Uri.parse("gva-widget://"+id+"/"+key));
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void update(Context c) {
        int[] ids=ids(c);AlarmManager alarm=c.getSystemService(AlarmManager.class);
        PendingIntent refresh=PendingIntent.getBroadcast(c,0,new Intent(c,HomeWidgetProvider.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(ids.length==0){if(alarm!=null)alarm.cancel(refresh);return;}
        AppText.configure(c.getResources().getConfiguration().getLocales().get(0));Model model=model(c);AppWidgetManager manager=AppWidgetManager.getInstance(c);
        for(int id:ids) {
            RemoteViews views=views(c,id,manager.getAppWidgetOptions(id),model);
            manager.updateAppWidget(id,views); // Full updates release prior bitmap allocations on Android17.
        }
        if(alarm!=null){alarm.cancel(refresh);if(model.reading!=null){long age=System.currentTimeMillis()-model.reading.received;long next=age<WatchSnapshot.FRESH?WatchSnapshot.FRESH:WatchSnapshot.WINDOW+1;alarm.set(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+Math.max(1000,next-age),refresh);}}
    }
    static RemoteViews views(Context c,int id,Bundle options,Model model) {
        ArrayList<SizeF> sizes=Build.VERSION.SDK_INT>=31?options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES):null;
        if(sizes!=null&&!sizes.isEmpty()) {
            List<SizeF> sorted=new ArrayList<>(sizes);sorted.sort(Comparator.comparingDouble(x->x.getWidth()*x.getHeight()));
            Map<SizeF,RemoteViews> variants=new LinkedHashMap<>();
            for(SizeF size:new SizeF[]{sorted.get(0),sorted.get(sorted.size()-1)})variants.put(size,view(c,id,clamp(size.getWidth(),220,600),clamp(size.getHeight(),120,600),model));
            return new RemoteViews(variants);
        }
        boolean landscape=c.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        return view(c,id,clamp(options.getInt(landscape?AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH:AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300),220,600),clamp(options.getInt(landscape?AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT:AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160)),120,600),model);
    }
    private static int clamp(float n,int min,int max){return Float.isFinite(n)?Math.max(min,Math.min(max,Math.round(n))):min;}
    private HomeWidget(){}
}
