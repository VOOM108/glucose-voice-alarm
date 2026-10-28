// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import de.giesen.camaps.companion.prototype.alarm.*;

/** Explicit immutable widget click, handled in foreground. Replayed old settings clicks are ignored. */
public final class WidgetControlActivity extends UiActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);if(!SafetyConsent.ready(this))return;
        if(saved!=null){finish();return;}
        Intent intent=getIntent();int id=intent.getIntExtra("widget",-1),channel=intent.getIntExtra("channel",-2);
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        if(!HomeWidget.owns(this,id)||channel< -1||channel>3||(keyguard!=null&&keyguard.isDeviceLocked())){finish();return;}
        boolean enabled=intent.getBooleanExtra("enabled",false);
        LinearLayout body=LevelUi.screen(this);LevelUi.text(body,HomeWidget.tr("Widget · Ausgabe","Widget · Output"),25);
        LevelUi.button(body,AppText.tr("Zurück"),v->finish());
        try {
            OutputPreferenceStore.Snapshot current=new OutputPreferenceStore(getFilesDir()).load();
            if(current.revision!=intent.getLongExtra("revision",-1)||LivePreferences.alarms(this)!=intent.getBooleanExtra("expected",false)){HomeWidget.changed(this);finish();return;}
            if(channel>=0){OutputModeUi.channel(this,channel,enabled,()->{HomeWidget.changed(this);finish();});if(!isFinishing()){body.addView(new ModeToggleRow(this));LevelUi.button(body,AppText.tr("Ton und Sprache"),v->startActivity(new Intent(this,OutputSettingsActivity.class)));}}
            else if(enabled){LiveActivation.Problem problem=LiveActivation.start(this);if(problem==null){HomeWidget.changed(this);finish();}else{LevelUi.text(body,problem.text,18);LevelUi.button(body,AppText.tr("Prüfen"),v->startActivity(new Intent(this,problem.target)));}}
            else{LivePreferences.set(this,LivePreferences.monitor(this),false);LiveAlarmService.settingsChanged();HomeWidget.changed(this);finish();}
        }catch(java.io.IOException|IllegalArgumentException e){LevelUi.text(body,HomeWidget.tr("Einstellungen nicht lesbar. Bitte in der App prüfen.","Cannot read settings. Please check the app."),18);}
    }
}
