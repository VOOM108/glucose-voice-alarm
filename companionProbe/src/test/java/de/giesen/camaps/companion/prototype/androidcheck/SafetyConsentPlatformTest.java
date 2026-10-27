// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
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
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de",shadows=SafetyConsentPlatformTest.RebindTracker.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SafetyConsentPlatformTest {
    @Implements(android.service.notification.NotificationListenerService.class)
    public static class RebindTracker extends org.robolectric.shadows.ShadowNotificationListenerService {
        static ComponentName requested;
        @Implementation protected static void requestRebind(ComponentName component){requested=component;}
    }
    private Application app;private final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void before(){
        // Robolectric can reuse a sandbox. A first-start test needs an unbound, unthrottled
        // listener and no delayed reconnect from another synthetic service lifecycle.
        org.robolectric.util.ReflectionHelpers.setStaticField(CamApsConnection.class,"current",null);
        org.robolectric.util.ReflectionHelpers.setStaticField(CamApsConnection.class,"lastAttempt",-1L);
        org.robolectric.util.ReflectionHelpers.setStaticField(CamApsConnection.class,"failed",false);
        ((Handler)org.robolectric.util.ReflectionHelpers.getStaticField(CamApsConnection.class,"handler")).removeCallbacksAndMessages(null);
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("live_controls_v1",0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().clear().commit();
        ProbeState.latest=null;ProbeState.vectorCandidate=null;UnitPreferences.reset(app);AppText.configure(Locale.GERMAN);RebindTracker.requested=null;
    }
    @After public void after(){for(ActivityController<?> c:activities)c.pause().stop().destroy();ProbeState.latest=null;}
    private <T extends Activity> T open(Class<T> type){ActivityController<T> c=Robolectric.buildActivity(type).create().start().resume().visible();activities.add(c);return c.get();}
    private View find(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),tag);if(found!=null)return found;}return null;}
    private View tag(Activity a,String name){View found=find(a.getWindow().getDecorView(),name);assertNotNull(name,found);return found;}
    private void warning(){assertTrue(SafetyConsent.acknowledge(app,SafetyConsent.Stage.WARNING,true));}
    private void accept(){warning();assertTrue(SafetyConsent.acknowledge(app,SafetyConsent.Stage.TERMS,true));}
    @Test public void freshInstallRequiresBothAndCannotArm(){
        assertEquals(SafetyConsent.Stage.WARNING,SafetyConsent.stage(app));LivePreferences.set(app,true,true);
        assertFalse(LivePreferences.monitor(app));assertFalse(LivePreferences.alarms(app));assertFalse(SafetyConsent.ready(app));
    }
    @Test public void neitherUncheckedNorOutOfOrderAcknowledgementWorks(){
        assertFalse(SafetyConsent.acknowledge(app,SafetyConsent.Stage.WARNING,false));assertFalse(SafetyConsent.acknowledge(app,SafetyConsent.Stage.TERMS,true));
        assertFalse(SafetyConsent.acknowledge(app,SafetyConsent.Stage.COMPLETE,true));assertEquals(SafetyConsent.Stage.WARNING,SafetyConsent.stage(app));
    }
    @Test public void warningButtonRequiresActualCheckboxEvenOnDirectClick(){
        SafetyActivity a=open(SafetyActivity.class);CheckBox check=(CheckBox)tag(a,"safety_check");Button next=(Button)tag(a,"safety_next");
        assertFalse(check.isChecked());assertFalse(next.isEnabled());next.performClick();assertEquals(SafetyConsent.Stage.WARNING,SafetyConsent.stage(app));
        check.setChecked(true);assertTrue(next.isEnabled());next.performClick();assertEquals(SafetyConsent.Stage.TERMS,SafetyConsent.stage(app));assertFalse(((CheckBox)tag(a,"safety_check")).isChecked());
    }
    @Test public void termsButtonAlsoStartsUncheckedAndFinalAcceptanceLeavesOutputOff(){
        warning();app.getSharedPreferences("live_controls_v1",0).edit().putBoolean("monitor",true).putBoolean("alarms",true).putInt("consent_version",BuildConfig.VERSION_CODE).commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        shadowOf(app.getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        SafetyActivity a=open(SafetyActivity.class);Button next=(Button)tag(a,"safety_next");next.performClick();assertFalse(SafetyConsent.ready(app));
        ((CheckBox)tag(a,"safety_check")).setChecked(true);next.performClick();assertTrue(SafetyConsent.ready(app));assertFalse(LivePreferences.alarms(app));
        Intent target=shadowOf(a).getNextStartedActivity();assertEquals(ProbeActivity.class.getName(),target.getComponent().getClassName());assertTrue(a.isFinishing());
        assertEquals(new ComponentName(app,CamApsListener.class),RebindTracker.requested);assertNull(shadowOf(app).getNextStartedService());
    }
    @Test public void partialAcceptancePersistsButDoesNotGrantFullAccess(){warning();assertFalse(SafetyConsent.ready(app));assertEquals(SafetyConsent.Stage.TERMS,SafetyConsent.stage(app.createConfigurationContext(app.getResources().getConfiguration())));}
    @Test public void rotationNeverRestoresAnUnconfirmedCheck(){
        ActivityController<SafetyActivity> c=Robolectric.buildActivity(SafetyActivity.class).create().start().resume().visible();activities.add(c);
        ((CheckBox)tag(c.get(),"safety_check")).setChecked(true);c.recreate();assertFalse(((CheckBox)tag(c.get(),"safety_check")).isChecked());assertFalse(SafetyConsent.ready(app));
    }
    @Test public void backAtEitherStageDoesNotGrantAcceptance(){
        SafetyActivity first=open(SafetyActivity.class);first.onBackPressed();assertTrue(first.isFinishing());assertEquals(SafetyConsent.Stage.WARNING,SafetyConsent.stage(app));
        warning();SafetyActivity second=open(SafetyActivity.class);second.onBackPressed();assertTrue(second.isFinishing());assertEquals(SafetyConsent.Stage.TERMS,SafetyConsent.stage(app));
    }
    @Test public void acceptedTextRevisionIsIndependentOfApkOutputConsent(){
        accept();app.getSharedPreferences("live_controls_v1",0).edit().putInt("consent_version",BuildConfig.VERSION_CODE-1).putBoolean("alarms",true).commit();
        assertTrue(SafetyConsent.ready(app));assertFalse(LivePreferences.alarms(app));assertEquals(SafetyConsent.TEXT_REVISION,app.getSharedPreferences(SafetyConsent.PREFS,0).getInt(SafetyConsent.TERMS,0));
    }
    @Test public void obsoleteFutureMissingAndCorruptFlagsFailClosed(){
        for(int revision:new int[]{0,-1,SafetyConsent.TEXT_REVISION+1}){app.getSharedPreferences(SafetyConsent.PREFS,0).edit().putInt(SafetyConsent.WARNING,revision).putInt(SafetyConsent.TERMS,revision).commit();assertFalse(SafetyConsent.ready(app));}
        accept();app.getSharedPreferences(SafetyConsent.PREFS,0).edit().remove(SafetyConsent.TERMS).commit();assertEquals(SafetyConsent.Stage.TERMS,SafetyConsent.stage(app));
        app.getSharedPreferences(SafetyConsent.PREFS,0).edit().putString(SafetyConsent.TERMS,"yes").commit();assertFalse(SafetyConsent.ready(app));
    }
    @Test public void newWarningAcceptanceClearsAnyStaleTermsFlag(){
        app.getSharedPreferences(SafetyConsent.PREFS,0).edit().putInt(SafetyConsent.TERMS,SafetyConsent.TEXT_REVISION).commit();warning();assertEquals(SafetyConsent.Stage.TERMS,SafetyConsent.stage(app));
    }
    @Test public void everyOwnUiEntryRoutesToSafetyAndCanBeDestroyedWithoutInitializedWidgets(){
        for(Class<? extends Activity> type:Arrays.asList(ProbeActivity.class,AppearanceActivity.class,ReadinessActivity.class,OutputSettingsActivity.class,AlarmLevelsActivity.class,AlarmLevelActivity.class,ProfilesActivity.class,GapSettingsActivity.class,DeveloperOptionsActivity.class,DiagnosticHistoryActivity.class,ProbeDiagnosticsActivity.class,SyntheticAlarmActivity.class,ToneSetsActivity.class)){
            Activity a=open(type);assertTrue(type.getSimpleName(),a.isFinishing());assertEquals(SafetyActivity.class.getName(),shadowOf(a).getNextStartedActivity().getComponent().getClassName());
            Bundle saved=new Bundle();activities.get(activities.size()-1).saveInstanceState(saved);
        }
    }
    @Test public void activationReturnsSafetyLinkWithoutStartingAService(){ProbeActivity a=open(ProbeActivity.class);assertEquals(SafetyActivity.class,LiveActivation.start(a).target);assertNull(shadowOf(app).getNextStartedService());}
    @Test public void bootAndPackageReplacementCannotStartBackgroundWork(){
        app.getSharedPreferences("live_controls_v1",0).edit().putBoolean("monitor",true).putBoolean("alarms",true).putInt("consent_version",BuildConfig.VERSION_CODE).commit();
        for(String action:new String[]{Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED}){new LiveBootReceiver().onReceive(app,new Intent(action));assertNull(shadowOf(app).getNextStartedService());assertFalse(LivePreferences.alarms(app));}
    }
    @Test public void directLiveServiceStartIsNotStickyAndNeverForeground(){ServiceController<LiveAlarmService> c=Robolectric.buildService(LiveAlarmService.class).create();try{assertEquals(Service.START_NOT_STICKY,c.get().onStartCommand(new Intent(),0,1));assertFalse(LiveAlarmService.running());assertNull(shadowOf(c.get()).getLastForegroundNotification());}finally{c.destroy();}}
    @Test public void grantedNotificationAccessCannotCollectBeforeAcceptance(){
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        ServiceController<CamApsListener> c=Robolectric.buildService(CamApsListener.class).create();try{
            Notification n=new Notification.Builder(app,"synthetic").setContentTitle("100 mg/dl →").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build();
            StatusBarNotification sbn=new StatusBarNotification("com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.mgdl",1,"synthetic",0,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis());
            c.get().onNotificationPosted(sbn);c.get().onNotificationRemoved(sbn);assertNull(ProbeState.latest);assertNull(UnitPreferences.source(app));
        }finally{c.destroy();}
    }
    @Test public void manualAudioAndVibrationAreUnavailableBeforeBothSteps(){
        PreviewAudio audio=new PreviewAudio(app,message->{});try{assertFalse(audio.speak("synthetic"));assertFalse(audio.tone("android.resource://de.giesen.camaps.companion.prototype/raw/bell_velvet_high"));assertFalse(audio.vibrate("SHORT"));assertFalse(new PreviewVibration(app).available());}finally{audio.close();}
    }
    @Test public void profilesCannotGrantAcknowledgementAndDoNotContainIt() throws Exception {
        accept();ConfigurationSnapshot profile=ProfileController.capture(app);String wire=new String(profile.encode(),java.nio.charset.StandardCharsets.ISO_8859_1);assertFalse(wire.contains(SafetyConsent.PREFS));
        app.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();try{ProfileController.apply(app,profile);fail();}catch(IOException expected){}assertFalse(SafetyConsent.ready(app));
        accept();ProfileController.apply(app,profile);assertTrue(SafetyConsent.ready(app));assertFalse(LivePreferences.alarms(app));
    }
    @Test public void rereadingAcceptedNoticesDoesNotDisarmOutputOrAskAgain(){
        accept();LivePreferences.set(app,true,true);SafetyActivity a=open(SafetyActivity.class);assertTrue(LivePreferences.alarms(app));assertNull(find(a.getWindow().getDecorView(),"safety_check"));assertTrue(SafetyConsent.ready(app));
    }
    @Test public void bothLanguagesAndOfflineLicenseContainTheRequiredWarnings() throws Exception {
        String de=SafetyActivity.text("warning-de.txt"),en=SafetyActivity.text("warning-en.txt");assertTrue(de.contains("Nicht für medizinische Verwendung"));assertTrue(de.contains("Forschungswerkzeug"));assertTrue(en.contains("Not for medical use"));assertTrue(en.contains("research tool only"));
        for(String lang:new String[]{"de","en"}){String terms=SafetyActivity.text("terms-"+lang+".txt");assertTrue(terms.contains("GPL"));assertTrue(terms.contains("15"));assertTrue(terms.contains("17"));}
        assertTrue(SafetyActivity.text("gpl-3.0.txt").contains("GNU GENERAL PUBLIC LICENSE"));assertTrue(SafetyActivity.text("gpl-3.0.txt").contains("29 June 2007"));
    }
}
