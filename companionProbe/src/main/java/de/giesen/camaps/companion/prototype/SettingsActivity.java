// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** General preferences; output routes and Android permissions have separate destinations. */
public final class SettingsActivity extends UiActivity {
    private boolean syncing;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->finish());
        LevelUi.text(body,AppText.tr("Einstellungen"),28);
        LevelUi.navigation(body,AppText.tr("Darstellung"),v->startActivity(new Intent(this,AppearanceActivity.class)));
        LevelUi.navigation(body,AppText.tr("Einheit"),v->startActivity(new Intent(this,DeveloperOptionsActivity.class)));
        LevelUi.text(body,AppText.tr("Datenquelle"),21);
        Switch receive=new Switch(this);receive.setText(AppText.tr("CamAPS-Empfang"));receive.setMinHeight(LevelUi.dp(this,48));
        receive.setChecked(getSharedPreferences(CamApsListener.PREFS,0).getBoolean(CamApsListener.ENABLED,false));body.addView(receive);
        TextView status=LevelUi.text(body,"",14);
        receive.setOnCheckedChangeListener((v,enabled)->{
            if(syncing)return;
            if(!getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,enabled).commit()) {
                syncing=true;receive.setChecked(!enabled);syncing=false;status.setText(AppText.tr("Empfangseinstellung nicht speicherbar"));return;
            }
            ProbeState.clear();ProbeState.vectorCandidate=null;HomeWidget.changed(this);
            if(WatchPublisher.enabled(this))WatchPublisher.clear(this);
            AlarmProbe.clear(enabled?AlarmProbe.Ingress.WAITING:AlarmProbe.Ingress.OFF);
            LiveAlarmService.sourceDisconnected();
            ComponentName component=new ComponentName(this,CamApsListener.class);
            if(!enabled){LivePreferences.set(this,false,false);stopService(new Intent(this,LiveAlarmService.class));}
            getPackageManager().setComponentEnabledSetting(component,enabled?android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);
            if(enabled)CamApsConnection.ensure(this);
            status.setText(AppText.tr(enabled?"Warte auf ein neues CamAPS-Update":"Empfang ausgeschaltet"));
        });
        LevelUi.text(body,AppText.tr("App-Informationen"),21);
        LevelUi.navigation(body,AppText.tr("Sicherheit und Bedingungen"),v->startActivity(new Intent(this,SafetyActivity.class)));
        LevelUi.text(body,"Glucose Voice Alarm · "+BuildConfig.VERSION_NAME,15);
        LevelUi.text(body,"xDrip+ · GPL-3.0-or-later",14);
    }
}
