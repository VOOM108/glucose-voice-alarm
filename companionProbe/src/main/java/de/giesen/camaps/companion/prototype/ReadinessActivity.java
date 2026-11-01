// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

/** Each row represents a checked Android permission, never inferred physical delivery. */
public final class ReadinessActivity extends UiActivity {
    private LinearLayout rows;private TextView summary;private String lastState;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->finish());
        LevelUi.text(body,AppText.tr("Android-Freigaben"),28);summary=LevelUi.text(body,"",16);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
        LevelUi.help(body,AppText.tr("Was wird geprüft?"),AppText.tr("Die Häkchen bestätigen die Android-Freigaben. Ob Du eine Ausgabe hörst oder die Anzeige auf Deinem Sperrbildschirm siehst, prüfst Du mit einem Test am Gerät."));render();
    }
    private void row(String id,String title,Boolean ok,String text,Runnable open){
        LinearLayout card=DashboardUi.card(this,rows);card.setTag("permission_"+id);
        TextView state=LevelUi.text(card,(ok==null?"— ":ok?"✓ ":"! ")+title,18);state.setTag("permission_state_"+id);
        state.setTextColor(ok==null?AppAppearance.colors(this).muted:ok?(AppAppearance.dark(this)?0xff8edbb0:0xff1a7043):AppAppearance.colors(this).error);
        LevelUi.text(card,text,14);
        Button action=LevelUi.navigation(card,AppText.tr("Android-Einstellung öffnen"),v->{try{open.run();}catch(RuntimeException e){openDetails();}});action.setContentDescription(title+" · "+AppText.tr("Android-Einstellung öffnen"));
    }
    private void render(){if(rows==null)return;SystemReadiness s=SystemReadiness.read(this);
        String key=s.access+":"+s.notifications+":"+s.batteryExempt+":"+s.restricted+":"+DisplayDiagnostics.promotionAllowed(this)+":"+LivePreferences.nativeDisplay(this);
        if(key.equals(lastState))return;lastState=key;rows.removeAllViews();
        summary.setText(s.access && s.notifications && s.batteryExempt && !s.restricted?AppText.tr("Benötigte Android-Freigaben erteilt"):AppText.tr("Bitte die markierten Freigaben prüfen"));
        row("access",AppText.tr("Benachrichtigungszugriff"),s.access,AppText.tr(s.access?"Erlaubt":"Freigabe fehlt"),()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        row("notifications",AppText.tr("Eigene Benachrichtigungen"),s.notifications,AppText.tr(s.notifications?"Erlaubt":"Von Android gesperrt"),()->{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},17);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        row("battery",AppText.tr("Akku-Optimierung"),s.batteryExempt,AppText.tr(s.batteryExempt?"Ausnahme erteilt":"Ausnahme fehlt"),()->startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()))));
        row("background",AppText.tr("Hintergrundnutzung"),!s.restricted,AppText.tr(s.restricted?"Eingeschränkt":"Nicht eingeschränkt"),this::openDetails);
        Boolean promoted=null;if(Build.VERSION.SDK_INT>=37 && LivePreferences.nativeDisplay(this))try{promoted=(Boolean)NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(getSystemService(NotificationManager.class));}catch(ReflectiveOperationException | RuntimeException | LinkageError e){/* Explicit unknown. */}
        row("live",AppText.tr("Große Live-Anzeige"),promoted,AppText.tr(!LivePreferences.nativeDisplay(this)?"Nicht verwendet":Build.VERSION.SDK_INT<37?"Auf dieser Android-Version nicht verfügbar":promoted==null?"Nicht prüfbar":promoted?"Von Android erlaubt":"Freigabe fehlt"),()->DisplayDiagnostics.openSettings(this));
    }
    private void openDetails(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    @Override public void onResume(){super.onResume();if(SafetyConsent.ready(this)){lastState=null;render();handler.postDelayed(refresh,2000);}}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
