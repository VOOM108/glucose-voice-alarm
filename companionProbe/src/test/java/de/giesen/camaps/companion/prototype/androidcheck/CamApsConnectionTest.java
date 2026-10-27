// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.service.notification.*;
import android.widget.RemoteViews;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",shadows=CamApsConnectionTest.ListenerPort.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CamApsConnectionTest {
    @Implements(NotificationListenerService.class)
    public static class ListenerPort extends org.robolectric.shadows.ShadowNotificationListenerService {
        static int rebinds,unbinds;static boolean fail;
        @Implementation protected static void requestRebind(ComponentName name){rebinds++;if(fail)throw new IllegalStateException("synthetic");}
        @Implementation public void requestUnbind(){unbinds++;}
    }
    private Application app;private final List<ServiceController<CamApsListener>> listeners=new ArrayList<>();
    private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);ListenerPort.rebinds=ListenerPort.unbinds=0;ListenerPort.fail=false;
        ReflectionHelpers.setStaticField(CamApsConnection.class,"current",null);ReflectionHelpers.setStaticField(CamApsConnection.class,"lastAttempt",-1L);ReflectionHelpers.setStaticField(CamApsConnection.class,"failed",false);
        ((Handler)ReflectionHelpers.getStaticField(CamApsConnection.class,"handler")).removeCallbacksAndMessages(null);
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();app.getSharedPreferences("live_controls_v1",0).edit().clear().commit();
        shadowOf(app.getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        ProbeState.latest=null;ProbeState.vectorCandidate=null;app.deleteDatabase("probe-observations.db");UnitPreferences.reset(app);
    }
    @After public void after(){for(ActivityController<?> c:activities)c.pause().stop().destroy();for(ServiceController<CamApsListener> c:listeners)c.destroy();ProbeState.latest=null;}
    private CamApsListener listener(boolean connect){ServiceController<CamApsListener> c=Robolectric.buildService(CamApsListener.class).create();listeners.add(c);if(connect)c.get().onListenerConnected();return c.get();}
    private void send(CamApsListener listener,Notification notification,String pkg){listener.onNotificationPosted(new StatusBarNotification(pkg,pkg,1,"synthetic",0,0,0,notification,android.os.Process.myUserHandle(),System.currentTimeMillis()));}
    private Notification source(String text,boolean ongoing){return new Notification.Builder(app,"synthetic").setSmallIcon(R.drawable.ic_xcam_status).setOngoing(ongoing).setContentText(text).build();}
    @Test public void initialRequestRestoresBindingWithoutEnablingAnyOutput(){assertTrue(CamApsConnection.ensure(app));assertEquals(1,ListenerPort.rebinds);assertFalse(LivePreferences.alarms(app));assertFalse(LivePreferences.monitor(app));assertFalse(CamApsConnection.connected());}
    @Test public void consentGateNeverRequestsOrGrantsAccess(){app.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();assertFalse(CamApsConnection.ensure(app));assertFalse(CamApsConnection.reconnect(app));assertEquals(0,ListenerPort.rebinds);assertFalse(LivePreferences.alarms(app));}
    @Test public void disabledReceptionRemainsDisabled(){app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,false).commit();assertFalse(CamApsConnection.ensure(app));assertEquals(0,ListenerPort.rebinds);assertFalse(app.getSharedPreferences(CamApsListener.PREFS,0).getBoolean(CamApsListener.ENABLED,true));}
    @Test public void missingAndroidAccessIsNeverGrantedByReconnect(){shadowOf(app.getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),false);assertFalse(CamApsConnection.ensure(app));assertFalse(CamApsConnection.reconnect(app));assertEquals(0,ListenerPort.rebinds);assertFalse(SystemReadiness.read(app).access);}
    @Test public void bindingDuringWarningDoesNotUnbindOrProcessUntilBothSteps(){
        app.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();CamApsListener listener=listener(true);assertEquals(0,ListenerPort.unbinds);
        send(listener,source("100 mg/dl →",true),"com.camdiab.fx_alert.mgdl");assertNull(ProbeState.latest);assertNull(UnitPreferences.source(app));
        ConsentTestSupport.accept(app);assertTrue(CamApsConnection.ensure(app));assertEquals(0,ListenerPort.rebinds);
        send(listener,source("100 mg/dl →",true),"com.camdiab.fx_alert.mgdl");assertNotNull(ProbeState.latest);assertFalse(LivePreferences.alarms(app));
    }
    @Test public void disconnectRequestsRebindWithBoundedDelay(){CamApsListener listener=listener(true);listener.onListenerDisconnected();assertFalse(CamApsConnection.connected());assertEquals(0,ListenerPort.rebinds);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,ListenerPort.rebinds);for(int i=0;i<20;i++)CamApsConnection.ensure(app);assertEquals(1,ListenerPort.rebinds);}
    @Test public void failedRequestRetriesAtMostOncePerThirtySeconds(){ListenerPort.fail=true;assertFalse(CamApsConnection.ensure(app));for(int i=0;i<20;i++)assertFalse(CamApsConnection.ensure(app));assertEquals(1,ListenerPort.rebinds);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));ListenerPort.fail=false;assertTrue(CamApsConnection.ensure(app));assertEquals(2,ListenerPort.rebinds);}
    @Test public void explicitReconnectPreservesAlarmSwitchAndUserAccess(){CamApsListener listener=listener(true);LivePreferences.set(app,true,true);assertTrue(CamApsConnection.reconnect(app));assertEquals(1,ListenerPort.unbinds);listener.onListenerDisconnected();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,ListenerPort.rebinds);assertTrue(SystemReadiness.read(app).access);assertTrue(LivePreferences.alarms(app));}
    @Test public void reopeningUiRequestsMissingBindingWithoutTurningOutputOn(){ActivityController<AppearanceActivity> c=Robolectric.buildActivity(AppearanceActivity.class).create().start().resume();activities.add(c);assertEquals(1,ListenerPort.rebinds);assertFalse(LivePreferences.alarms(app));}
    @Test public void connectedListenerIsNotResetJustBecauseNoReadingArrives(){listener(true);for(int i=0;i<10;i++){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));CamApsConnection.ensure(app);}assertEquals(0,ListenerPort.rebinds);assertTrue(CamApsConnection.connected());}
    @Test public void insulinMessageCanCoexistWithAValueInTheExpandedView(){
        CamApsListener listener=listener(true);Notification n=source("Insulin abgegeben",true);RemoteViews expanded=new RemoteViews(app.getPackageName(),android.R.layout.simple_list_item_1);expanded.setTextViewText(android.R.id.text1,"100 mg/dl →");n.bigContentView=expanded;
        send(listener,n,"com.camdiab.fx_alert.mgdl");assertNotNull(ProbeState.diagnostics,ProbeState.latest);assertEquals(100,ProbeState.latest.value,0);assertEquals(NotificationParser.Trend.FLAT,ProbeState.latest.trend);
    }
    @Test public void insulinMessageWithoutAGlucoseValueNeverCreatesOne(){CamApsListener listener=listener(true);send(listener,source("Insulin abgegeben: 4 E",true),"com.camdiab.fx_alert.mgdl");assertNull(ProbeState.latest);assertNull(UnitPreferences.source(app));}
    @Test public void nonOngoingAndForeignNotificationsStayExcluded(){CamApsListener listener=listener(true);send(listener,source("100 mg/dl →",false),"com.camdiab.fx_alert.mgdl");send(listener,source("100 mg/dl →",true),"other.synthetic.app");assertNull(ProbeState.latest);assertNull(UnitPreferences.source(app));}
    @Test public void lateDisconnectFromOldInstanceDoesNotClearTheNewConnection(){CamApsListener old=listener(true);CamApsListener fresh=listener(true);send(fresh,source("100 mg/dl →",true),"com.camdiab.fx_alert.mgdl");ProbeState.Observation accepted=ProbeState.latest;assertNotNull(accepted);old.onListenerDisconnected();assertSame(accepted,ProbeState.latest);assertTrue(CamApsConnection.connected());assertTrue(CamApsConnection.ensure(app));assertEquals(0,ListenerPort.rebinds);fresh.onListenerDisconnected();assertFalse(CamApsConnection.connected());}
}
