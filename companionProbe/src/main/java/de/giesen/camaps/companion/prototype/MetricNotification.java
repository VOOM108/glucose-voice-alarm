// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Notification;
import android.os.Build;
import android.content.Context;
import android.app.NotificationManager;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Public API-37 reflection keeps the pinned original toolchain usable; no hidden APIs or third-party code loading. */
final class MetricNotification {
    // Android 17's Metric constructor rejects a blank label, even though the public reference only says non-null.
    static String label(long receivedWall) { return new SimpleDateFormat("HH:mm",Locale.GERMANY).format(new Date(receivedWall)); }
    static String trendTitle(NotificationParser.Trend trend) {
        switch (trend) {
            case DOUBLE_UP: return AppText.tr("Doppelt aufwärts");
            case UP: return AppText.tr("Schnell steigend");
            case UP_RIGHT: return AppText.tr("Steigend");
            case FLAT: return AppText.tr("Stabil");
            case DOWN_RIGHT: return AppText.tr("Fallend");
            case DOWN: return AppText.tr("Schnell fallend");
            case DOUBLE_DOWN: return AppText.tr("Doppelt abwärts");
            default: return AppText.tr("Trend offen");
        }
    }
    static boolean apply(Notification.Builder builder,int value,NotificationParser.Trend trend,long receivedWall) {
        return apply(builder,value,trend,receivedWall,GlucoseUnit.MGDL);
    }
    static boolean apply(Notification.Builder builder,double value,NotificationParser.Trend trend,long receivedWall,GlucoseUnit unit) {
        if (Build.VERSION.SDK_INT<37) return false;
        try {
            Class<?> metric=Class.forName("android.app.Notification$Metric");
            Class<?> metricValue=Class.forName("android.app.Notification$Metric$MetricValue");
            Class<?> fixedText=Class.forName("android.app.Notification$Metric$FixedText");
            Class<?> style=Class.forName("android.app.Notification$MetricStyle");
            String display=unit.number(value,Locale.getDefault())+(trend==NotificationParser.Trend.UNKNOWN?"":" "+trend.symbol);
            Object rendered=fixedText.getConstructor(CharSequence.class).newInstance(display);
            String time=label(receivedWall);
            Object entry=metric.getConstructor(metricValue,CharSequence.class).newInstance(rendered,time);
            Object renderedStyle=style.getConstructor().newInstance(); style.getMethod("addMetric",metric).invoke(renderedStyle,entry);
            style.getMethod("setCriticalMetric",int.class).invoke(renderedStyle,0);
            builder.setStyle((Notification.Style)renderedStyle);
            // An empty ordinary title becomes the app name on the Pixel. Use a short trend word.
            // The required metric label remains the clock; value/arrow stay in the large metric.
            builder.setContentTitle(trendTitle(trend)).setContentText(null).setShowWhen(false).setUsesChronometer(false);
            Notification.Builder.class.getMethod("setShortCriticalText",String.class).invoke(builder,display);
            Notification.Builder.class.getMethod("setRequestPromotedOngoing",boolean.class).invoke(builder,true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return false; }
    }
    static String capability(Context context) {
        if (Build.VERSION.SDK_INT<37) return AppText.tr("Native Messwertanzeige: Standardbenachrichtigung (MetricStyle erfordert Android 17)");
        try {
            Notification.Builder builder=new Notification.Builder(context,LiveNotifications.STATUS_CHANNEL)
                    .setSmallIcon(R.drawable.ic_xcam_status).setContentTitle(AppText.tr("XCam-Anzeigepruefung")).setOngoing(true);
            if (!apply(builder,123,NotificationParser.Trend.FLAT,System.currentTimeMillis())) return AppText.tr("Native Messwertanzeige: API nicht verfügbar; Standardanzeige");
            builder.build(); // Fixed synthetic object, never posted and never a real reading.
            boolean allowed=(Boolean)NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(context.getSystemService(NotificationManager.class));
            return AppText.tr("Native Messwertanzeige: baubar\nLive Updates: ")+(allowed?AppText.tr("von Android erlaubt"):AppText.tr("in Android nicht erlaubt"))+AppText.tr("\nDauerhaftes AOD: am Gerät zu bestätigen");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return AppText.tr("Native Messwertanzeige: Prüfung nicht erfolgreich; Standardanzeige"); }
    }
    private MetricNotification() {}
}
