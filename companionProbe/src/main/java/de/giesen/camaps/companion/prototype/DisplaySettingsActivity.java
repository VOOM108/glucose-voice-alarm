// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Phone display channel only. Permission state and diagnostics live elsewhere. */
public final class DisplaySettingsActivity extends UiActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);if(!SafetyConsent.ready(this))return;LinearLayout body=LevelUi.screen(this);
        LevelUi.back(body,AppText.tr("Zurück"),v->finish());LevelUi.text(body,AppText.tr("Sperrbildschirm und Benachrichtigungen"),28);
        Switch large=new Switch(this);large.setText(AppText.tr("Große Live-Anzeige verwenden"));large.setMinHeight(LevelUi.dp(this,48));large.setChecked(LivePreferences.nativeDisplay(this));body.addView(large);
        large.setOnCheckedChangeListener((v,checked)->{LivePreferences.nativeDisplay(this,checked);LiveAlarmService.displayChanged();});
        Switch visible=new Switch(this);visible.setText(AppText.tr("Wert auf dem gesperrten Telefon sichtbar"));visible.setMinHeight(LevelUi.dp(this,48));visible.setChecked(LivePreferences.publicValue(this));body.addView(visible);
        visible.setOnCheckedChangeListener((v,checked)->{LivePreferences.setPublicValue(this,checked);LiveAlarmService.displayChanged();});
        LevelUi.help(body,AppText.tr("Zur Live-Anzeige"),AppText.tr("Android bestimmt Größe und Sichtbarkeit der Live-Anzeige. Die Darstellung kann sich je nach Gerät unterscheiden."));
        TextView status=LevelUi.text(body,"",14);
        LevelUi.button(body,AppText.tr("Anzeige ohne Alarme starten"),v->{
            if(!SystemReadiness.read(this).ready()){status.setText(AppText.tr("Bitte zuerst die Android-Freigaben prüfen."));return;}
            try{LivePreferences.set(this,true,false);startForegroundService(new Intent(this,LiveAlarmService.class));status.setText(AppText.tr("Anzeige angefordert"));}
            catch(RuntimeException e){LivePreferences.set(this,false,false);status.setText(AppText.tr("Hintergrundstart fehlgeschlagen"));}
        });
        LevelUi.button(body,AppText.tr("Hintergrundbetrieb beenden"),v->{LivePreferences.set(this,false,false);stopService(new Intent(this,LiveAlarmService.class));status.setText(AppText.tr("Hintergrundbetrieb beendet"));});
        LevelUi.navigation(body,AppText.tr("Android-Freigaben"),v->startActivity(new Intent(this,ReadinessActivity.class)));
    }
}
