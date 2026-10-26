// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Notification;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.VectorDrawable;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.io.IOException;
import static de.giesen.camaps.companion.prototype.TrendDiagnostics.Source.*;

/** Restricted to ongoing CamAPS mg/dl notifications. No upstream Application/collector starts. */
public final class CamApsListener extends NotificationListenerService {
    static final String PREFS = "probe", ENABLED = "receiver_enabled";
    private ObservationStore store;
    private final ReceiptWindow receiptOrder=new ReceiptWindow();

    @Override public void onCreate() {
        AppText.configure(getResources().getConfiguration().getLocales().get(0));
        super.onCreate(); store = new ObservationStore(this);
        RuntimeStateBridge.refresh(getFilesDir(),RuntimeClock.read(this));
    }
    @Override public void onDestroy() { store.close(); super.onDestroy(); }
    @Override public void onListenerConnected() {
        receiptOrder.clear(); LiveAlarmService.sourceDisconnected();
        ProbeState.latest = null;
        ProbeState.vectorCandidate = null;
        AlarmProbe.clear(AlarmProbe.Ingress.WAITING);
        coreReport();
        clearDiagnosticCache();
        ProbeState.diagnostics = "Verbunden; warte auf ein neues CamAPS-Update für die Trenddiagnose.";
        ProbeState.status = "Verbunden; warte auf ein neues CamAPS-Update";
        if (!enabled()) requestUnbind();
        // Never import getActiveNotifications() as new measurements.
    }
    @Override public void onListenerDisconnected() {
        receiptOrder.clear(); LiveAlarmService.sourceDisconnected();
        ProbeState.latest = null;
        ProbeState.vectorCandidate = null;
        AlarmProbe.clear(AlarmProbe.Ingress.OFF);
        coreReport();
        clearDiagnosticCache();
        ProbeState.diagnostics = "Kein verbundener Benachrichtigungsempfang.";
        ProbeState.status = "Benachrichtigungsempfang nicht verbunden";
    }
    private boolean enabled() { return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(ENABLED, false); }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!enabled() || !NotificationParser.acceptsPackage(sbn.getPackageName()) || !sbn.isOngoing()) return;
        de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.Clock receiptClock=RuntimeClock.read(this);
        if(receiptOrder.older(sbn.getPostTime(),receiptClock)) return;
        ProbeState.vectorCandidate = null;
        Notification n = sbn.getNotification();
        VectorReference vectors=new VectorReference();
        String profile=sourceProfile(sbn.getPackageName());
        List<String> values = new ArrayList<>(), trends = new ArrayList<>();
        TrendDiagnostics diagnostic = new TrendDiagnostics();
        ReferenceAudit.Match auditMatch=ReferenceAudit.Match.NO_CANDIDATE;
        String auditExact=null,auditShape=null;
        diagnostic.icons(n.getSmallIcon()!=null, n.getLargeIcon()!=null);
        Bundle extras = n.extras;
        if (extras != null) {
            try {
                extra(extras, Notification.EXTRA_TITLE, TITLE, true, values, trends, diagnostic);
                extra(extras, Notification.EXTRA_TEXT, TEXT, true, values, trends, diagnostic);
                extra(extras, Notification.EXTRA_BIG_TEXT, BIG_TEXT, true, values, trends, diagnostic);
                extra(extras, Notification.EXTRA_SUB_TEXT, SUB_TEXT, true, values, trends, diagnostic);
                // Additional fields are trend-only: they cannot break the working value selection.
                extra(extras, Notification.EXTRA_INFO_TEXT, INFO_TEXT, false, values, trends, diagnostic);
                extra(extras, Notification.EXTRA_SUMMARY_TEXT, SUMMARY_TEXT, false, values, trends, diagnostic);
                extra(extras, Notification.EXTRA_TITLE_BIG, TITLE_BIG, false, values, trends, diagnostic);
                CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (lines != null) for (int i=0;i<lines.length;i++) {
                    if (i>=64) { diagnostic.limitReached(); break; }
                    text(lines[i], TEXT_LINES, false, true, values, trends, diagnostic);
                }
                int inspected=0;
                for (String key:extras.keySet()) {
                    if (++inspected>64) { diagnostic.limitReached(); break; }
                    if (standardTextKey(key)) continue;
                    Object content=extras.get(key);
                    // Unknown keys and their text are never copied into the shared summary or parser.
                    if (content instanceof CharSequence) diagnostic.text(OTHER_TEXT,content.toString(),false);
                }
            } catch (RuntimeException e) { diagnostic.extrasFailed(); }
        }
        RemoteViews[] remoteViews={n.contentView,n.bigContentView,n.headsUpContentView};
        TrendDiagnostics.Source[] sources={CONTENT_VIEW,BIG_CONTENT_VIEW,HEADS_UP_VIEW};
        int[] budget={256};
        for (int i=0;i<remoteViews.length;i++) {
            if (remoteViews[i]==null) continue;
            diagnostic.remote(sources[i],false);
            try {
                collect(remoteViews[i].apply(this,null),sources[i],i<2,values,trends,diagnostic,budget,0,vectors,profile,sbn.getKey());
            } catch (RuntimeException e) { diagnostic.remote(sources[i],true); vectors.block(); }
        }
        NotificationParser.Result result=NotificationParser.parse(values,trends);
        if (diagnostic.hasEmbeddedVector()) vectors.block(); // Unsupported ImageSpan transforms stay unknown.
        if (diagnostic.isLimited()) {
            result=new NotificationParser.Result(result.mgdl,NotificationParser.Trend.UNKNOWN,"Trendprüfung unvollständig; Diagnose beachten");
        } else if (result.mgdl!=null) {
            ProbeState.vectorCandidate=vectors.candidate();
            if (ProbeState.vectorCandidate!=null) {
                VectorReference.Candidate candidate=ProbeState.vectorCandidate;
                android.content.SharedPreferences shapes=getSharedPreferences(VectorReference.SHAPE_PREFS,MODE_PRIVATE);
                NotificationParser.Trend exact=TrendReference.lookup(getSharedPreferences(VectorReference.PREFS,MODE_PRIVATE).getAll(),candidate.key);
                // Upgrade only a pixel-exact sample that was already confirmed by the user.
                if (candidate.shapeKey!=null && exact!=NotificationParser.Trend.UNKNOWN && !shapes.contains(candidate.shapeKey) && shapes.getAll().size()<64)
                    shapes.edit().putString(candidate.shapeKey,exact.name()).apply();
                NotificationParser.Trend shape=TrendReference.lookup(shapes.getAll(),candidate.shapeKey);
                boolean conflict=exact!=NotificationParser.Trend.UNKNOWN && shape!=NotificationParser.Trend.UNKNOWN && exact!=shape;
                NotificationParser.Trend reference=ShapeReference.resolve(exact,shape);
                auditExact=candidate.key; auditShape=candidate.shapeKey;
                auditMatch=conflict?ReferenceAudit.Match.CONFLICT:exact!=NotificationParser.Trend.UNKNOWN?ReferenceAudit.Match.EXACT:
                        shape!=NotificationParser.Trend.UNKNOWN?ReferenceAudit.Match.SHAPE:ReferenceAudit.Match.UNKNOWN;
                diagnostic.shapeReference(candidate.shapeKey!=null,exact!=NotificationParser.Trend.UNKNOWN,shape!=NotificationParser.Trend.UNKNOWN,
                        conflict || (candidate.shapeKey!=null && ShapeReference.CONFLICT.equals(shapes.getAll().get(candidate.shapeKey))));
                diagnostic.reference(true,reference!=NotificationParser.Trend.UNKNOWN);
                result=conflict?new NotificationParser.Result(result.mgdl,NotificationParser.Trend.UNKNOWN,"Widersprüchliche Trendangaben"):
                        TrendReference.combine(result,reference);
            }
        }
        ProbeState.diagnostics=diagnostic.summary(result);
        if (profile!=null && result.mgdl!=null) ArrowCoverage.observe(this,profile,result.trend);
        ReferenceAuditStore.record(this,auditMatch,auditExact,auditShape);
        try { DiagnosticCache.write(getCacheDir(),BuildConfig.VERSION_NAME,diagnostic,result); }
        catch (IOException e) { ProbeState.diagnostics+="\nPrivate ADB-Diagnose nicht verfügbar."; }
        if (result.mgdl == null) {
            LiveAlarmService.invalidCurrentSource();
            AlarmProbe.clear(AlarmProbe.Ingress.NO_VALUE);
            coreReport();
            ProbeState.latest = null;
            ProbeState.status = result.reason;
            return;
        }
        ReceiptWindow.Intake intake=receiptOrder.receive(sbn.getKey(),sbn.getPostTime(),result.mgdl,receiptClock);
        if(intake!=ReceiptWindow.Intake.ACCEPTED && intake!=ReceiptWindow.Intake.CLOCK_RESET) {
            ProbeState.status="Veraltetes oder bereits verarbeitetes Quellereignis ignoriert"; return;
        }
        try {
            boolean freshObservation = store.insert(sbn.getKey(), sbn.getPostTime(), System.currentTimeMillis(), result);
            if (!freshObservation) {
                AlarmProbe.ingress=AlarmProbe.Ingress.DUPLICATE;
                coreReport();
                ProbeState.status = "Identisches Benachrichtigungsereignis ignoriert";
                return;
            }
            if (!enabled()) return;
            java.util.Calendar calendar=java.util.Calendar.getInstance();
            AlarmProbe.receive(result.mgdl,System.currentTimeMillis(),calendar.get(java.util.Calendar.HOUR_OF_DAY)*60+calendar.get(java.util.Calendar.MINUTE));
            RuntimeStateBridge.unconfirmedInput();
            LiveAlarmService.receive(sbn.getKey(),sbn.getPostTime(),result.mgdl,result.trend);
            coreReport();
            ProbeState.latest = new ProbeState.Observation(result.mgdl, result.trend,
                    SystemClock.elapsedRealtime(), sbn.getPostTime(), sbn.getKey());
            ProbeState.status = result.reason + "; Zeitbasis: Empfang";
        } catch (RuntimeException e) {
            AlarmProbe.clear(AlarmProbe.Ingress.NO_VALUE);
            coreReport();
            ProbeState.latest = null;
            ProbeState.status = "Empfang konnte nicht lokal gespeichert werden";
        }
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if(receiptOrder.older(sbn.getPostTime(),RuntimeClock.read(this))) return;
        VectorReference.Candidate candidate=ProbeState.vectorCandidate;
        if (candidate!=null && candidate.notificationKey.equals(sbn.getKey())) ProbeState.vectorCandidate=null;
        ProbeState.Observation latest = ProbeState.latest;
        if (latest != null && latest.notificationKey.equals(sbn.getKey())) {
            receiptOrder.clear(); LiveAlarmService.sourceDisconnected();
            ProbeState.latest = null;
            AlarmProbe.clear(AlarmProbe.Ingress.WAITING);
            coreReport();
            clearDiagnosticCache();
            ProbeState.diagnostics = "CamAPS-Benachrichtigung entfernt; warte auf ein neues Ereignis.";
            ProbeState.status = "CamAPS-Benachrichtigung entfernt";
        }
    }
    private void clearDiagnosticCache() {
        try { DiagnosticCache.clear(getCacheDir()); }
        catch (IOException e) { ProbeState.diagnostics="Private ADB-Diagnose konnte nicht geleert werden."; }
    }
    private void coreReport() {
        try { DiagnosticCache.writeCore(getCacheDir(),BuildConfig.VERSION_NAME); }
        catch (IOException e) { /* A failed private report never enables output. */ }
    }
    private String sourceProfile(String packageName) {
        try {
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(packageName,0);
            long code=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
            return "vector-reference-v1:"+packageName+":"+code+":"+info.versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) { return null; }
    }
    private static boolean standardTextKey(String key) {
        return Notification.EXTRA_TITLE.equals(key) || Notification.EXTRA_TEXT.equals(key)
                || Notification.EXTRA_BIG_TEXT.equals(key) || Notification.EXTRA_SUB_TEXT.equals(key)
                || Notification.EXTRA_TEXT_LINES.equals(key) || Notification.EXTRA_INFO_TEXT.equals(key)
                || Notification.EXTRA_SUMMARY_TEXT.equals(key) || Notification.EXTRA_TITLE_BIG.equals(key);
    }
    private static void extra(Bundle extras,String key,TrendDiagnostics.Source source,boolean value,
                              List<String> values,List<String> trends,TrendDiagnostics diagnostic) {
        text(extras.getCharSequence(key),source,false,value,values,trends,diagnostic);
    }
    private static void text(CharSequence content,TrendDiagnostics.Source source,boolean description,
                             boolean value,List<String> values,List<String> trends,TrendDiagnostics diagnostic) {
        if (content==null || content.length()==0) return;
        diagnostic.text(source,content.toString(),description);
        if (content.length()>512 || trends.size()>=96) { diagnostic.limitReached(); return; }
        if (content instanceof Spanned) {
            for (ImageSpan span:((Spanned)content).getSpans(0,content.length(),ImageSpan.class)) {
                diagnostic.image(source,kind(span.getDrawable()),true);
            }
        }
        trends.add(content.toString());
        if (value) values.add(content.toString());
    }
    private static TrendDiagnostics.ImageKind kind(Drawable image) {
        if (image instanceof BitmapDrawable) return TrendDiagnostics.ImageKind.BITMAP;
        if (image instanceof VectorDrawable) return TrendDiagnostics.ImageKind.VECTOR;
        if (image instanceof LevelListDrawable) return TrendDiagnostics.ImageKind.LEVEL_LIST;
        if (image instanceof StateListDrawable) return TrendDiagnostics.ImageKind.STATE_LIST;
        return TrendDiagnostics.ImageKind.OTHER;
    }
    private static void collect(View view,TrendDiagnostics.Source source,boolean value,
                                List<String> values,List<String> trends,TrendDiagnostics diagnostic,int[] budget,int depth,
                                VectorReference vectors,String profile,String notificationKey) {
        if (view.getVisibility()!=View.VISIBLE) return;
        if (depth>20 || --budget[0]<0) { diagnostic.limitReached(); return; }
        text(view.getContentDescription(),source,true,value,values,trends,diagnostic);
        if (view instanceof TextView) {
            TextView label=(TextView)view;
            text(label.getText(),source,false,value,values,trends,diagnostic);
            // A trend graphic may be attached to a TextView rather than a separate ImageView.
            Set<Drawable> attached=Collections.newSetFromMap(new IdentityHashMap<Drawable,Boolean>());
            Collections.addAll(attached,label.getCompoundDrawables());
            Collections.addAll(attached,label.getCompoundDrawablesRelative());
            for (Drawable image:attached) if (image!=null) {
                diagnostic.image(source,kind(image),false);
                imageDetails(view,image,source,diagnostic,0);
                vectors.add(view,image,profile,notificationKey);
            }
        }
        if (view instanceof ImageView && ((ImageView)view).getDrawable()!=null) {
            diagnostic.image(source,kind(((ImageView)view).getDrawable()),false);
            float[] transform=new float[9];
            ((ImageView)view).getImageMatrix().getValues(transform);
            float matrixRotation=(float)Math.toDegrees(Math.atan2(transform[Matrix.MSKEW_Y],transform[Matrix.MSCALE_X]));
            imageDetails(view,((ImageView)view).getDrawable(),source,diagnostic,matrixRotation);
            vectors.add(view,((ImageView)view).getDrawable(),profile,notificationKey);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) {
                if (budget[0]<=0) { diagnostic.limitReached(); break; }
                collect(group.getChildAt(i),source,value,values,trends,diagnostic,budget,depth+1,vectors,profile,notificationKey);
            }
        }
    }
    private static void imageDetails(View view,Drawable image,TrendDiagnostics.Source source,
                                     TrendDiagnostics diagnostic,float matrixRotation) {
        String id="NO_ID";
        if (view.getId()!=View.NO_ID) {
            try { id=view.getResources().getResourceEntryName(view.getId()); }
            catch (android.content.res.Resources.NotFoundException e) { /* Static identifier unavailable. */ }
        }
        diagnostic.imageDetails(source,id,kind(image),image.getIntrinsicWidth(),image.getIntrinsicHeight(),view.getRotation(),matrixRotation);
    }
}
