// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Synthetic graphics and ordinary value speech only; never device data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi",instrumentedPackages={"de.giesen.camaps.companion.prototype.RuntimeClock"},shadows={ArrowCorrectionPlatformTest.SourceViews.class,RecoveryGapPlatformTest.TestClock.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ArrowCorrectionPlatformTest {
    private Context app;
    private ServiceController<LiveAlarmService> service;
    private ServiceController<CamApsListener> listener;
    private TextToSpeech liveSpeech;
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication(); ConsentTestSupport.accept(app); AppText.configure(Locale.GERMANY);
        for(String name:new String[]{AppAppearance.PREFS,VectorReference.PREFS,VectorReference.SHAPE_PREFS,VectorReference.HISTORY_PREFS,"live_controls_v1",CamApsListener.PREFS,SoundDefaults.PREFS,"source_unit_v1"})app.getSharedPreferences(name,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME).delete();
        ProbeState.clear(); ProbeState.vectorCandidate=null; SourceViews.rendered=null;
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager manager=app.getSystemService(NotificationManager.class);
        shadowOf(manager).setNotificationsEnabled(true); shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);
        shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        new OutputPreferenceStore(app.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,1,"{wert} {trend}").withChannels(false,true,true));
        android.content.pm.PackageInfo source=new android.content.pm.PackageInfo();source.packageName="com.camdiab.fx_alert.mgdl";source.versionName="synthetic";source.versionCode=1;
        shadowOf(app.getPackageManager()).installPackage(source);
        ShadowTextToSpeech.addVoice(new Voice("synthetic-de",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
    }
    @After public void after() {
        for(ActivityController<?> c:activities)c.pause().stop().destroy();
        if(listener!=null)listener.destroy(); if(service!=null)service.destroy(); ProbeState.clear(); SourceViews.rendered=null;
    }
    private ProbeActivity home() {
        ActivityController<ProbeActivity> c=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible();activities.add(c);return c.get();
    }
    private View tag(Activity a,String tag){return a.getWindow().getDecorView().findViewWithTag(tag);}
    private void start() {
        LivePreferences.set(app,true,true);service=Robolectric.buildService(LiveAlarmService.class).create();service.startCommand(0,1);shadowOf(Looper.getMainLooper()).idle();
        liveSpeech=ShadowTextToSpeech.getLastTextToSpeechInstance();assertNotNull(liveSpeech);shadowOf(liveSpeech).getOnInitListener().onInit(TextToSpeech.SUCCESS);shadowOf(Looper.getMainLooper()).idle();
        listener=Robolectric.buildService(CamApsListener.class).create();
    }
    private void post(String text,boolean graphic) {
        Notification n=new Notification.Builder(app,"synthetic").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(true).setContentText(text).build();
        if(graphic)n.contentView=new RemoteViews(app.getPackageName(),android.R.layout.activity_list_item);
        listener.get().onNotificationPosted(new android.service.notification.StatusBarNotification("com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.mgdl",1,null,1000,0,0,n,android.os.UserHandle.getUserHandleForUid(1000),RuntimeClock.read(app).wallMs));
    }
    private ImageView arrow(boolean down) throws Exception {
        ImageView image=new ImageView(app);image.setImageDrawable(new SyntheticVector(down));return image;
    }
    private static final class SyntheticVector extends VectorDrawable {
        private final boolean down;
        SyntheticVector(boolean down){this.down=down;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
        @Override public void draw(Canvas canvas){
            canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);
            Path p=new Path();
            if(down){p.moveTo(12,3);p.lineTo(12,17);p.moveTo(6,11);p.lineTo(12,17);p.lineTo(18,11);p.moveTo(6,17);p.lineTo(12,23);p.lineTo(18,17);}
            else{p.moveTo(12,21);p.lineTo(12,7);p.moveTo(6,13);p.lineTo(12,7);p.lineTo(18,13);p.moveTo(6,7);p.lineTo(12,1);p.lineTo(18,7);}
            canvas.drawPath(p,paint);canvas.restore();
        }
    }
    @Test public void syntheticUpAndDownVectorsHaveDistinctExactAndShapeIdentitiesAndPreviewMatchesKey() throws Exception {
        ImageView down=arrow(true),up=arrow(false);VectorReference a=new VectorReference(),b=new VectorReference();
        a.add(down,down.getDrawable(),"synthetic","synthetic");b.add(up,up.getDrawable(),"synthetic","synthetic");
        VectorReference.Candidate ca=a.candidate(),cb=b.candidate();assertNotNull(ca);assertNotNull(cb);assertNotNull(ca.shapeKey);assertNotEquals(ca.key,cb.key);assertNotEquals(ca.shapeKey,cb.shapeKey);
        int[] pixels=new int[4096];ca.preview.getPixels(pixels,0,64,0,0,64,64);
        assertEquals(ca.key,TrendReference.fingerprint("synthetic:"+down.getDrawable().getIntrinsicWidth()+":"+down.getDrawable().getIntrinsicHeight(),pixels));
    }
    @Test public void wronglyLabeledDoubleDownCanBeCorrectedFromDashboardWithoutRefreshingValueAge() throws Exception {
        start();SourceViews.rendered=arrow(true);post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
        VectorReference.Candidate captured=ProbeState.vectorCandidate;assertNotNull(captured);
        app.getSharedPreferences(VectorReference.PREFS,0).edit().putString(captured.key,NotificationParser.Trend.DOUBLE_UP.name()).commit();
        app.getSharedPreferences(VectorReference.SHAPE_PREFS,0).edit().putString(captured.shapeKey,NotificationParser.Trend.DOUBLE_UP.name()).commit();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NotificationParser.Trend.DOUBLE_UP,ProbeState.latest.trend);
        ProbeState.Observation previous=ProbeState.latest;ProbeActivity activity=home();assertTrue(tag(activity,"edit_vector").isShown());tag(activity,"edit_vector").performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
        assertTrue(dialog.findViewById(android.R.id.custom).findViewWithTag("arrow_choice_0").isSelected());
        render((View)tag(activity,"camaps_trend").getParent().getParent(),"arrow-correction-dashboard",480);
        render(dialog.findViewById(android.R.id.custom).findViewWithTag("arrow_choice_panel"),"arrow-correction-dialog",340);
        dialog.findViewById(android.R.id.custom).findViewWithTag("arrow_choice_6").performClick();dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NotificationParser.Trend.DOUBLE_DOWN,TrendReference.lookup(app.getSharedPreferences(VectorReference.PREFS,0).getAll(),captured.key));
        assertEquals(ShapeReference.CONFLICT,app.getSharedPreferences(VectorReference.SHAPE_PREFS,0).getString(captured.shapeKey,""));
        assertEquals(123,ProbeState.latest.value,0);assertEquals(NotificationParser.Trend.UNKNOWN,ProbeState.latest.trend);
        assertEquals(previous.receivedWall,ProbeState.latest.receivedWall);assertEquals(previous.receivedElapsed,ProbeState.latest.receivedElapsed);assertTrue(LivePreferences.alarms(app));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NotificationParser.Trend.DOUBLE_DOWN,ProbeState.latest.trend);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("123 ⇊",((TextView)tag(activity,"dashboard_value")).getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        String spoken=shadowOf(liveSpeech).getLastSpokenText();
        assertEquals("123 sehr schnell fallend",spoken);
    }
    @Test public void correctionRejectsAlreadyQueuedInterpretationsAndPreservesLiveConsent() throws Exception {
        start();LiveAlarmService.receive("synthetic",RuntimeClock.read(app).wallMs,123,NotificationParser.Trend.DOUBLE_UP);
        LiveAlarmService.trendReferencesChanged();shadowOf(Looper.getMainLooper()).idle();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(65));
        assertNull(shadowOf(liveSpeech).getLastSpokenText());assertTrue(LivePreferences.alarms(app));assertTrue(LiveAlarmService.running());
        Notification status=shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.STATUS_ID);
        assertFalse(status.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("⇈"));
    }
    @Test public void correctionStopsRunningOrdinarySpeechUntilAnotherRealReceiptArrives() throws Exception {
        start();post("123 mg/dl ⇈",false);shadowOf(Looper.getMainLooper()).idle();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertEquals("123 sehr schnell steigend",shadowOf(liveSpeech).getLastSpokenText());
        ProbeState.forgetTrend();LiveAlarmService.trendReferencesChanged();shadowOf(Looper.getMainLooper()).idle();
        assertTrue(shadowOf(liveSpeech).isStopped());shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(65));
        assertEquals("123 sehr schnell steigend",shadowOf(liveSpeech).getLastSpokenText());
        post("123 mg/dl ⇊",false);shadowOf(Looper.getMainLooper()).idle();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertEquals("123 sehr schnell fallend",shadowOf(liveSpeech).getLastSpokenText());
    }
    @Test public void unavailableAndExpiredPreviewExplainWhyCorrectionMustWait() throws Exception {
        ProbeActivity a=home();tag(a,"edit_vector").performClick();AlertDialog missing=ShadowAlertDialog.getLatestAlertDialog();assertTrue(missing.isShowing());
        assertTrue(shadowOf(missing).getMessage().toString().contains("nächsten CamAPS-Update"));missing.dismiss();
        ImageView down=arrow(true);VectorReference v=new VectorReference();v.add(down,down.getDrawable(),"synthetic","synthetic");ProbeState.vectorCandidate=v.candidate();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));tag(a,"edit_vector").performClick();
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());assertNull(app.getSharedPreferences(VectorReference.PREFS,0).getAll().get(ProbeState.vectorCandidate.key));
    }
    @Test public void everyTextArrowFeedsTheSameDirectionToDashboardAndNormalValueSpeech() throws Exception {
        for(NotificationParser.Trend trend:TrendDisplayWords.TRENDS) {
            start();post("123 mg/dl "+trend.symbol,false);shadowOf(Looper.getMainLooper()).idle();ProbeActivity a=home();
            assertEquals(trend,ProbeState.latest.trend);assertEquals("123 "+trend.symbol,((TextView)tag(a,"dashboard_value")).getText().toString());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
            assertEquals("123 "+TrendSpeech.defaults().word(trend),shadowOf(liveSpeech).getLastSpokenText());
            listener.destroy();listener=null;service.destroy();service=null;
        }
    }
    @Implements(RemoteViews.class) public static class SourceViews {
        static View rendered;
        @Implementation protected View apply(Context context,ViewGroup parent){return rendered;}
    }
    private void render(View view,String name,int height) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,411,height);
        Bitmap bitmap=Bitmap.createBitmap(411,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(AppAppearance.colors(app).surface);view.draw(canvas);
        File folder=new File("build/ui-previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
}
