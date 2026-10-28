// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import de.giesen.gva.watch.WatchAlert;
import java.util.UUID;
/** Separate opt-in relay of accepted live outputs; normal readings never enter this path. */
final class WatchAlertPublisher {
    static final String PREFS="gva_watch_alerts_v1";
    private static final String SESSION=UUID.randomUUID().toString();
    private static long sequence;
    private static String active;
    static volatile WatchTransferStatus status=WatchTransferStatus.EMPTY;
    static boolean enabled(Context c){return SafetyConsent.ready(c)&&c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static synchronized void set(Context c,boolean enabled){if(!SafetyConsent.ready(c))return;if(!enabled)clear(c);c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",enabled).commit();}
    static synchronized void alarm(Context c,String title,String text){if(enabled(c)&&LiveAlarmService.live())show(c,title,text,false);}
    static synchronized void test(Context c){
        if(!enabled(c))return;WatchAlert event=event(WatchAlert.Action.SHOW,UUID.randomUUID().toString(),WatchPublisher.tr("GVA · Testalarm","GVA · Test alert"),WatchPublisher.tr("Test – kein Messwert. Nur die Uhr vibriert.","Test – no glucose reading. Only the watch vibrates."),true);
        if(event!=null)WatchAlertTransport.enqueue(c,event);
    }
    private static void show(Context c,String title,String text,boolean test){
        String token=UUID.randomUUID().toString();WatchAlert event=event(WatchAlert.Action.SHOW,token,title,text,test);
        if(event==null)return;active=token;WatchAlertTransport.enqueue(c,event);
    }
    static synchronized void update(Context c,String title,String text){
        if(active==null||!enabled(c)||!LiveAlarmService.live())return;
        WatchAlert event=event(WatchAlert.Action.UPDATE,active,title,text,false);if(event!=null)WatchAlertTransport.enqueue(c,event);
    }
    static synchronized void clear(Context c){
        if(active==null)return;WatchAlert event=event(WatchAlert.Action.CLEAR,active,"","",false);active=null;
        if(event!=null)WatchAlertTransport.enqueue(c,event);
    }
    private static WatchAlert event(WatchAlert.Action action,String token,String title,String text,boolean test){
        try{return new WatchAlert(action,SESSION,++sequence,token,System.currentTimeMillis(),bounded(title,80),bounded(text,500),test);}
        catch(IllegalArgumentException e){status=WatchTransferStatus.completed(WatchTransferStatus.alert(action,test),WatchTransferStatus.Result.FAILED);return null;}
    }
    static synchronized boolean sendAllowed(Context c,WatchAlert event){
        return SafetyConsent.ready(c)&&event.validAt(System.currentTimeMillis())&&(event.action==WatchAlert.Action.CLEAR
                ||(enabled(c)&&(event.test||(event.token.equals(active)&&LiveAlarmService.live()&&LivePreferences.pauseUntil(c)<=RuntimeClock.read(c).wallMs))));
    }
    private static String bounded(String text,int max){if(text.length()<=max)return text;int end=max-1;if(Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end)+"…";}
    private WatchAlertPublisher(){}
}
