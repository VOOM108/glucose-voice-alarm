// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;

final class SystemReadiness {
    enum Step { RECEIVER, ACCESS, NOTIFICATIONS, BATTERY, BACKGROUND, READY }
    final boolean receiver,access,notifications,batteryExempt,restricted;
    SystemReadiness(boolean receiver,boolean access,boolean notifications,boolean batteryExempt,boolean restricted) {
        this.receiver=receiver; this.access=access; this.notifications=notifications; this.batteryExempt=batteryExempt; this.restricted=restricted;
    }
    static SystemReadiness read(Context context) {
        boolean access=false;
        ComponentName component=new ComponentName(context,CamApsListener.class);
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT>=27) access=manager.isNotificationListenerAccessGranted(component);
        else {
            String enabled=Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
            if (enabled!=null) for (String value:enabled.split(":")) if (component.equals(ComponentName.unflattenFromString(value))) access=true;
        }
        return new SystemReadiness(context.getSharedPreferences(CamApsListener.PREFS,Context.MODE_PRIVATE).getBoolean(CamApsListener.ENABLED,false),
                access,manager.areNotificationsEnabled(),context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName()),
                Build.VERSION.SDK_INT>=28 && context.getSystemService(ActivityManager.class).isBackgroundRestricted());
    }
    boolean ready() { return receiver && access && notifications && batteryExempt && !restricted; }
    Step nextStep() {
        return !receiver?Step.RECEIVER:!access?Step.ACCESS:!notifications?Step.NOTIFICATIONS:
                !batteryExempt?Step.BATTERY:restricted?Step.BACKGROUND:Step.READY;
    }
    String nextAction() {
        switch (nextStep()) {
            case RECEIVER: return AppText.tr("CamAPS-Empfang einschalten");
            case ACCESS: return AppText.tr("Benachrichtigungszugriff erlauben");
            case NOTIFICATIONS: return AppText.tr("Eigene Benachrichtigungen erlauben");
            case BATTERY: return AppText.tr("Akku-Ausnahme erteilen");
            case BACKGROUND: return AppText.tr("Hintergrundnutzung erlauben");
            default: return AppText.tr("Live-Alarme einschalten");
        }
    }
    String blockingReason() {
        switch (nextStep()) {
            case RECEIVER: return AppText.tr("Der CamAPS-Empfang ist ausgeschaltet.");
            case ACCESS: return AppText.tr("Androids Benachrichtigungszugriff fehlt noch.");
            case NOTIFICATIONS: return AppText.tr("Android blockiert die Benachrichtigungen dieser App.");
            case BATTERY: return AppText.tr("Die Ausnahme von der Akku-Optimierung fehlt noch.");
            case BACKGROUND: return AppText.tr("Android schränkt die Hintergrundnutzung ein.");
            default: return AppText.tr("Alle benötigten Systemfreigaben sind erteilt.");
        }
    }
    static String operatingTitle(Context context) {
        if (LiveAlarmService.live()) return LivePreferences.pauseUntil(context)>System.currentTimeMillis()
                ?AppText.tr("Eigene Ausgaben pausiert"):AppText.tr("Live-Alarmdienst läuft");
        if (LiveAlarmService.running()) return LivePreferences.alarms(context)?AppText.tr("Alarmausgabe gesperrt"):AppText.tr("Nur Hintergrundanzeige aktiv");
        return LivePreferences.alarms(context)?AppText.tr("Live-Alarmdienst läuft nicht"):AppText.tr("Live-Alarme ausgeschaltet");
    }
    String summary() {
        return AppText.tr("CamAPS-Empfang: ")+(receiver?AppText.tr("eingeschaltet"):AppText.tr("ausgeschaltet"))
                +"\nBenachrichtigungszugriff: "+(access?AppText.tr("erlaubt"):AppText.tr("fehlt"))
                +AppText.tr("\nEigene Benachrichtigungen: ")+(notifications?AppText.tr("erlaubt"):AppText.tr("gesperrt"))
                +AppText.tr("\nAkku-Optimierung: ")+(batteryExempt?AppText.tr("Ausnahme erteilt"):AppText.tr("Ausnahme fehlt"))
                +"\nHintergrundnutzung: "+(restricted?AppText.tr("eingeschränkt"):AppText.tr("nicht eingeschränkt"));
    }
}
