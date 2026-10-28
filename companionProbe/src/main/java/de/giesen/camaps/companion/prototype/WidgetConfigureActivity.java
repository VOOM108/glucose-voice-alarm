// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public final class WidgetConfigureActivity extends UiActivity {
    @Override public void onCreate(Bundle saved){
        setResult(RESULT_CANCELED);super.onCreate(saved);if(!SafetyConsent.ready(this))return;
        int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);if(!HomeWidget.owns(this,id)){finish();return;}
        LinearLayout body=LevelUi.screen(this);LevelUi.text(body,HomeWidget.tr("Widget","Widget"),28);
        CheckBox controls=LevelUi.check(body,HomeWidget.tr("Schalterzeile anzeigen","Show toggle row"),getSharedPreferences(HomeWidget.PREFS,0).getBoolean("controls_"+id,true));
        controls.setId(R.id.widget_controls);
        LevelUi.text(body,HomeWidget.tr("Fünf kleine Schalter in einer Zeile, auch bei niedriger Höhe. Hell/Dunkel folgt der App-Einstellung.","Five small toggles in one row, including at a low height. Light/dark follows the app setting."),17);
        LevelUi.text(body,HomeWidget.tr("Die Kurve zeigt je nach Breite 30 Minuten, eine oder zwei Stunden. Der Zeitraum steht direkt darunter.","Depending on width, the chart shows 30 minutes, one or two hours. Its window is labeled directly below."),16);
        LevelUi.text(body,HomeWidget.tr("Ton · Alarm-Sprache · Wertansagen · Vibration · eigene Ausgabe. Antippen der übrigen Fläche öffnet das Dashboard.","Sound · alarm speech · reading speech · vibration · own output. Tapping elsewhere opens the dashboard."),16);
        LevelUi.text(body,HomeWidget.tr("Die Zeit ist der letzte Empfang. Android kann Widget-Aktualisierungen verzögern; dies ist keine Alarmanzeige.","The timestamp is the last receipt. Android may defer widget updates; this is not an alarm display."),16);
        LevelUi.button(body,AppText.tr("Speichern"),v->{if(!getSharedPreferences(HomeWidget.PREFS,0).edit().putBoolean("controls_"+id,controls.isChecked()).commit())return;HomeWidget.changed(this);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish();});
        LevelUi.button(body,AppText.tr("Abbrechen"),v->finish());
    }
}
