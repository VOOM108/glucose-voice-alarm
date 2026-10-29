// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.KeyguardManager;
import android.content.*;
import android.widget.Toast;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Private immutable widget actions. Never launches an Activity from a receiver. */
public final class WidgetControlReceiver extends BroadcastReceiver {
    static final String ACTION="de.giesen.gva.WIDGET_CONTROL";
    /** Only already-known setup problems get an Activity PendingIntent at render time. */
    static boolean needsUi(Context c,int channel,boolean enabled) {
        if(!SafetyConsent.ready(c))return true;
        try {
            synchronized(ConfigurationFiles.LOCK) {
                SettingsTransaction.requireComplete(c.getFilesDir());
                if(channel==-1)return enabled && LiveActivation.check(c)!=null;
                OutputPreferences p=new OutputPreferenceStore(c.getFilesDir()).load().preferences;
                return OutputModeUi.needsInterval(p,channel,enabled) || OutputModeUi.check(c,OutputModeUi.desired(p,channel,enabled))!=null;
            }
        }catch(IOException|IllegalArgumentException e){return true;}
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null || !ACTION.equals(intent.getAction()))return;
        AppText.configure(context.getResources().getConfiguration().getLocales().get(0));
        try {
            int widget=intent.getIntExtra("widget",-1),channel=intent.getIntExtra("channel",-2);
            KeyguardManager lock=context.getSystemService(KeyguardManager.class);
            if(!SafetyConsent.ready(context) || !HomeWidget.owns(context,widget) || channel< -1 || channel>3
                    || (lock!=null && lock.isDeviceLocked()))return;
            synchronized(ConfigurationFiles.LOCK) {
                SettingsTransaction.requireComplete(context.getFilesDir());
                OutputPreferenceStore.Snapshot current=new OutputPreferenceStore(context.getFilesDir()).load();
                if(current.revision!=intent.getLongExtra("revision",-1)
                        || LivePreferences.alarms(context)!=intent.getBooleanExtra("expected",false))return;
                boolean enabled=intent.getBooleanExtra("enabled",false);
                if(channel>=0) {
                    if(OutputModeUi.needsInterval(current.preferences,channel,enabled)) {unavailable(context);return;}
                    OutputPreferences desired=OutputModeUi.desired(current.preferences,channel,enabled);
                    if(OutputModeUi.check(context,desired)!=null){unavailable(context);return;}
                    OutputModeUi.save(context,current,desired);
                } else if(enabled) {
                    // A deliberate widget interaction is Android's documented FGS-start exemption.
                    if(LiveActivation.start(context)!=null)unavailable(context);
                } else {
                    LivePreferences.set(context,LivePreferences.monitor(context),false);LiveAlarmService.settingsChanged();
                }
            }
        }catch(IOException|IllegalArgumentException e){unavailable(context);}
        finally {HomeWidget.changed(context);}
    }
    private static void unavailable(Context c) {
        Toast.makeText(c,AppText.tr("Nicht umgeschaltet. Bitte im Dashboard prüfen."),Toast.LENGTH_LONG).show();
    }
}
