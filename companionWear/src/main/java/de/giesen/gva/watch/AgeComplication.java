// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;
import androidx.wear.protolayout.expression.DynamicBuilders.*;
import java.time.Instant;

/** Native time-dependent numeric text; no one-minute timeline expiry or app wake-ups. */
public final class AgeComplication extends ComplicationDataSourceService {
    @Override public void onComplicationRequest(ComplicationRequest request,ComplicationRequestListener listener) {
        WatchStore.Entry entry=WatchStore.read(this);long now=System.currentTimeMillis();
        try {
            listener.onComplicationData(request.getComplicationType()==ComplicationType.SHORT_TEXT?
                    ageData(entry==null?null:entry.snapshot,now,entry!=null&&entry.clockValid(this,now)):
                    new NoDataComplicationData());
        }catch(android.os.RemoteException e){/* Closed watch face; never log display data. */}
    }
    @Override public ComplicationData getPreviewData(ComplicationType type) {
        return type==ComplicationType.SHORT_TEXT?text(new PlainComplicationText.Builder("0").build()):null;
    }
    ComplicationData ageData(WatchSnapshot s,long now,boolean trustedClock) {
        ComplicationText value=new PlainComplicationText.Builder("—").build();
        if(s!=null&&trustedClock&&now>=s.received&&now-s.received<=WatchSnapshot.WINDOW&&Build.VERSION.SDK_INT>=33)
            value=new DynamicComplicationText(expression(s.received,DynamicInstant.platformTimeWithSecondsPrecision()),"—");
        return text(value);
    }
    static DynamicString expression(long received,DynamicInstant now) {
        // Ceiling prevents the platform's seconds precision from advancing a minute early.
        DynamicInstant start=DynamicInstant.withSecondsPrecision(Instant.ofEpochSecond((received+999)/1000));
        DynamicInt32 minutes=start.durationUntil(now).toIntMinutes();
        return DynamicString.onCondition(minutes.gte(0).and(minutes.lte(120)))
                .use(minutes.format()).elseUse("—");
    }
    private ComplicationData text(ComplicationText value) {
        PendingIntent tap=PendingIntent.getActivity(this,0,new Intent(this,WatchActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new ShortTextComplicationData.Builder(value,
                new PlainComplicationText.Builder(WatchConsent.text("Alter in Minuten","Age in minutes")).build())
                .setTapAction(tap).build();
    }
}
