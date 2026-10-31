// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;

import android.app.*;
import android.content.*;
import android.os.Build;
import de.giesen.camaps.companion.prototype.alarm.*;

final class LiveNotifications {
    static final int STATUS_ID=701,ALARM_ID=702;
    static final String STATUS_CHANNEL="live_status_v1",ALARM_CHANNEL="live_alarm_v1";
    private final Context context;
    private final NotificationManager manager;
    LiveNotifications(Context context) {
        this.context=context; manager=context.getSystemService(NotificationManager.class);
        NotificationChannel status=new NotificationChannel(STATUS_CHANNEL,AppText.tr("Empfang und Sperrbildschirm"),NotificationManager.IMPORTANCE_LOW);
        status.setSound(null,null); status.enableVibration(false); status.setDescription(AppText.tr("Aktueller Empfang und Ausgabestatus"));
        NotificationChannel alarm=new NotificationChannel(ALARM_CHANNEL,AppText.tr("Eigene Alarme"),NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null,null); alarm.enableVibration(false); alarm.setDescription(AppText.tr("Ton und Sprache kommen aus den Einstellungen der jeweiligen Alarmstufe"));
        manager.createNotificationChannel(status); manager.createNotificationChannel(alarm);
    }
    private PendingIntent action(String action,String id,int code) {
        Intent intent=new Intent(context,LiveAlarmService.class).setAction(action);
        if (id!=null) intent.putExtra("id",id);
        return PendingIntent.getService(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent open(Class<?> screen,int code) {
        return PendingIntent.getActivity(context,code,new Intent(context,screen),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    Notification status(Number value,NotificationParser.Trend trend,long received,boolean fresh,String detail) {
        return status(value,trend,received,fresh,detail,ArrowColor.NORMAL);
    }
    Notification status(Number value,NotificationParser.Trend trend,long received,boolean fresh,String detail,ArrowColor color) {
        String clock=received>0?new java.text.SimpleDateFormat("HH:mm",java.util.Locale.GERMANY).format(new java.util.Date(received)):AppText.tr("Warte auf einen neuen Wert");
        boolean known=value!=null&&received>0;
        Notification.Builder builder=new Notification.Builder(context,STATUS_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(known?MetricNotification.reading(value.doubleValue(),trend,UnitPreferences.display(context),fresh,color):AppText.tr("Kein aktueller Wert"))
                .setContentText(known&&!fresh?clock+" · "+MetricNotification.ageTitle(received):!fresh && detail.startsWith(AppText.tr("Datenlücke"))?clock+AppText.tr(" · Datenlücke"):clock).setOngoing(true).setLocalOnly(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(open(ProbeActivity.class,1)).setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Einstellungen"),open(ReadinessActivity.class,2)).build())
                .addAction(new Notification.Action.Builder(0,AppText.tr("Beenden"),action(LiveAlarmService.STOP,null,3)).build());
        if (Build.VERSION.SDK_INT>=31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (known) {
            builder.setWhen(received).setShowWhen(false).setUsesChronometer(false);
            if (LivePreferences.nativeDisplay(context)) MetricNotification.apply(builder,value.doubleValue(),trend,received,UnitPreferences.display(context),fresh,fresh?DisplayTrendPreferences.title(context,trend,color):MetricNotification.ageTitle(received),color);
        } else builder.setShowWhen(false);
        return builder.build();
    }
    void update(Number value,NotificationParser.Trend trend,long received,boolean fresh,String detail) { update(value,trend,received,fresh,detail,ArrowColor.NORMAL); }
    void update(Number value,NotificationParser.Trend trend,long received,boolean fresh,String detail,ArrowColor color) { manager.notify(STATUS_ID,status(value,trend,received,fresh,detail,color)); }
    void alarm(AlarmLevel level,double value,NotificationParser.Trend trend) {
        alarm(level,value,trend,null);
    }
    void alarm(AlarmLevel level,double value,NotificationParser.Trend trend,String additional) {
        alarm(level,value,trend,additional,false);
    }
    void alarm(AlarmLevel level,double value,NotificationParser.Trend trend,String additional,boolean refresh) {
        alarm(level,value,trend,additional,refresh,ArrowColor.NORMAL);
    }
    void alarm(AlarmLevel level,double value,NotificationParser.Trend trend,String additional,boolean refresh,ArrowColor color) {
        String title=level.name+(additional==null?"":" · "+additional);
        String text=AppText.tr("Aktuell: ")+UnitPreferences.display(context).number(value,java.util.Locale.getDefault())+" "+UnitPreferences.display(context).label+(trend==NotificationParser.Trend.UNKNOWN?"":" "+trend.symbol);
        Notification.Builder builder=new Notification.Builder(context,ALARM_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(title).setContentText(color.colored()?coloredAlarmText(text,trend,color):text)
                .setContentIntent(open(ReadinessActivity.class,4)).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setLocalOnly(true).setOnlyAlertOnce(refresh)
                .setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Quittieren"),action(LiveAlarmService.ACK,level.rule.id,5)).build());
        if (LevelForm.hasSnooze(level.rule.kind)) builder.addAction(new Notification.Action.Builder(0,"Snooze "+level.rule.outputs.snoozeMinutes+" Min.",action(LiveAlarmService.SNOOZE,level.rule.id,6)).build());
        manager.notify(ALARM_ID,builder.build());
        if(refresh)WatchAlertPublisher.update(context,title,text);else WatchAlertPublisher.alarm(context,title,text);
    }
    private CharSequence coloredAlarmText(String text,NotificationParser.Trend trend,ArrowColor color) {
        if(trend==NotificationParser.Trend.UNKNOWN)return text;
        android.text.SpannableString styled=new android.text.SpannableString(text);
        styled.setSpan(new android.text.style.ForegroundColorSpan(color.phone),text.length()-trend.symbol.length(),text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }
    void gap(String message) {
        Notification.Builder builder=new Notification.Builder(context,ALARM_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(AppText.tr("Datenlücke")).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open(GapSettingsActivity.class,7)).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setLocalOnly(true)
                .setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Quittieren"),action(LiveAlarmService.ACK,"gap_warning",8)).build());
        manager.notify(ALARM_ID,builder.build());
        WatchAlertPublisher.alarm(context,AppText.tr("Datenlücke"),message);
    }
    void clearAlarm() { manager.cancel(ALARM_ID); WatchAlertPublisher.clear(context); }
}
