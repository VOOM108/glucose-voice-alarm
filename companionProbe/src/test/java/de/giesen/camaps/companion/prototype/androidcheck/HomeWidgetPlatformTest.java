// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.WatchSnapshot;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w360dp-h800dp-mdpi",shadows={CamApsConnectionTest.ListenerPort.class})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeWidgetPlatformTest {
    Application c;int widget;final List<ActivityController<?>> activities=new ArrayList<>();
    @Before public void reset()throws Exception {
        c=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(c);AppText.configure(Locale.GERMAN);ProbeState.clear();
        for(String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(c.getFilesDir(),file).delete();
        for(String name:new String[]{HomeWidget.PREFS,AppAppearance.PREFS,"live_controls_v1",UnitPreferences.PREFS,"gva_watch_link_v1"})c.getSharedPreferences(name,0).edit().clear().commit();
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        c.deleteDatabase("probe-observations.db");ShadowSystemClock.advanceBy(Duration.ofHours(3));
        shadowOf(c.getSystemService(KeyguardManager.class)).setIsDeviceLocked(false);
        new OutputPreferenceStore(c.getFilesDir()).save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,0,SpeechTemplate.VALUE_DEFAULT));
        AppWidgetManager manager=AppWidgetManager.getInstance(c);shadowOf(manager).setAllowedToBindAppWidgets(true);
        AppWidgetProviderInfo info=new AppWidgetProviderInfo();info.provider=new ComponentName(c,HomeWidgetProvider.class);info.initialLayout=R.layout.home_widget;
        shadowOf(manager).addInstalledProvider(info);
        widget=43;assertTrue(manager.bindAppWidgetIdIfAllowed(widget,new ComponentName(c,HomeWidgetProvider.class)));
        assertTrue(HomeWidget.owns(c,widget));
    }
    @After public void close()throws Exception {
        for(ActivityController<?> a:activities)a.pause().stop().destroy();
        await();ProbeState.clear();
    }
    private void await()throws Exception {CountDownLatch done=new CountDownLatch(1);HomeWidget.request(c,done::countDown);assertTrue(done.await(15,TimeUnit.SECONDS));}
    private void received(long age) throws Exception {
        long now=System.currentTimeMillis();ProbeState.Observation o=new ProbeState.Observation(126,NotificationParser.Trend.UP_RIGHT,SystemClock.elapsedRealtime()-age,now-age,"synthetic-widget");
        ReflectionHelpers.setField(o,"receivedWall",now-age);ProbeState.observed(o);
        try(ObservationStore store=new ObservationStore(c)){/* Database schema exists; no old value is made current. */}
    }
    private WidgetControlActivity action(PendingIntent p){Intent i=shadowOf(p).getSavedIntent();if(WidgetControlReceiver.class.getName().equals(i.getComponent().getClassName())){new WidgetControlReceiver().onReceive(c,i);return null;}ActivityController<WidgetControlActivity> a=Robolectric.buildActivity(WidgetControlActivity.class,i).create();activities.add(a);return a.get();}
    private OutputPreferenceStore.Snapshot output()throws Exception{return new OutputPreferenceStore(c.getFilesDir()).load();}
    @Test public void widgetReadsAcceptedDataWithoutEnablingWatchTransfer()throws Exception {
        received(0);assertFalse(WatchPublisher.enabled(c));HomeWidget.Model m=HomeWidget.model(c);
        assertNotNull(m.reading);assertEquals(126,m.reading.value,0);assertEquals(WatchSnapshot.Trend.UP_RIGHT,m.reading.trend);assertTrue(m.fresh);
        assertNull(WatchPublisher.snapshot(c));assertFalse(LivePreferences.alarms(c));
    }
    @Test public void aGapKeepsTheOriginalReceiptButCannotProduceFreshDelta()throws Exception {
        received(5*60000);long receipt=ProbeState.display(c).receivedWall;ProbeState.unavailable();
        HomeWidget.Model m=HomeWidget.model(c);assertNotNull(m.reading);assertFalse(m.fresh);assertEquals(receipt,m.reading.received);
        assertNull(m.reading.rate);assertTrue(HomeWidget.description(m).contains("Veraltet"));assertFalse(LivePreferences.alarms(c));
    }
    @Test public void expiryUntrustedClockConsentAndDisabledReceptionHideTheValue()throws Exception {
        received(121*60000);assertNull(HomeWidget.model(c).reading);
        received(0);ProbeState.Observation o=ProbeState.latest;ReflectionHelpers.setField(o,"receivedWall",o.receivedWall-120000);assertNull(HomeWidget.model(c).reading);
        received(0);c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,false).commit();assertNull(HomeWidget.model(c).reading);
        c.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        c.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();assertNull(HomeWidget.model(c).reading);assertFalse(HomeWidget.model(c).ready);
    }
    @Test public void storedHistoryCannotRestoreAReadingAfterProcessStateWasCleared()throws Exception {
        received(0);ProbeState.clear();assertNull(PhoneDisplaySnapshot.read(c));assertNull(HomeWidget.model(c).reading);
    }
    @Test public void invalidWidgetAndLockedDeviceCannotChangeAChannel()throws Exception {
        long revision=output().revision;
        action(HomeWidget.control(c,-77,1,true,revision,false));assertEquals(revision,output().revision);
        shadowOf(c.getSystemService(KeyguardManager.class)).setIsDeviceLocked(true);
        action(HomeWidget.control(c,widget,1,true,revision,false));assertEquals(revision,output().revision);
    }
    @Test public void channelClickAppliesAnExplicitStateAndAReplayDoesNothing()throws Exception {
        PendingIntent p=HomeWidget.control(c,widget,1,true,output().revision,false);
        action(p);assertTrue(output().preferences.alarmSpeechEnabled);long revision=output().revision;
        action(p);assertEquals(revision,output().revision);assertTrue(output().preferences.alarmSpeechEnabled);assertFalse(LivePreferences.alarms(c));
    }
    @Test public void lastAlarmRouteCannotBeTurnedOffByWidget()throws Exception {
        long revision=output().revision;action(HomeWidget.control(c,widget,0,false,revision,false));
        assertEquals(revision,output().revision);assertTrue(output().preferences.tonesEnabled);assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
    }
    @Test public void valueSpeechNeedsItsExistingIntervalSetup()throws Exception {
        long revision=output().revision;WidgetControlActivity a=action(HomeWidget.control(c,widget,2,true,revision,false));
        Intent next=shadowOf(a).getNextStartedActivity();assertEquals(OutputSettingsActivity.class.getName(),next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra("enable_values",false));assertEquals(revision,output().revision);
    }
    @Test public void masterOffPreservesReceptionAndChannels()throws Exception {
        LivePreferences.set(c,true,true);long revision=output().revision;
        action(HomeWidget.control(c,widget,-1,false,revision,true));assertFalse(LivePreferences.alarms(c));assertTrue(LivePreferences.monitor(c));assertEquals(revision,output().revision);
    }
    @Test public void masterOnWithMissingSystemPermissionNeverArmsOutput()throws Exception {
        shadowOf(c.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);
        WidgetControlActivity a=action(HomeWidget.control(c,widget,-1,true,output().revision,false));assertFalse(LivePreferences.alarms(c));assertFalse(a.isFinishing());
    }
    @Test public void masterOnUsesTheExistingForegroundActivationWhenReady()throws Exception {
        NotificationManager notifications=c.getSystemService(NotificationManager.class);shadowOf(notifications).setNotificationsEnabled(true);shadowOf(notifications).setNotificationListenerAccessGranted(new ComponentName(c,CamApsListener.class),true);
        shadowOf(c.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(c.getPackageName(),true);shadowOf(c.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        new LevelStore(c.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        WidgetControlActivity a=action(HomeWidget.control(c,widget,-1,true,output().revision,false));assertTrue(LivePreferences.alarms(c));assertNull(a);assertNull(shadowOf(c).getNextStartedActivity());
        Intent service=shadowOf(c).getNextStartedService();assertNotNull(service);assertEquals(LiveAlarmService.class.getName(),service.getComponent().getClassName());
    }
    @Test public void vibrationMayBeTheOnlyWidgetEnabledAlarmRoute()throws Exception {
        shadowOf((Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE)).setHasVibrator(true);
        OutputPreferenceStore.Snapshot p=output();new OutputPreferenceStore(c.getFilesDir()).save(p.revision,p.preferences.withChannels(true,true,false,true));
        action(HomeWidget.control(c,widget,0,false,output().revision,false));action(HomeWidget.control(c,widget,1,false,output().revision,false));
        assertFalse(output().preferences.tonesEnabled);assertFalse(output().preferences.alarmSpeechEnabled);assertTrue(output().preferences.vibrationEnabled);
        long revision=output().revision;action(HomeWidget.control(c,widget,3,false,revision,false));assertEquals(revision,output().revision);assertTrue(output().preferences.vibrationEnabled);
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
    }
    @Test @Config(sdk=34) public void responsiveViewsHaveBoundedTwoSizeImagesAndCanBeParceled()throws Exception {
        Bundle options=new Bundle();options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,new ArrayList<>(Arrays.asList(new android.util.SizeF(300,128),new android.util.SizeF(320,160))));
        RemoteViews rv=HomeWidget.views(c,widget,options,new HomeWidget.Model(demo(false),true,false,false,false,true,output()));
        Parcel parcel=Parcel.obtain();try{rv.writeToParcel(parcel,0);assertTrue(parcel.dataSize()>0);parcel.setDataPosition(0);RemoteViews recreated=RemoteViews.CREATOR.createFromParcel(parcel);assertNotNull(recreated.apply(c,new FrameLayout(c)));}finally{parcel.recycle();}
    }
    @Test public void refreshBroadcastCannotChangeOutputOrChannels()throws Exception {
        long revision=output().revision;HomeWidgetProvider provider=new HomeWidgetProvider();
        provider.onReceive(c,new Intent("de.giesen.gva.WIDGET_TOGGLE").putExtra("channel",0).putExtra("enabled",false));await();
        assertEquals(revision,output().revision);assertTrue(output().preferences.tonesEnabled);assertFalse(LivePreferences.alarms(c));
    }
    @Test public void toggleRowIsAvailableAtEverySupportedHeightAndCanStillBeHidden(){
        for(int height:new int[]{128,160,220})assertTrue(HomeWidget.controls(c,widget,220,height));
        c.getSharedPreferences(HomeWidget.PREFS,0).edit().putBoolean("controls_"+widget,false).commit();assertFalse(HomeWidget.controls(c,widget,300,160));assertTrue(HomeWidget.controls(c,widget+1,300,160));
    }
    @Test public void configureOnlyChangesWidgetPresentation()throws Exception {
        Intent i=new Intent(c,WidgetConfigureActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widget);
        ActivityController<WidgetConfigureActivity> a=Robolectric.buildActivity(WidgetConfigureActivity.class,i).create();activities.add(a);
        CheckBox check=findCheck(a.get().getWindow().getDecorView());assertNotNull(check);check.setChecked(false);assertTrue(click(a.get().getWindow().getDecorView(),"Speichern"));
        assertFalse(HomeWidget.controls(c,widget,300,340));assertFalse(LivePreferences.alarms(c));assertEquals(Activity.RESULT_OK,shadowOf(a.get()).getResultCode());
    }
    @Test public void backgroundOpensDashboardWithoutChangingOutput()throws Exception {
        LivePreferences.set(c,true,true);HomeWidget.Model m=HomeWidget.model(c);
        View view=HomeWidget.view(c,widget,320,340,m).apply(c,new FrameLayout(c));
        view.findViewById(R.id.widget_root).performClick();Intent next=shadowOf(c).getNextStartedActivity();
        assertEquals(ProbeActivity.class.getName(),next.getComponent().getClassName());assertTrue(LivePreferences.alarms(c));
    }
    @Test public void fiveTogglesFitOneVisibleRowAtTheMinimumSize()throws Exception {
        View root=HomeWidget.view(c,widget,220,128,HomeWidget.model(c)).apply(c,new FrameLayout(c));
        root.measure(View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(128,View.MeasureSpec.EXACTLY));root.layout(0,0,220,128);
        ViewGroup row=root.findViewById(R.id.widget_controls);assertEquals(View.VISIBLE,row.getVisibility());
        assertTrue(row.getBottom()<=root.getHeight()-root.getPaddingBottom());assertEquals(2,((ViewGroup)root).getChildCount());
        for(int id:new int[]{R.id.widget_tone,R.id.widget_alarm,R.id.widget_value,R.id.widget_vibration,R.id.widget_master}){View toggle=row.findViewById(id);assertEquals(0,toggle.getTop());assertEquals(44,toggle.getHeight());assertTrue(toggle.getRight()<=row.getWidth());assertNotNull(toggle.getContentDescription());}
        assertEquals(View.GONE,root.findViewById(R.id.widget_master_separator).getVisibility());
        assertEquals(36,root.findViewById(R.id.widget_tone_track).getWidth());assertEquals(18,root.findViewById(R.id.widget_tone_track).getHeight());
        root.findViewById(R.id.widget_alarm).performClick();assertNull(shadowOf(c).getNextStartedActivity());
        assertTrue(shadowOf(c).getBroadcastIntents().stream().anyMatch(i->i.getComponent()!=null && WidgetControlReceiver.class.getName().equals(i.getComponent().getClassName())));
    }
    @Test @Config(sdk=34) public void controlsUseImmutablePrivateBroadcastsAndActivityOnlyForSetup()throws Exception {
        PendingIntent p=HomeWidget.control(c,widget,1,true,output().revision,false);assertTrue(p.isImmutable());assertTrue(shadowOf(p).isBroadcastIntent());
        Intent i=shadowOf(p).getSavedIntent();assertEquals(WidgetControlReceiver.class.getName(),i.getComponent().getClassName());
        assertFalse(c.getPackageManager().getReceiverInfo(i.getComponent(),0).exported);assertNotNull(i.getData());
        PendingIntent setup=HomeWidget.control(c,widget,2,true,output().revision,false);assertTrue(setup.isImmutable());assertTrue(shadowOf(setup).isActivityIntent());
        assertEquals(WidgetControlActivity.class.getName(),shadowOf(setup).getSavedIntent().getComponent().getClassName());
    }
    @Test @Config(sdk={28,34}) public void directClicksSwitchAllFourChannelsWithoutOpeningAnyActivity()throws Exception {
        shadowOf((Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE)).setHasVibrator(true);
        OutputPreferenceStore store=new OutputPreferenceStore(c.getFilesDir());
        store.save(output().revision,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES,5,SpeechTemplate.VALUE_DEFAULT,100,100,5,0,TrendSpeech.defaults(),true,true,true,true));
        for(int channel=0;channel<4;channel++) {
            assertNull(action(HomeWidget.control(c,widget,channel,false,output().revision,false)));
            OutputPreferences p=output().preferences;boolean[] states={p.tonesEnabled,p.alarmSpeechEnabled,p.valueSpeechEnabled,p.vibrationEnabled};assertFalse(states[channel]);
            assertNull(action(HomeWidget.control(c,widget,channel,true,output().revision,false)));
            p=output().preferences;states=new boolean[]{p.tonesEnabled,p.alarmSpeechEnabled,p.valueSpeechEnabled,p.vibrationEnabled};assertTrue(states[channel]);
        }
        assertNull(shadowOf(c).getNextStartedActivity());assertFalse(LivePreferences.alarms(c));
    }
    @Test @Config(sdk={28,34}) public void raceAfterRenderingCannotBypassLastRouteProtectionOrOpenActivity()throws Exception {
        action(HomeWidget.control(c,widget,1,true,output().revision,false));
        Intent click=shadowOf(HomeWidget.control(c,widget,0,false,output().revision,false)).getSavedIntent();
        assertEquals(WidgetControlReceiver.class.getName(),click.getComponent().getClassName());
        OutputPreferenceStore store=new OutputPreferenceStore(c.getFilesDir());store.save(output().revision,output().preferences.withChannels(true,false,false));
        click.putExtra("revision",output().revision);long before=output().revision;
        new WidgetControlReceiver().onReceive(c,click);
        assertEquals(before,output().revision);assertTrue(output().preferences.tonesEnabled);assertNull(shadowOf(c).getNextStartedActivity());
    }
    @Test @Config(sdk={28,34}) public void directReceiverIgnoresWrongActionsConsentLossAndPendingProfile()throws Exception {
        Intent click=shadowOf(HomeWidget.control(c,widget,1,true,output().revision,false)).getSavedIntent();long revision=output().revision;
        new WidgetControlReceiver().onReceive(c,new Intent(click).setAction(HomeWidget.REFRESH));assertEquals(revision,output().revision);
        c.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();new WidgetControlReceiver().onReceive(c,click);assertEquals(revision,output().revision);
        ConsentTestSupport.accept(c);ConfigurationSnapshot desired=ProfileController.capture(c);SettingsTransaction.begin(c.getFilesDir(),desired);
        byte[] pending=java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),SettingsTransaction.FILE_NAME).toPath());
        new WidgetControlReceiver().onReceive(c,click);
        assertArrayEquals(pending,java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),SettingsTransaction.FILE_NAME).toPath()));
        assertFalse(LivePreferences.alarms(c));assertNull(shadowOf(c).getNextStartedActivity());
    }
    @Test @Config(sdk={28,34}) public void directReceiverRejectsAnOldClickAfterMasterStateChanges()throws Exception {
        Intent click=shadowOf(HomeWidget.control(c,widget,1,true,output().revision,false)).getSavedIntent();long revision=output().revision;
        LivePreferences.set(c,true,true);new WidgetControlReceiver().onReceive(c,click);
        assertEquals(revision,output().revision);assertFalse(output().preferences.alarmSpeechEnabled);assertTrue(LivePreferences.alarms(c));
    }
    @Test @Config(sdk={28,34}) public void directStartFailureDoesNotLeaveOutputArmed()throws Exception {
        NotificationManager notifications=c.getSystemService(NotificationManager.class);shadowOf(notifications).setNotificationsEnabled(true);shadowOf(notifications).setNotificationListenerAccessGranted(new ComponentName(c,CamApsListener.class),true);
        shadowOf(c.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(c.getPackageName(),true);shadowOf(c.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
        new LevelStore(c.getFilesDir()).save(0,Collections.singletonList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level));
        Intent click=shadowOf(HomeWidget.control(c,widget,-1,true,output().revision,false)).getSavedIntent();
        Context blocked=new ContextWrapper(c){@Override public ComponentName startForegroundService(Intent i){throw new IllegalStateException("synthetic start denied");}};
        new WidgetControlReceiver().onReceive(blocked,click);
        assertFalse(LivePreferences.alarms(c));assertFalse(LivePreferences.monitor(c));assertNull(shadowOf(c).getNextStartedActivity());assertNull(shadowOf(c).getNextStartedService());
    }
    @Test public void remoteViewsRenderBothThemesAndModesWithoutTouchingOutput()throws Exception {
        WatchSnapshot sample=demo(false);OutputPreferenceStore.Snapshot output=output();
        for(AppAppearance.Mode theme:new AppAppearance.Mode[]{AppAppearance.Mode.DARK,AppAppearance.Mode.LIGHT}) {
            AppAppearance.set(c,theme);
            for(int height:new int[]{160,220})save("widget-balanced-"+theme.name().toLowerCase()+"-"+height+".png",HomeWidget.view(c,widget,320,height,new HomeWidget.Model(sample,true,false,false,false,true,output)),320,height);
        }
        AppAppearance.set(c,AppAppearance.Mode.DARK);
        save("widget-balanced-stale.png",HomeWidget.view(c,widget,320,160,new HomeWidget.Model(sample,false,false,false,false,true,output)),320,160);
        save("widget-balanced-empty.png",HomeWidget.view(c,widget,320,160,new HomeWidget.Model(null,false,false,false,false,true,output)),320,160);
        AppText.configure(Locale.ENGLISH);
        save("widget-balanced-english-mmol.png",HomeWidget.view(c,widget,320,160,new HomeWidget.Model(demo(true),true,false,false,false,true,output)),320,160);
        AppText.configure(Locale.GERMAN);
        for(int width:new int[]{220,260,400})save("widget-balanced-width-"+width+".png",HomeWidget.view(c,widget,width,160,new HomeWidget.Model(sample,true,false,false,false,true,output)),width,160);
        assertFalse(LivePreferences.alarms(c));
    }
    @Test public void aLongGapInTheCurveIsNeverConnected(){
        long now=1800000000000L;List<WatchSnapshot.Point> points=Arrays.asList(new WatchSnapshot.Point(now-50*60000,110),new WatchSnapshot.Point(now-10*60000,110));
        WatchSnapshot s=new WatchSnapshot(now,now,110,WatchSnapshot.Trend.FLAT,false,true,null,80.,180.,points);
        Bitmap b=new HomeWidgetRenderer().image(s,true,true,300,1f,60,now);
        int line=0;for(int y=0;y<b.getHeight();y++)for(int x=b.getWidth()/3;x<b.getWidth()*2/3;x++)if(b.getPixel(x,y)==0xffd4f6e7)line++;
        assertEquals(0,line);
    }
    @Test public void narrowerChartsUseShorterClearlyNamedWindows()throws Exception {
        assertEquals(30,HomeWidgetRenderer.historyMinutes(204));assertEquals("30 min",HomeWidgetRenderer.historyLabel(30));
        assertEquals(60,HomeWidgetRenderer.historyMinutes(304));assertEquals("1 h",HomeWidgetRenderer.historyLabel(60));
        assertEquals(120,HomeWidgetRenderer.historyMinutes(384));assertEquals("2 h",HomeWidgetRenderer.historyLabel(120));
        View normal=HomeWidget.view(c,widget,320,160,new HomeWidget.Model(demo(false),true,false,false,false,true,output())).apply(c,new FrameLayout(c));
        assertTrue(normal.findViewById(R.id.widget_data).getContentDescription().toString().contains("60 Minuten"));
        assertEquals(View.VISIBLE,normal.findViewById(R.id.widget_master_separator).getVisibility());
    }
    @Test public void olderPointsAreOmittedFromAShorterWindowRatherThanCompressed(){
        long now=1800000000000L;List<WatchSnapshot.Point> points=Arrays.asList(new WatchSnapshot.Point(now-90*60000,60),new WatchSnapshot.Point(now-10*60000,110));
        WatchSnapshot s=new WatchSnapshot(now,now,110,WatchSnapshot.Trend.FLAT,false,true,null,80.,180.,points);HomeWidgetRenderer r=new HomeWidgetRenderer();
        assertEquals(0,pixels(r.image(s,true,true,96,1f,30,now),0xffff5c5c));
        assertTrue(pixels(r.image(s,true,true,180,1f,120,now),0xffff5c5c)>0);
    }
    @Test public void valueDigitsAreLargeAndArrowStaysBesideThemBeforeTheChart()throws Exception {
        HomeWidget.Model m=new HomeWidget.Model(demo(false),true,false,false,false,true,output());
        View normal=layout(HomeWidget.view(c,widget,320,160,m),320,160);
        ImageView number=normal.findViewById(R.id.widget_reading);
        Bitmap digits=Bitmap.createBitmap(number.getWidth(),number.getHeight(),Bitmap.Config.ARGB_8888);number.draw(new Canvas(digits));
        Rect ink=ink(digits);int firstInk=ink.left;
        // Measure visible glyphs rather than the former TextView's em size.
        assertTrue("The visible reading must remain readable without dominating the widget",ink.height()>=30&&ink.height()<=46);
        assertTrue("The reading must begin at the leading edge instead of leaving a blank block",firstInk<18);
        assertTrue("The larger arrow uses spare room but stays inside the reading region",ink.width()>number.getWidth()*.5f&&ink.right<number.getWidth()-1);
        View wide=layout(HomeWidget.view(c,widget,560,180,m),560,180);
        ImageView larger=wide.findViewById(R.id.widget_reading);Bitmap expanded=Bitmap.createBitmap(larger.getWidth(),larger.getHeight(),Bitmap.Config.ARGB_8888);larger.draw(new Canvas(expanded));
        assertTrue("A wider host may enlarge the value only up to its height cap",ink(expanded).height()>=ink.height()&&ink(expanded).height()<=46);
        assertEquals(18,((TextView)normal.findViewById(R.id.widget_period)).getTextSize(),.1);
        save("widget-balanced-wide.png",HomeWidget.view(c,widget,560,180,m),560,180);
        c.getSharedPreferences(HomeWidget.PREFS,0).edit().putBoolean("controls_"+widget,false).commit();
        save("widget-balanced-no-controls.png",HomeWidget.view(c,widget,320,220,m),320,220,false);
    }
    @Test @Config(sdk=34,qualifiers="de-w411dp-h891dp-xxxhdpi") public void roundedNumeralsRetainTheirSizeOnDenseLauncherAndThroughParcel()throws Exception {
        HomeWidget.Model m=new HomeWidget.Model(demo(false),true,false,false,false,true,output());
        for(int width:new int[]{320,400}) {
            RemoteViews rv=HomeWidget.view(c,widget,width,160,m);Parcel parcel=Parcel.obtain();
            try {
                rv.writeToParcel(parcel,0);parcel.setDataPosition(0);
                RemoteViews copy=RemoteViews.CREATOR.createFromParcel(parcel);
                View host=layout(copy,width,160);float density=c.getResources().getDisplayMetrics().density;
                ImageView number=host.findViewById(R.id.widget_reading);TextView period=host.findViewById(R.id.widget_period);
                Bitmap visible=Bitmap.createBitmap(number.getWidth(),number.getHeight(),Bitmap.Config.ARGB_8888);number.draw(new Canvas(visible));
                float visibleHeight=ink(visible).height()/density;
                assertTrue("Dense displays retain a bounded readable size",visibleHeight>=30&&visibleHeight<=46);
                Bitmap before=bitmap(layout(rv,width,160),R.id.widget_reading),after=bitmap(host,R.id.widget_reading);
                assertTrue("The exact round font and arrow must survive RemoteViews parceling",before.sameAs(after));
                assertEquals(18,period.getTextSize()/density,.1);
                assertTrue(host.findViewById(R.id.widget_controls).getBottom()<=host.getHeight());
                save("widget-balanced-dense-"+width+".png",copy,width,160);
            } finally {parcel.recycle();}
        }
    }
    @Test @Config(sdk=34) public void aSingleExactLauncherSizeIsUsedInsteadOfUnrelatedFallbackDimensions()throws Exception {
        Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,220);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,128);
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,new ArrayList<>(Collections.singletonList(new android.util.SizeF(400,160))));
        View host=layout(HomeWidget.views(c,widget,options,new HomeWidget.Model(demo(false),true,false,false,false,true,output())),400,160);
        assertEquals("2 h",((TextView)host.findViewById(R.id.widget_period)).getText().toString());
    }
    @Test public void legacyPortraitKeepsNarrowWidthAndNormalizedChart()throws Exception {
        Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,560);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,128);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,180);
        View host=layout(HomeWidget.views(c,widget,options,new HomeWidget.Model(demo(false),true,false,false,false,true,output())),320,180);
        ImageView chart=host.findViewById(R.id.widget_chart);Bitmap b=((android.graphics.drawable.BitmapDrawable)chart.getDrawable()).getBitmap();
        assertEquals("Portrait chart must use its own normalized height",64,b.getHeight());
        assertEquals("Portrait uses the narrower width",132,b.getWidth());
    }
    @Test @Config(sdk=34,qualifiers="de-w411dp-h891dp-xxxhdpi") public void aWrongLauncherHeightCannotInflateTheCurveIntoABand()throws Exception {
        AppAppearance.set(c,AppAppearance.Mode.DARK);long now=System.currentTimeMillis();
        List<WatchSnapshot.Point> points=new ArrayList<>();for(int i=0;i<=120;i++)points.add(new WatchSnapshot.Point(now-(120-i)*60000L,110));
        WatchSnapshot sample=new WatchSnapshot(now,now,110,WatchSnapshot.Trend.FLAT,false,true,null,80.,180.,points);
        HomeWidget.Model m=new HomeWidget.Model(sample,true,false,false,false,true,output());
        for(int hint:new int[]{120,240}) {
            RemoteViews rv=HomeWidget.view(c,widget,400,hint,m);View root=layout(rv,400,160);ImageView chart=root.findViewById(R.id.widget_chart);
            Bitmap pixels=Bitmap.createBitmap(chart.getWidth(),chart.getHeight(),Bitmap.Config.ARGB_8888);chart.draw(new Canvas(pixels));
            int thickness=0;for(int y=0;y<pixels.getHeight();y++)if(pixels.getPixel(pixels.getWidth()/2,y)==0xffd4f6e7)thickness++;
            float density=c.getResources().getDisplayMetrics().density;
            float relativeStroke=thickness/(float)chart.getHeight();
            assertTrue("Use the middle weight between the previous two curves",relativeStroke>=.055f&&relativeStroke<=.095f);
            Bitmap source=((android.graphics.drawable.BitmapDrawable)chart.getDrawable()).getBitmap();
            assertEquals("Use enough source pixels for a dense screen",256,source.getHeight());
            save("widget-balanced-height-hint-"+hint+".png",rv,400,160);
        }
    }
    @Test @Config(sdk=34) public void restrictedLauncherKeepsExactlyThePreparedRoundFontAfterParcel()throws Exception {
        Context launcher=c.createPackageContext(c.getPackageName(),Context.CONTEXT_RESTRICTED);assertTrue(launcher.isRestricted());
        HomeWidget.Model m=new HomeWidget.Model(demo(false),true,false,false,false,true,output());RemoteViews rv=HomeWidget.view(c,widget,400,160,m);
        View app=layout(rv,400,160);Parcel parcel=Parcel.obtain();
        try{rv.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews copy=RemoteViews.CREATOR.createFromParcel(parcel);
            View hosted=copy.apply(launcher,new FrameLayout(launcher));
            for(int id:new int[]{R.id.widget_reading,R.id.widget_delta})assertTrue("Launcher restrictions must not replace the font",bitmap(app,id).sameAs(bitmap(hosted,id)));
            int memory=0;for(int id:new int[]{R.id.widget_reading,R.id.widget_delta,R.id.widget_chart})memory+=bitmap(hosted,id).getAllocationByteCount();
            assertTrue("Bound bitmap memory for both responsive variants",memory*2<4*1024*1024);
        }finally{parcel.recycle();}
    }
    @Test public void roundNumeralsKeepEveryArrowAndBothUnitFormatsInsideTransparentBounds(){
        for(String value:new String[]{"126","9,8","10.5","—"})for(WatchSnapshot.Trend trend:WatchSnapshot.Trend.values()){
            String arrow=HomeWidget.arrow(trend);Bitmap with=HomeWidgetRenderer.numerals(c,value,arrow,Color.WHITE,false);
            Bitmap without=HomeWidgetRenderer.numerals(c,value,"",Color.WHITE,false);Rect r=ink(with);
            assertTrue(r.width()>0&&r.height()>0);assertTrue(r.left>0&&r.top>0&&r.right<with.getWidth()&&r.bottom<with.getHeight());
            if(!arrow.isEmpty())assertTrue("Known arrow must add visible glyphs",ink(with).width()>ink(without).width());
            assertTrue(with.getWidth()<=960&&with.getHeight()<=384);
        }
        for(boolean small:new boolean[]{true,false}){
            Bitmap number=HomeWidgetRenderer.numerals(c,"0","",Color.WHITE,small),missing=HomeWidgetRenderer.numerals(c,"—","",Color.WHITE,small);
            assertEquals("A missing-value dash uses the same line height as a numeral",number.getHeight(),missing.getHeight());
            assertTrue("The placeholder remains a thin stroke",ink(missing).height()<ink(number).height()*.3f);
        }
    }
    @Test public void receiptCaptionOmitsDateButKeepsOriginalTimeAndStaleWarning()throws Exception {
        WatchSnapshot s=demo(false);HomeWidget.Model m=new HomeWidget.Model(s,false,false,false,false,true,output());
        String clock=new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(s.received));
        for(Locale language:new Locale[]{Locale.GERMAN,Locale.ENGLISH}) {
            AppText.configure(language);
            TextView stamp=layout(HomeWidget.view(c,widget,320,160,m),320,160).findViewById(R.id.widget_stamp);
            String expected=(language==Locale.GERMAN?"Empfangen: ":"Received: ")+clock+" · mg/dl";
            assertEquals(expected,stamp.getText().toString());assertEquals(AppAppearance.colors(c).error,stamp.getCurrentTextColor());
        }
        AppText.configure(Locale.GERMAN);
        assertTrue("Screen readers retain the full original timestamp",HomeWidget.description(m).contains(new java.text.SimpleDateFormat("dd.MM. HH:mm",Locale.getDefault()).format(new Date(s.received))));
    }
    @Test public void responsiveArrowsGrowWithoutRescalingDigitsOrClippingAnyDirection()throws Exception {
        for(String value:new String[]{"96","196","7,0"})for(WatchSnapshot.Trend trend:WatchSnapshot.Trend.values()) {
            if(trend==WatchSnapshot.Trend.UNKNOWN)continue;
            String arrow=HomeWidget.arrow(trend);
            Bitmap bare=HomeWidgetRenderer.numerals(c,value,"",Color.WHITE,false,220);
            Bitmap narrow=HomeWidgetRenderer.numerals(c,value,arrow,Color.WHITE,false,220);
            Bitmap wide=HomeWidgetRenderer.numerals(c,value,arrow,Color.WHITE,false,440);
            assertEquals(narrow.getWidth(),wide.getWidth());assertEquals(narrow.getHeight(),wide.getHeight());
            int start=ink(bare).right+1;
            for(int y=0;y<narrow.getHeight();y++)for(int x=0;x<Math.min(start,narrow.getWidth());x++)
                assertEquals("Numerals must stay pixel-identical at different arrow scales",narrow.getPixel(x,y),wide.getPixel(x,y));
            Rect smallArrow=inkAfter(narrow,start),largeArrow=inkAfter(wide,start);
            assertTrue(largeArrow.width()>0&&largeArrow.height()>0);
            assertTrue("Arrow stays inside its bitmap",largeArrow.top>0&&largeArrow.bottom<wide.getHeight()&&largeArrow.right<wide.getWidth()-1);
            assertTrue(largeArrow.width()>=smallArrow.width()-1);assertTrue(largeArrow.height()>=smallArrow.height()-1);
            if(trend==WatchSnapshot.Trend.FLAT)assertTrue("Extra width should visibly enlarge the horizontal arrow",largeArrow.width()>smallArrow.width()*1.05f);
        }
        WatchSnapshot base=demo(false);
        for(int width:new int[]{220,320,400,560})for(WatchSnapshot.Trend trend:new WatchSnapshot.Trend[]{WatchSnapshot.Trend.FLAT,WatchSnapshot.Trend.UP_RIGHT,WatchSnapshot.Trend.DOUBLE_DOWN}) {
            WatchSnapshot sample=new WatchSnapshot(base.generated,base.received,126,trend,false,true,base.rate,base.low,base.high,base.points);
            save("widget-arrow-"+width+"-"+trend.name().toLowerCase(Locale.ROOT)+".png",HomeWidget.view(c,widget,width,160,new HomeWidget.Model(sample,true,false,false,false,true,output())),width,160);
        }
    }
    private Rect inkAfter(Bitmap b,int from){Rect r=new Rect(b.getWidth(),b.getHeight(),0,0);for(int y=0;y<b.getHeight();y++)for(int x=from;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>100){r.left=Math.min(r.left,x);r.top=Math.min(r.top,y);r.right=Math.max(r.right,x+1);r.bottom=Math.max(r.bottom,y+1);}return r;}
    @Test @Config(sdk=34,qualifiers="de-w411dp-h891dp-xxxhdpi") public void shorterValuesDoNotGrowAndTallHostsRespectTheHeightCap()throws Exception {
        float density=c.getResources().getDisplayMetrics().density;WatchSnapshot base=demo(false);
        for(int width:new int[]{320,400,560})for(int height:new int[]{160,260})for(WatchSnapshot.Trend trend:new WatchSnapshot.Trend[]{WatchSnapshot.Trend.FLAT,WatchSnapshot.Trend.DOWN_RIGHT}){
            float previous=-1;
            for(int value:new int[]{96,196}){
                WatchSnapshot sample=new WatchSnapshot(base.generated,base.received,value,trend,false,true,base.rate,base.low,base.high,base.points);
                RemoteViews rv=HomeWidget.view(c,widget,width,height,new HomeWidget.Model(sample,true,false,false,false,true,output()));
                ImageView number=layout(rv,width,height).findViewById(R.id.widget_reading);
                Bitmap visible=Bitmap.createBitmap(number.getWidth(),number.getHeight(),Bitmap.Config.ARGB_8888);number.draw(new Canvas(visible));
                float glyphHeight=ink(visible).height()/density;
                assertTrue("The launcher cannot allocate an oversized reading image",number.getHeight()/density<=56);
                assertTrue("Both digit counts stay readable and below the visual cap",glyphHeight>=30&&glyphHeight<=46);
                if(previous>=0)assertEquals("Two and three digits must have comparable height",previous,glyphHeight,2);
                previous=glyphHeight;
                if(width==400&&height==160&&trend==WatchSnapshot.Trend.DOWN_RIGHT)save("widget-balanced-"+(value==96?"two-digits":"three-digits")+".png",rv,width,height);
            }
        }
    }
    private Bitmap bitmap(View root,int id){return ((android.graphics.drawable.BitmapDrawable)((ImageView)root.findViewById(id)).getDrawable()).getBitmap();}
    private Rect ink(Bitmap b){Rect r=new Rect(b.getWidth(),b.getHeight(),0,0);for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>100){r.left=Math.min(r.left,x);r.top=Math.min(r.top,y);r.right=Math.max(r.right,x+1);r.bottom=Math.max(r.bottom,y+1);}return r;}
    private int pixels(Bitmap b,int color){int count=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(b.getPixel(x,y)==color)count++;return count;}
    private WatchSnapshot demo(boolean mmol){long now=System.currentTimeMillis();List<WatchSnapshot.Point> points=new ArrayList<>();for(int i=0;i<=120;i++)points.add(new WatchSnapshot.Point(now-(120-i)*60000L,126+65*Math.sin(i/17.)));return new WatchSnapshot(now,now-60000,126,WatchSnapshot.Trend.UP_RIGHT,mmol,true,1.2,80.,180.,points);}
    private View layout(RemoteViews rv,int width,int height) {
        float density=c.getResources().getDisplayMetrics().density;int w=Math.round(width*density),h=Math.round(height*density);
        View v=rv.apply(c,new FrameLayout(c));v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);return v;
    }
    private void save(String name,RemoteViews rv,int width,int height)throws Exception {save(name,rv,width,height,true);}
    private void save(String name,RemoteViews rv,int width,int height,boolean controls)throws Exception {
        if(!controls)rv.setViewVisibility(R.id.widget_controls,View.GONE);
        View v=layout(rv,width,height);Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));
        assertEquals(controls?View.VISIBLE:View.GONE,v.findViewById(R.id.widget_controls).getVisibility());
        File folder=new File("build/ui-previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    private CheckBox findCheck(View v){if(v instanceof CheckBox)return (CheckBox)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){CheckBox r=findCheck(((ViewGroup)v).getChildAt(n));if(r!=null)return r;}return null;}
    private boolean click(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v.performClick();if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(click(((ViewGroup)v).getChildAt(n),label))return true;return false;}
}
