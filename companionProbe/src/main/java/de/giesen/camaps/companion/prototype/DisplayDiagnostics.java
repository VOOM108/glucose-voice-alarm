// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Own notification metadata only. Never read or export its title, value, time, text, or bitmap. */
final class DisplayDiagnostics {
    static final String FILE="native-display.txt";
    private static String flag(Object receiver,String method) {
        try { return (Boolean)receiver.getClass().getMethod(method).invoke(receiver)?"ja":"nein"; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return "unbekannt"; }
    }
    static String report(android.content.Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class); Notification posted=null;
        try { for (StatusBarNotification item:manager.getActiveNotifications()) if (item.getId()==LiveNotifications.STATUS_ID) { posted=item.getNotification(); break; } }
        catch (RuntimeException e) { /* Unknown remains explicit. */ }
        String synthetic="nicht verfuegbar";
        if (Build.VERSION.SDK_INT>=37) {
            try {
                Notification.Builder builder=new Notification.Builder(context,LiveNotifications.STATUS_CHANNEL)
                        .setSmallIcon(R.drawable.ic_xcam_status).setContentTitle("Anzeigepruefung").setOngoing(true);
                boolean styled=MetricNotification.apply(builder,123,NotificationParser.Trend.FLAT,System.currentTimeMillis());
                Notification sample=builder.build();
                synthetic=styled && AppText.tr("Stabil").contentEquals(sample.extras.getCharSequence(Notification.EXTRA_TITLE))
                        && "123".equals(sample.extras.getString("android.shortCriticalText"))
                        && "ja".equals(flag(sample,"hasPromotableCharacteristics"))?"bestanden":"fehlgeschlagen";
            } catch (RuntimeException | LinkageError e) { synthetic="fehlgeschlagen"; }
        }
        String metric="unbekannt",promoted="unbekannt";
        if (posted!=null) {
            metric="android.app.Notification$MetricStyle".equals(posted.extras.getString("android.template"))?"ja":"nein";
            try { int mask=Notification.class.getField("FLAG_PROMOTED_ONGOING").getInt(null); promoted=(posted.flags&mask)!=0?"ja":"nein"; }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { /* Public API absent. */ }
        }
        return "Prototyp "+BuildConfig.VERSION_NAME+"\nNative Anzeige: nur Statusflags\nAndroid-API: "+Build.VERSION.SDK_INT
                +"\nSynthetische Metric-Pruefung: "+synthetic+"\nEigene Betriebsbenachrichtigung: "+(posted==null?"fehlt":"vorhanden")
                +"\nMetricStyle aktiv: "+metric+"\nHervorhebung angefordert: "+(posted==null?"unbekannt":flag(posted,"isRequestPromotedOngoing"))
                +"\nHervorhebung geeignet: "+(posted==null?"unbekannt":flag(posted,"hasPromotableCharacteristics"))
                +"\nHervorhebung erlaubt: "+flag(manager,"canPostPromotedNotifications")+"\nTatsaechlich hervorgehoben: "+promoted
                +"\nDatenbasis: keine Werte oder Benachrichtigungstexte\n";
    }
    static void write(android.content.Context context) {
        try (FileOutputStream output=new FileOutputStream(new File(context.getCacheDir(),FILE))) { output.write(report(context).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException | RuntimeException e) { /* Optional bounded diagnostic. */ }
    }
    static String status(android.content.Context context) {
        if (!LivePreferences.nativeDisplay(context)) return "Große Live-Anzeige: ausgeschaltet";
        String allowed=flag(context.getSystemService(NotificationManager.class),"canPostPromotedNotifications");
        return "Große Live-Anzeige: "+("ja".equals(allowed)?"von Android erlaubt":"nein".equals(allowed)?"bitte in Android freigeben":"Systemunterstützung noch ungeprüft");
    }
    static void openSettings(Activity activity) {
        try {
            String action=(String)Settings.class.getField("ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS").get(null);
            activity.startActivity(new Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName()));
        }
    }
    private DisplayDiagnostics() {}
}
