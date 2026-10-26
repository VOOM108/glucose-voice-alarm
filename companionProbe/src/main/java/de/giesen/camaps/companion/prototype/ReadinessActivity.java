// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.service.notification.NotificationListenerService;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;

public final class ReadinessActivity extends UiActivity {
    private TextView status,result,details,nativeStatus;
    private Button enable,pauseButton,resume,stop;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable() { @Override public void run() { render(); handler.postDelayed(this,2000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(!SafetyConsent.ready(this))return; LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish()); LevelUi.text(body,AppText.tr("Betrieb"),28);
        status=LevelUi.text(body,"",21);
        enable=LevelUi.button(body,AppText.tr("Live-Alarme einschalten"),v -> next());
        result=LevelUi.text(body,"",15);
        pauseButton=LevelUi.button(body,AppText.tr("Alle eigenen Ausgaben pausieren …"),v -> pause());
        resume=LevelUi.button(body,AppText.tr("Pause beenden"),v -> { LivePreferences.pauseUntil(this,0); LiveAlarmService.pauseChanged(); render(); });
        stop=LevelUi.button(body,AppText.tr("Hintergrundbetrieb beenden"),v -> {
            LivePreferences.set(this,false,false); stopService(new Intent(this,LiveAlarmService.class)); render();
        });
        LevelUi.text(body,AppText.tr("Sperrbildschirm und Statusleiste"),21);
        CheckBox nativeDisplay=LevelUi.check(body,AppText.tr("Große Live-Anzeige verwenden"),LivePreferences.nativeDisplay(this));
        nativeDisplay.setOnCheckedChangeListener((button,value) -> { LivePreferences.nativeDisplay(this,value); LiveAlarmService.displayChanged(); render(); });
        CheckBox publicValue=LevelUi.check(body,AppText.tr("Wert auf dem gesperrten Telefon sichtbar"),LivePreferences.publicValue(this));
        publicValue.setOnCheckedChangeListener((button,value) -> { LivePreferences.setPublicValue(this,value); LiveAlarmService.displayChanged(); });
        nativeStatus=LevelUi.text(body,"",15);
        LevelUi.button(body,AppText.tr("Live-Anzeige in Android öffnen"),v -> DisplayDiagnostics.openSettings(this));
        LevelUi.button(body,AppText.tr("Anzeige ohne Alarme starten"),v -> start(false));
        final LinearLayout advanced=new LinearLayout(this); advanced.setOrientation(LinearLayout.VERTICAL); advanced.setVisibility(View.GONE);
        DisclosureRow.add(body,AppText.tr("Details und Systemfreigaben"),advanced); body=advanced;
        details=LevelUi.text(body,"",14);
        LevelUi.button(body,AppText.tr("Benachrichtigungszugriff öffnen"),v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LevelUi.button(body,AppText.tr("Eigene Benachrichtigungen erlauben"),v -> {
            if (Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17);
            else openDetails();
        });
        LevelUi.button(body,AppText.tr("Ausnahme von Akku-Optimierung anfordern"),v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()))); }
            catch (ActivityNotFoundException e) { openDetails(); }
        });
        LevelUi.button(body,AppText.tr("Android-App-Einstellungen / Hintergrundnutzung"),v -> openDetails());
        LevelUi.text(body,AppText.tr("Android bestimmt Größe und Sichtbarkeit der Live-Anzeige. Die Darstellung kann sich je nach Gerät unterscheiden."),14);
        LevelUi.button(body,AppText.tr("Live-Alarme ausschalten, Anzeige behalten"),v -> {
            LivePreferences.set(this,LivePreferences.monitor(this),false); LiveAlarmService.settingsChanged(); render();
        });
        LevelUi.text(body,AppText.tr("Bei Erholung lassen sich Wiederholungen je Hoch-/Niedrigstufe aussetzen. Die optionale eigene Untergrenze ist noch nicht umgesetzt. Eine Akku-Ausnahme garantiert keinen ununterbrochenen Betrieb."),14);
        LevelUi.text(body,AppText.tr("Nach einem App-Update sind Live-Alarme erneut einzuschalten. Stufen, Töne und gespeicherte Pausen bleiben erhalten. Zeitbasis ist der Empfang."),14);
        if (savedRequest(state) && !LiveAlarmService.live()) next();
    }
    private boolean savedRequest(Bundle state) { return state==null && getIntent().getBooleanExtra("request_live",false); }
    private void next() {
        try {
            switch (SystemReadiness.read(this).nextStep()) {
                case RECEIVER:
                    getSharedPreferences(CamApsListener.PREFS,MODE_PRIVATE).edit().putBoolean(CamApsListener.ENABLED,true).commit();
                    ComponentName component=new ComponentName(this,CamApsListener.class);
                    getPackageManager().setComponentEnabledSetting(component,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP);
                    ProbeState.latest=null; ProbeState.vectorCandidate=null; ProbeState.status=AppText.tr("Warte auf ein neues CamAPS-Update");
                    NotificationListenerService.requestRebind(component); render(); return;
                case ACCESS: startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return;
                case NOTIFICATIONS:
                    if (Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17); else openDetails();
                    return;
                case BATTERY:
                    try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()))); }
                    catch (ActivityNotFoundException e) { openDetails(); }
                    return;
                case BACKGROUND: openDetails(); return;
                default: break;
            }
            if (LiveAlarmService.live()) { LivePreferences.set(this,true,false); LiveAlarmService.settingsChanged(); render(); }
            else start(true);
        } catch (RuntimeException e) { result.setText(AppText.tr("Android konnte den nächsten Schritt nicht öffnen. Weitere Wege stehen unter Details und Systemfreigaben.")); }
    }
    private void openDetails() { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
    private void start(boolean alarms) {
        if (!SystemReadiness.read(this).ready()) { result.setText(AppText.tr("Bitte zuerst die oben fehlenden Freigaben und den CamAPS-Empfang einschalten.")); return; }
        try {
            if(alarms) {
                LiveActivation.Problem problem=LiveActivation.start(this);
                result.setText(problem==null?AppText.tr("Start angefordert. Der Betriebsstatus oben zeigt, ob der Dienst läuft."):problem.text);
                render(); return;
            }
            LivePreferences.set(this,true,alarms);
            startForegroundService(new Intent(this,LiveAlarmService.class));
            result.setText(AppText.tr("Start angefordert. Der Betriebsstatus oben zeigt, ob der Dienst läuft."));
        } catch (RuntimeException e) { LivePreferences.set(this,false,false); result.setText(AppText.tr("Hintergrundstart fehlgeschlagen. Einstellungen bleiben erhalten.")); }
        render();
    }
    private void pause() {
        EditText minutes=new EditText(this); minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); minutes.setHint(AppText.tr("Minuten"));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(AppText.tr("Alle eigenen Alarme pausieren")).setView(minutes)
                .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Pausieren"),null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long value=Long.parseLong(minutes.getText().toString().trim()); if (value<1 || value>Integer.MAX_VALUE/60000) throw new NumberFormatException();
                LivePreferences.pauseUntil(this,Math.addExact(System.currentTimeMillis(),value*60000)); LiveAlarmService.pauseChanged(); dialog.dismiss(); render();
            } catch (ArithmeticException | NumberFormatException e) { minutes.setError(AppText.tr("Bitte eine positive ganze Minutenzahl eingeben")); }
        })); dialog.show();
    }
    private void render() {
        if (status==null) return;
        SystemReadiness ready=SystemReadiness.read(this); boolean live=LiveAlarmService.live();
        boolean paused=LivePreferences.pauseUntil(this)>System.currentTimeMillis();
        status.setText(SystemReadiness.operatingTitle(this)+"\n"+(ready.ready()?LiveAlarmService.running()
                ?LiveAlarmService.status:AppText.tr("Alle Systemfreigaben sind erteilt."):ready.blockingReason()));
        enable.setText(ready.ready()?live?AppText.tr("Live-Alarme ausschalten"):AppText.tr("Live-Alarme einschalten"):ready.nextAction());
        enable.setEnabled(true);
        pauseButton.setVisibility(live && !paused?View.VISIBLE:View.GONE); resume.setVisibility(paused?View.VISIBLE:View.GONE);
        stop.setVisibility(LiveAlarmService.running() || LivePreferences.monitor(this)?View.VISIBLE:View.GONE);
        details.setText(ready.summary()+"\n\n"+MetricNotification.capability(this)+"\n\n"+LiveAlarmService.audioState());
        nativeStatus.setText(DisplayDiagnostics.status(this)); DisplayDiagnostics.write(this);
    }
    @Override public void onResume() { super.onResume(); if(!SafetyConsent.ready(this))return; handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
