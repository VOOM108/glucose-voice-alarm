// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.service.notification.NotificationListenerService;

/** Own listener binding only. Never grants access, enables receipt/output, or imports old notifications. */
final class CamApsConnection {
    static final long RETRY_MS=30000;
    private static CamApsListener current;
    private static long lastAttempt=-1;
    private static boolean failed;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    static boolean connected() {return current!=null;}
    private static boolean allowed(Context c) {
        c=c.getApplicationContext();
        return SafetyConsent.ready(c) && c.getSharedPreferences(CamApsListener.PREFS,0).getBoolean(CamApsListener.ENABLED,false)
                && SystemReadiness.read(c).access;
    }
    static boolean ensure(Context c) {
        if(!allowed(c))return false;
        if(current!=null)return true;
        long now=SystemClock.elapsedRealtime();
        if(lastAttempt>=0 && now>=lastAttempt && now-lastAttempt<RETRY_MS)return !failed;
        lastAttempt=now;failed=false;
        try {
            ComponentName name=new ComponentName(c,CamApsListener.class);
            PackageManager pm=c.getPackageManager();
            if(pm.getComponentEnabledSetting(name)==PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                pm.setComponentEnabledSetting(name,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            NotificationListenerService.requestRebind(name);return true;
        } catch(RuntimeException e){failed=true;return false;}
    }
    static void connected(CamApsListener service) {current=service;lastAttempt=-1;failed=false;}
    static boolean disconnected(CamApsListener service) {
        if(current!=null && current!=service)return false;
        current=null;
        Context app=service.getApplicationContext();
        // Do not loop from the callback; the foreground service also retries at a bounded rate.
        handler.postDelayed(()->ensure(app),1000);return true;
    }
    static boolean reconnect(Context c) {
        if(!allowed(c))return false;
        CamApsListener old=current;current=null;lastAttempt=-1;failed=false;
        if(old==null)return ensure(c);
        try {old.requestUnbind();}
        catch(RuntimeException e){failed=true;return false;}
        Context app=c.getApplicationContext();handler.postDelayed(()->ensure(app),1000);return true;
    }
    static String summary() {
        return current!=null?AppText.tr("Benachrichtigungsempfang verbunden"):
                failed?AppText.tr("Empfang nicht verbunden · Neu verbinden"):
                AppText.tr("Warte auf Android-Verbindung");
    }
    private CamApsConnection(){}
}
