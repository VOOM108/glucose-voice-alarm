// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
/** Local watch notifications for accepted phone events. No independent glucose alarm logic. */
final class WatchAlerts {
    static final String PREFS="watch_alerts_v1",CHANNEL="phone_alerts_v1",DISMISSED="de.giesen.gva.watch.ALERT_DISMISSED";
    static final int ID=801,TEST_ID=802;
    static boolean enabled(Context c){return WatchConsent.ready(c)&&c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static boolean permission(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static void set(Context c,boolean enabled){
        boolean allow=enabled&&WatchConsent.ready(c)&&permission(c);
        c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",allow).remove("active").remove("active_test").commit();
        c.getSystemService(NotificationManager.class).cancel(ID);
        c.getSystemService(NotificationManager.class).cancel(TEST_ID);
        if(allow)channel(c);
    }
    private static void channel(Context c){
        NotificationChannel ch=new NotificationChannel(CHANNEL,WatchConsent.text("Telefonalarme","Phone alerts"),NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(WatchConsent.text("Übernommene GVA-Alarme und Datenlückenwarnungen","Relayed GVA alarms and data-gap warnings"));
        ch.setSound(null,null);ch.enableVibration(true);ch.setVibrationPattern(new long[]{0,180,180,180});
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    static synchronized boolean receive(Context c,WatchAlert event){
        if(!WatchConsent.ready(c)||!event.validAt(System.currentTimeMillis()))return false;
        android.content.SharedPreferences p=c.getSharedPreferences(PREFS,0);
        int boot=Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);if(boot<0)return false;
        if(p.getInt("boot",-2)==boot){
            if(event.session.equals(p.getString("session",""))){if(event.sequence<=p.getLong("sequence",0))return false;}
            else if(event.issued<p.getLong("issued",0))return false;
        }
        // Reserve receipt before posting. Only identifiers/order are stored, never alert text or values.
        String activeKey=event.test?"active_test":"active";int notificationId=event.test?TEST_ID:ID;
        String active=p.getString(activeKey,null);
        if(!p.edit().putInt("boot",boot).putString("session",event.session).putLong("sequence",event.sequence).putLong("issued",event.issued).commit())return false;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(event.action==WatchAlert.Action.CLEAR){if(event.token.equals(active)){manager.cancel(ID);p.edit().remove("active").commit();}return true;}
        if(!enabled(c)||!permission(c))return false;
        if(event.action==WatchAlert.Action.UPDATE){
            if(!event.token.equals(active)||!visible(c,event.token))return false;
        }
        channel(c);
        NotificationChannel ch=manager.getNotificationChannel(CHANNEL);if(ch==null||ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        Intent open=new Intent(c,WatchActivity.class);
        PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent delete=PendingIntent.getBroadcast(c,event.token.hashCode(),new Intent(c,WatchAlertDismissed.class).setAction(DISMISSED).putExtra("token",event.token).putExtra("test",event.test),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.watch_icon).setContentTitle(event.title).setContentText(event.text)
                .setStyle(new Notification.BigTextStyle().bigText(event.text)).setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tap).setDeleteIntent(delete).setAutoCancel(true).setOngoing(false).setLocalOnly(true)
                .setOnlyAlertOnce(event.action==WatchAlert.Action.UPDATE).setWhen(event.issued).setShowWhen(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(event.test?15000:0).addExtras(token(event.token)).build();
        if(!p.edit().putString(activeKey,event.token).commit())return false;
        try {manager.notify(notificationId,n);return true;}catch(RuntimeException e){p.edit().remove(activeKey).commit();return false;}
    }
    private static android.os.Bundle token(String value){android.os.Bundle b=new android.os.Bundle();b.putString("gva_alert_token",value);return b;}
    private static boolean visible(Context c,String token){
        for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())
            if(n.getId()==ID&&token.equals(n.getNotification().extras.getString("gva_alert_token")))return true;
        return false;
    }
    static synchronized void dismissed(Context c,String token,boolean test){
        android.content.SharedPreferences p=c.getSharedPreferences(PREFS,0);
        String key=test?"active_test":"active";if(token!=null&&token.equals(p.getString(key,null)))p.edit().remove(key).commit();
    }
    private WatchAlerts(){}
}
