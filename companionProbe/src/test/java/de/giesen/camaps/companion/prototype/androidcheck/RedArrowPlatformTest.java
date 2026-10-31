// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import de.giesen.gva.watch.ArrowColor;
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
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi",shadows={RedArrowPlatformTest.SourceViews.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RedArrowPlatformTest {
    private Context app;
    private ServiceController<LiveAlarmService> service;
    private ServiceController<CamApsListener> listener;
    private TextToSpeech liveSpeech;
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication(); org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofHours(3)); ConsentTestSupport.accept(app); AppText.configure(Locale.GERMANY);
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
    private ImageView colouredArrow(NotificationParser.Trend direction,boolean redBackground) { return colouredArrow(direction,redBackground?ArrowColor.RED:ArrowColor.NORMAL); }
    private ImageView colouredArrow(NotificationParser.Trend direction,ArrowColor color) {
        ImageView image=new ImageView(app);image.setImageDrawable(new ColouredVector(direction,color));return image;
    }
    private static final class ColouredVector extends VectorDrawable {
        final NotificationParser.Trend direction;final ArrowColor color;
        ColouredVector(NotificationParser.Trend direction,ArrowColor color){this.direction=direction;this.color=color;}
        @Override public int getIntrinsicWidth(){return 24;} @Override public int getIntrinsicHeight(){return 24;}
        @Override public void draw(Canvas canvas){
            canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
            if(color.colored()){Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);background.setColor(color==ArrowColor.RED?Color.RED:Color.YELLOW);canvas.drawCircle(12,12,11,background);}
            canvas.rotate(direction==NotificationParser.Trend.FLAT?90:direction==NotificationParser.Trend.UP_RIGHT?45:
                    direction==NotificationParser.Trend.DOWN_RIGHT?135:direction==NotificationParser.Trend.DOWN||direction==NotificationParser.Trend.DOUBLE_DOWN?180:0,12,12);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);
            boolean twice=direction==NotificationParser.Trend.DOUBLE_UP||direction==NotificationParser.Trend.DOUBLE_DOWN;
            for(int i=0;i<(twice?2:1);i++){float x=twice?(i==0?8:16):12;canvas.drawLine(x,20,x,4,p);canvas.drawLine(x-4,8,x,4,p);canvas.drawLine(x+4,8,x,4,p);}canvas.restore();
        }
    }
    private void assign(ProbeActivity a,int index) {
        tag(a,"edit_vector").performClick();AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(d.findViewById(android.R.id.custom).findViewWithTag("arrow_choice_panel"));d.findViewById(android.R.id.custom).findViewWithTag("arrow_choice_"+index).performClick();
        d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
    }
    private void refresh(ProbeActivity a){org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"render");}
    private void arrowColour(CharSequence text,boolean red) { arrowColour(text,red?ArrowColor.RED:ArrowColor.NORMAL); }
    private void arrowColour(CharSequence text,ArrowColor color) {
        if(color.colored()){assertTrue(text instanceof android.text.Spanned);android.text.Spanned s=(android.text.Spanned)text;
            android.text.style.ForegroundColorSpan[] spans=s.getSpans(0,s.length(),android.text.style.ForegroundColorSpan.class);
            assertEquals(1,spans.length);assertEquals(color.phone,spans[0].getForegroundColor());assertEquals(s.length()-1,s.getSpanStart(spans[0]));
        }else if(text instanceof android.text.Spanned)for(android.text.style.ForegroundColorSpan span:((android.text.Spanned)text).getSpans(0,text.length(),android.text.style.ForegroundColorSpan.class))assertNotEquals(0xffe53935,span.getForegroundColor());
    }
    @Test public void everyColouredBackgroundCanBeAssignedWithoutChangingDirectionOrFreshness() throws Exception {
        start();ProbeActivity a=home();NotificationParser.Trend[] directions={NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.UP,NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN,NotificationParser.Trend.DOUBLE_DOWN};
        for(ArrowColor expectedColor:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW})for(int i=0;i<directions.length;i++){
            NotificationParser.Trend direction=directions[i];SourceViews.rendered=colouredArrow(direction,expectedColor);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
            assertEquals(NotificationParser.Trend.UNKNOWN,ProbeState.latest.trend);assertEquals(ArrowColor.NORMAL,ProbeState.latest.arrowColor);
            VectorReference.Candidate captured=ProbeState.vectorCandidate;assertNotNull(captured);assertNull(captured.shapeKey);
            long stamp=ProbeState.latest.receivedWall;assign(a,i+7*expectedColor.ordinal());
            assertEquals(stamp,ProbeState.latest.receivedWall);assertEquals(ArrowColor.NORMAL,ProbeState.latest.arrowColor);
            assertEquals(expectedColor.name()+"_"+direction.name(),app.getSharedPreferences(VectorReference.PREFS,0).getString(captured.key,""));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();refresh(a);
            assertEquals(direction,ProbeState.latest.trend);assertEquals(expectedColor,ProbeState.latest.arrowColor);
            assertEquals("123 "+direction.symbol,((TextView)tag(a,"dashboard_value")).getText().toString());arrowColour(((TextView)tag(a,"dashboard_value")).getText(),expectedColor);
            assertEquals("Pfeil: "+MetricNotification.trendTitle(direction),((TextView)tag(a,"camaps_trend")).getText().toString());
            Notification status=shadowOf(app.getSystemService(NotificationManager.class)).getNotification(LiveNotifications.STATUS_ID);arrowColour(status.extras.getCharSequence(Notification.EXTRA_TITLE),expectedColor);
            de.giesen.gva.watch.WatchSnapshot snapshot=PhoneDisplaySnapshot.read(app);assertEquals(expectedColor,snapshot.arrowColor);assertEquals(direction.name(),snapshot.trend.name());
            render((View)tag(a,"camaps_trend").getParent().getParent(),expectedColor.name().toLowerCase(java.util.Locale.ROOT)+"-arrow-"+direction.name().toLowerCase(java.util.Locale.ROOT),480);
        }
    }
    @Test public void confirmingNormalAgainRemovesOnlyTheExplicitRedVariant() throws Exception {
        start();SourceViews.rendered=colouredArrow(NotificationParser.Trend.FLAT,true);post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();ProbeActivity a=home();assign(a,10);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();refresh(a);assertEquals(ArrowColor.RED,ProbeState.latest.arrowColor);
        assign(a,3);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();refresh(a);
        assertEquals(NotificationParser.Trend.FLAT,ProbeState.latest.trend);assertEquals(ArrowColor.NORMAL,ProbeState.latest.arrowColor);arrowColour(((TextView)tag(a,"dashboard_value")).getText(),false);
    }
    @Test public void staleRedArrowUsesExistingGreyDisplayAndDoesNotRemainCurrentRed() throws Exception {
        start();SourceViews.rendered=colouredArrow(NotificationParser.Trend.FLAT,true);post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();ProbeActivity a=home();assign(a,10);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
        // Age only this synthetic receipt on both clocks, as in RetainedDisplayPlatformTest.
        ProbeState.Observation o=ProbeState.latest;
        org.robolectric.util.ReflectionHelpers.setField(o,"receivedElapsed",o.receivedElapsed-300000);
        org.robolectric.util.ReflectionHelpers.setField(o,"receivedWall",o.receivedWall-300000);
        org.robolectric.util.ReflectionHelpers.setField(o,"postedWall",o.postedWall-300000);refresh(a);
        assertEquals(ArrowColor.RED,ProbeState.display(app).arrowColor);assertFalse(ProbeState.displayFresh(app,ProbeState.display(app)));arrowColour(((TextView)tag(a,"dashboard_value")).getText(),false);
        assertFalse(HomeWidget.model(app).fresh);render((View)tag(a,"camaps_trend").getParent().getParent(),"red-arrow-stale",480);
    }
    @Test public void colourDoesNotLeakFromConfirmedRedGraphicToAnotherColourVariant() throws Exception {
        start();SourceViews.rendered=colouredArrow(NotificationParser.Trend.FLAT,true);post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();ProbeActivity a=home();assign(a,10);
        SourceViews.rendered=colouredArrow(NotificationParser.Trend.FLAT,false);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));post("123 mg/dl",true);shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ArrowColor.NORMAL,ProbeState.latest.arrowColor);assertEquals(NotificationParser.Trend.UNKNOWN,ProbeState.latest.trend);
    }
    @Test public void renderedWidgetColoursOnlyTheArrowAndKeepsTheValueInk() throws Exception {
        for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW}) {
            Bitmap bitmap=HomeWidgetRenderer.numerals(app,"123","→",Color.BLACK,false,320,color);int colored=0,black=0;
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++){int pixel=bitmap.getPixel(x,y);if(Color.alpha(pixel)>200){if(pixel==color.phone)colored++;if(Color.red(pixel)<20&&Color.green(pixel)<20)black++;}}
            assertTrue(colored>20);assertTrue(black>100);
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
