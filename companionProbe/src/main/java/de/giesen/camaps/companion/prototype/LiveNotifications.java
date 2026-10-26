// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

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
    Notification status(Integer value,NotificationParser.Trend trend,long received,boolean fresh,String detail) {
        String clock=received>0?new java.text.SimpleDateFormat("HH:mm",java.util.Locale.GERMANY).format(new java.util.Date(received)):AppText.tr("Warte auf einen neuen Wert");
        Notification.Builder builder=new Notification.Builder(context,STATUS_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(value!=null && fresh?value+(trend==NotificationParser.Trend.UNKNOWN?"":" "+trend.symbol):AppText.tr("Kein aktueller Wert"))
                .setContentText(!fresh && detail.startsWith(AppText.tr("Datenlücke"))?clock+AppText.tr(" · Datenlücke"):clock).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(open(ProbeActivity.class,1)).setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Einstellungen"),open(ReadinessActivity.class,2)).build())
                .addAction(new Notification.Action.Builder(0,AppText.tr("Beenden"),action(LiveAlarmService.STOP,null,3)).build());
        if (Build.VERSION.SDK_INT>=31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (received>0 && value!=null && fresh) {
            builder.setWhen(received).setShowWhen(false).setUsesChronometer(false);
            if (LivePreferences.nativeDisplay(context)) MetricNotification.apply(builder,value,trend,received);
        } else builder.setShowWhen(false);
        return builder.build();
    }
    void update(Integer value,NotificationParser.Trend trend,long received,boolean fresh,String detail) { manager.notify(STATUS_ID,status(value,trend,received,fresh,detail)); }
    void alarm(AlarmLevel level,int value,NotificationParser.Trend trend) {
        alarm(level,value,trend,null);
    }
    void alarm(AlarmLevel level,int value,NotificationParser.Trend trend,String additional) {
        alarm(level,value,trend,additional,false);
    }
    void alarm(AlarmLevel level,int value,NotificationParser.Trend trend,String additional,boolean refresh) {
        Notification.Builder builder=new Notification.Builder(context,ALARM_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(level.name+(additional==null?"":" · "+additional)).setContentText("Aktuell: "+value+" mg/dl"+(trend==NotificationParser.Trend.UNKNOWN?"":" "+trend.symbol))
                .setContentIntent(open(ReadinessActivity.class,4)).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(refresh)
                .setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Quittieren"),action(LiveAlarmService.ACK,level.rule.id,5)).build());
        if (LevelForm.hasSnooze(level.rule.kind)) builder.addAction(new Notification.Action.Builder(0,"Snooze "+level.rule.outputs.snoozeMinutes+" Min.",action(LiveAlarmService.SNOOZE,level.rule.id,6)).build());
        manager.notify(ALARM_ID,builder.build());
    }
    void gap(String message) {
        Notification.Builder builder=new Notification.Builder(context,ALARM_CHANNEL).setSmallIcon(R.drawable.ic_xcam_status)
                .setContentTitle(AppText.tr("Datenlücke")).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open(GapSettingsActivity.class,7)).setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
                .setVisibility(LivePreferences.publicValue(context)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(0,AppText.tr("Quittieren"),action(LiveAlarmService.ACK,"gap_warning",8)).build());
        manager.notify(ALARM_ID,builder.build());
    }
    void clearAlarm() { manager.cancel(ALARM_ID); }
}
