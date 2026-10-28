// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.os.*;
import android.widget.*;
public final class WatchSettingsActivity extends UiActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView displayStatus,alertStatus;
    private final Runnable refresh=new Runnable(){public void run(){renderStatus();handler.postDelayed(this,2000);}};
    private void renderStatus(){displayStatus.setText(WatchPublisher.status.line(WatchPublisher.tr("Anzeige","Display")));alertStatus.setText(WatchAlertPublisher.status.line(WatchPublisher.tr("Alarme","Alerts")));}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.button(body,AppText.tr("Zurück"),v->finish());
        LevelUi.text(body,WatchPublisher.tr("Uhr","Watch"),28);
        CheckBox enabled=LevelUi.check(body,WatchPublisher.tr("Werte an Uhr senden","Send readings to watch"),WatchPublisher.enabled(this));
        enabled.setOnCheckedChangeListener((b,value)->WatchPublisher.set(this,value));
        LevelUi.text(body,WatchPublisher.tr("Anzeige: Wert, CamAPS-Pfeil, Wertzeit, Delta und zwei Stunden Verlauf. Keine Bedienung der Pumpe.",
                "Display: reading, CamAPS arrow, receipt time, delta and two hours of history. No pump controls."),16);
        LevelUi.button(body,WatchPublisher.tr("Anzeige jetzt senden","Send display now"),v->WatchPublisher.changed(this));
        displayStatus=LevelUi.text(body,"",15);displayStatus.setTag("watch_display_status");
        CheckBox alarms=LevelUi.check(body,WatchPublisher.tr("Alarme an Uhr senden","Send alerts to watch"),WatchAlertPublisher.enabled(this));
        alarms.setOnCheckedChangeListener((b,value)->WatchAlertPublisher.set(this,value));
        LevelUi.text(body,WatchPublisher.tr("Alarme und Datenlückenwarnungen mit Vibration. In GVA Watch unter Alarme ebenfalls freigeben. Keine normalen Wertmeldungen; ohne Verbindung können Alarme auf der Uhr fehlen.",
                "Alarms and data-gap warnings with vibration. Also enable Alerts in GVA Watch. No routine reading notifications; watch alerts may be missed without a connection."),16);
        LevelUi.button(body,WatchPublisher.tr("Uhrenalarm testen","Test watch alert"),v->{if(WatchAlertPublisher.enabled(this))WatchAlertPublisher.test(this);else Toast.makeText(this,WatchPublisher.tr("Zuerst Alarme an Uhr senden einschalten","Enable sending alerts to watch first"),Toast.LENGTH_SHORT).show();});
        alertStatus=LevelUi.text(body,"",15);alertStatus.setTag("watch_alert_status");
        LevelUi.text(body,WatchPublisher.tr("Letzter Versandversuch. Anzeige und Vibration werden nicht zurückgemeldet.","Last send attempt. Display and vibration are not acknowledged."),14);
        LevelUi.text(body,WatchPublisher.tr("Benötigt die passende GVA-Uhren-App mit derselben Signatur. Übertragung nur an eine als nah verbunden gemeldete Uhr; bei Trennung kein Versand und keine Warteschlange. Das Zifferblatt kann verzögert aktualisieren.",
                "Requires the matching GVA watch app signed with the same key. Transfers only to a connected nearby watch; no delivery queue when disconnected. Watch face updates may be delayed."),16);
        renderStatus();
    }
    @Override public void onResume(){super.onResume();if(displayStatus!=null)handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
