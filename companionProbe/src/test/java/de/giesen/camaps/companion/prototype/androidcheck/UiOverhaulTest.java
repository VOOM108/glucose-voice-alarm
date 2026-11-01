// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de-w411dp-h891dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class UiOverhaulTest {
    private Context app;private final List<ActivityController<?>> owners=new ArrayList<>();
    @Before public void before()throws Exception{
        app=RuntimeEnvironment.getApplication();configure("de",AppAppearance.Mode.LIGHT,1f);ConsentTestSupport.accept(app);ProbeState.clear();
        for(String p:new String[]{"live_controls_v1",CamApsListener.PREFS,AppAppearance.PREFS})app.getSharedPreferences(p,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new OutputPreferenceStore(app.getFilesDir()).save(0,OutputPreferences.defaults());
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        NotificationManager manager=app.getSystemService(NotificationManager.class);shadowOf(manager).setNotificationsEnabled(true);shadowOf(manager).setNotificationListenerAccessGranted(new ComponentName(app,CamApsListener.class),true);
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),false);
        shadowOf(app.getSystemService(ActivityManager.class)).setBackgroundRestricted(false);
    }
    @After public void after(){for(ActivityController<?> c:new ArrayList<>(owners))c.pause().stop().destroy();ProbeState.clear();AppText.configure(Locale.GERMAN);}
    private <T extends Activity> ActivityController<T> open(Class<T> type){ActivityController<T> c=Robolectric.buildActivity(type).create().start().resume().visible();owners.add(c);return c;}
    private List<View> views(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(views(((ViewGroup)v).getChildAt(i)));return out;}
    private View tag(Activity a,String name){return a.getWindow().getDecorView().findViewWithTag(name);}
    private View action(Activity a,String name){for(View v:views(a.getWindow().getDecorView()))if(v instanceof Button && (AppText.tr(name).equals(v.getContentDescription()) || AppText.tr(name).contentEquals(((Button)v).getText())))return v;throw new AssertionError(name);}
    private void speech(TrendNamesActivity a){((RadioButton)a.findViewById(9102)).performClick();assertTrue(((RadioButton)a.findViewById(9102)).isChecked());assertFalse(((RadioButton)a.findViewById(9101)).isChecked());tag(a,"trend_direction_FLAT").performClick();}
    private AlarmLevel level(String id,AlarmEngine.Kind kind,double limit){return new AlarmLevel(new AlarmEngine.Rule(id,kind,true,limit,kind==AlarmEngine.Kind.PERSISTENT_HIGH?10:0,true,0,1440,new AlarmEngine.Outputs(true,true,3,5)),id,AlarmLevel.DEFAULT_TONE);}
    @Test public void dashboardHasSevenConsistentNavigationDestinationsAndNoDiagnosticBlocks(){
        ProbeActivity a=open(ProbeActivity.class).get();String[] names={"Alarmstufen","Ausgabe","Trendbezeichnungen","Android-Freigaben","Profile","Verlauf & Diagnose","Einstellungen"};
        Class<?>[] types={AlarmLevelsActivity.class,OutputSettingsActivity.class,TrendNamesActivity.class,ReadinessActivity.class,ProfilesActivity.class,DiagnosticHistoryActivity.class,SettingsActivity.class};
        for(int i=0;i<names.length;i++){View v=action(a,names[i]);assertEquals(UiActions.Role.NAVIGATION,((UiActions.ActionButton)v).role);v.performClick();assertEquals(types[i].getName(),shadowOf(a).getNextStartedActivity().getComponent().getClassName());}
        for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView){String text=((TextView)v).getText().toString();assertFalse(text.contains("Alltagserprobung"));assertFalse(text.contains("Selbsttest"));}
    }
    @Test public void alarmCardsAreSortedAndTheirInitialDetailsAreReadOnlyAndCollapsed()throws Exception{
        new LevelStore(app.getFilesDir()).save(0,Arrays.asList(level("high210",AlarmEngine.Kind.HIGH,210),level("low70",AlarmEngine.Kind.LOW,70),level("high180",AlarmEngine.Kind.HIGH,180),level("low80",AlarmEngine.Kind.LOW,80)));
        AlarmLevelsActivity a=open(AlarmLevelsActivity.class).get();List<String> cards=new ArrayList<>();
        for(View v:views(a.getWindow().getDecorView()))if(v.getTag() instanceof String && v.getTag().toString().matches("alarm_(high|low)[0-9]+"))cards.add(v.getTag().toString());
        assertEquals(Arrays.asList("alarm_high180","alarm_high210","alarm_low80","alarm_low70"),cards);
        DisclosureRow details=(DisclosureRow)tag(a,"alarm_details_high210");assertFalse(details.expanded());details.performClick();assertTrue(details.expanded());
        View content=org.robolectric.util.ReflectionHelpers.getField(details,"content");
        for(View v:views(content)){assertFalse(v instanceof EditText);assertFalse(v instanceof Button);}
        assertEquals(1,views(a.getWindow().getDecorView()).stream().filter(v->v instanceof Button && "Alarm hinzufügen".equals(v.getContentDescription())).count());
        assertNotNull(tag(a,"alarm_gap"));assertFalse(LivePreferences.alarms(app));
    }
    @Test public void oneAddActionSelectsTheAlarmTypeAndReusesTheSingleGapEditor(){
        AlarmLevelsActivity a=open(AlarmLevelsActivity.class).get();action(a,"Alarm hinzufügen").performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null,2,2);assertEquals("SOURCE_LOW",shadowOf(a).getNextStartedActivity().getStringExtra("kind"));
        action(a,"Alarm hinzufügen").performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();int last=dialog.getListView().getCount()-1;dialog.getListView().performItemClick(null,last,last);
        assertEquals(GapSettingsActivity.class.getName(),shadowOf(a).getNextStartedActivity().getComponent().getClassName());assertFalse(LivePreferences.alarms(app));
    }
    @Test public void themeSettingsContainOnlyOpticalChoicesAndGeneralSettingsOwnTheUnitAndTerms(){
        AppearanceActivity appearance=open(AppearanceActivity.class).get();assertEquals(3,views(appearance.getWindow().getDecorView()).stream().filter(v->v instanceof RadioButton).count());
        assertEquals(1,views(appearance.getWindow().getDecorView()).stream().filter(v->v instanceof UiActions.ActionButton).count());
        SettingsActivity settings=open(SettingsActivity.class).get();action(settings,"Einheit").performClick();assertEquals(DeveloperOptionsActivity.class.getName(),shadowOf(settings).getNextStartedActivity().getComponent().getClassName());
        action(settings,"Sicherheit und Bedingungen").performClick();assertEquals(SafetyActivity.class.getName(),shadowOf(settings).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void watchAndPhoneDisplayAreOutputDestinationsAndWordsAreNotHiddenInOutput(){
        OutputSettingsActivity a=open(OutputSettingsActivity.class).get();assertNull(tag(a,"trend_speech_word_FLAT"));
        action(a,"Uhr").performClick();assertEquals(WatchSettingsActivity.class.getName(),shadowOf(a).getNextStartedActivity().getComponent().getClassName());
        action(a,"Sperrbildschirm und Benachrichtigungen").performClick();assertEquals(DisplaySettingsActivity.class.getName(),shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void permissionsUseCheckedStatesAndReturningNeverEnablesOutput(){
        ActivityController<ReadinessActivity> c=open(ReadinessActivity.class);ReadinessActivity a=c.get();
        assertTrue(((TextView)tag(a,"permission_state_access")).getText().toString().startsWith("✓"));
        assertTrue(((TextView)tag(a,"permission_state_battery")).getText().toString().startsWith("!"));
        assertTrue(((TextView)tag(a,"permission_state_live")).getText().toString().startsWith("—"));
        shadowOf(app.getSystemService(PowerManager.class)).setIgnoringBatteryOptimizations(app.getPackageName(),true);c.pause().resume();
        assertTrue(((TextView)tag(a,"permission_state_battery")).getText().toString().startsWith("✓"));assertFalse(LivePreferences.alarms(app));
        for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView)assertFalse(((TextView)v).getText().toString().contains("Eingangsnummer"));
    }
    @Test public void directionSelectorAlwaysStartsWithDoubleUpAndPreservesStoredWordIdentity()throws Exception{
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot base=store.load();
        store.save(base.revision,base.preferences.withTrendSpeech(new TrendSpeech(Arrays.asList("a","b","c","d","e","f","g"))));
        TrendNamesActivity a=open(TrendNamesActivity.class).get();List<String> buttons=new ArrayList<>();for(View v:views(a.getWindow().getDecorView()))if(v.getTag() instanceof String && v.getTag().toString().startsWith("trend_direction_"))buttons.add(v.getTag().toString());
        List<String> expected=new ArrayList<>();for(NotificationParser.Trend t:TrendOrder.ALL)expected.add("trend_direction_"+t.name());assertEquals(expected,buttons);
        ArrowCoverage.observe(app,"order-check",NotificationParser.Trend.DOUBLE_UP);String[] coverage=ArrowCoverage.summary(app).split("\n");
        assertEquals(8,coverage.length);for(int i=0;i<7;i++)assertTrue(coverage[i+1].startsWith(TrendOrder.ALL.get(i).symbol));
        assertTrue(coverage[1].endsWith(" · erkannt"));assertTrue(coverage[3].endsWith(" · noch nicht wiedererkannt"));
        ((RadioButton)a.findViewById(9102)).performClick();assertTrue(tag(a,"trend_speech_card_DOUBLE_UP").isShown());assertEquals("f",((EditText)tag(a,"trend_speech_word_DOUBLE_UP")).getText().toString());
    }
    @Test public void newDisplaySaveDoesNotRewriteSpeechOrArmOutput()throws Exception{
        File output=new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME);byte[] bytes=Files.readAllBytes(output.toPath());LivePreferences.set(app,true,true);
        TrendNamesActivity a=open(TrendNamesActivity.class).get();((EditText)tag(a,"trend_display_DOUBLE_UP")).setText("Doppelt hoch");tag(a,"trend_names_save").performClick();
        assertEquals("Doppelt hoch",DisplayTrendPreferences.title(app,NotificationParser.Trend.DOUBLE_UP));assertArrayEquals(bytes,Files.readAllBytes(output.toPath()));assertTrue(LivePreferences.alarms(app));
    }
    @Test public void speechValueAndColorDraftSurviveRotationAndStaleSaveCannotOverwriteNewSettings()throws Exception{
        ActivityController<TrendNamesActivity> first=open(TrendNamesActivity.class);TrendNamesActivity a=first.get();speech(a);
        ((EditText)tag(a,"trend_speech_word_FLAT")).setText("stabil bei {wert}");((CheckBox)tag(a,"trend_speech_color_RED_FLAT_enabled")).setChecked(true);
        ((EditText)tag(a,"trend_speech_color_RED_FLAT")).setText("unten bei {wert}");Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();owners.remove(first);
        ActivityController<TrendNamesActivity> restored=Robolectric.buildActivity(TrendNamesActivity.class).create(state).start().resume().visible();owners.add(restored);a=restored.get();
        assertTrue(tag(a,"trend_speech_card_FLAT").isShown());assertEquals("unten bei {wert}",((EditText)tag(a,"trend_speech_color_RED_FLAT")).getText().toString());
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot current=store.load();store.save(current.revision,current.preferences.withSpeechLanguage("en"));
        tag(a,"trend_names_save").performClick();assertEquals("en",store.load().preferences.speechLanguage);assertEquals("stabil",store.load().preferences.trendSpeech.word(NotificationParser.Trend.FLAT));
        assertTrue(((TextView)tag(a,"trend_names_status")).getText().toString().contains("inzwischen geändert"));
    }
    @Test public void previewUsesTheCompleteValueTemplateAndTheChosenColor(){
        TrendNamesActivity a=open(TrendNamesActivity.class).get();speech(a);((EditText)tag(a,"trend_speech_word_FLAT")).setText("gleich bei {wert}");
        String preview=((TextView)tag(a,"trend_full_preview")).getText().toString();assertTrue(preview,preview.contains("Trend: gleich bei 123"));assertFalse(preview.contains("Wert beträgt"));
        assertEquals(UiActions.Role.PLAY,((UiActions.ActionButton)tag(a,"trend_preview_RED")).role);
    }
    @Test public void missingArrowAndUnassignedGraphicHaveDifferentVisualHintsButNoSpokenTrend(){
        for(boolean assignment:new boolean[]{false,true}){
            ProbeState.Observation observation=new ProbeState.Observation(123,NotificationParser.Trend.UNKNOWN,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic",ReceiptRate.unavailable(),ArrowColor.NORMAL,assignment);
            ProbeState.observed(observation);ActivityController<ProbeActivity> c=open(ProbeActivity.class);ProbeActivity a=c.get();
            assertEquals(assignment?"Trend: Pfeil zuordnen":"Trend: offen",((TextView)tag(a,"camaps_trend")).getText().toString());
            assertEquals(assignment?"Pfeil zuordnen":"Trend offen",MetricNotification.displayTitle(app,NotificationParser.Trend.UNKNOWN,observation.receivedWall,true));
            Notification n=new LiveNotifications(app).status(123,NotificationParser.Trend.UNKNOWN,observation.receivedWall,true,"synthetic");
            assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().endsWith(assignment?"Pfeil zuordnen":"Trend offen"));
            String spoken=SpeechPolicy.observationText(123,NotificationParser.Trend.UNKNOWN,0);assertFalse(spoken.contains("offen"));assertFalse(spoken.contains("zuordnen"));
            c.pause().stop().destroy();owners.remove(c);
        }
    }
    @Test public void assignmentHintBelongsToTheAcceptedReadingAndCorrectionDoesNotRenewItsAge(){
        ProbeState.Observation observation=new ProbeState.Observation(123,NotificationParser.Trend.UNKNOWN,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic",ReceiptRate.unavailable(),ArrowColor.NORMAL,true);ProbeState.observed(observation);
        assertEquals("Trend offen",MetricNotification.displayTitle(app,NotificationParser.Trend.UNKNOWN,observation.receivedWall-1,true));
        assertTrue(MetricNotification.displayTitle(app,NotificationParser.Trend.UNKNOWN,observation.receivedWall,false).contains("alt"));
        ProbeState.forgetTrend();assertFalse(ProbeState.latest.assignmentNeeded);assertEquals(observation.receivedWall,ProbeState.latest.receivedWall);assertEquals(observation.receivedElapsed,ProbeState.latest.receivedElapsed);
        assertEquals("Trend offen",MetricNotification.displayTitle(app,NotificationParser.Trend.UNKNOWN,observation.receivedWall,true));
    }
    @Test public void outputTestIsSyntheticAndNeverWritesRuntimeDeadlines()throws Exception{
        File runtime=new File(new File(app.getFilesDir(),"live-receipt-runtime-v1"),AlarmStateStore.FILE_NAME);assertFalse(runtime.exists());
        byte[] output=Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath());
        OutputTestActivity a=open(OutputTestActivity.class).get();assertNotNull(tag(a,"output_test_Ton"));
        assertEquals(UiActions.Role.PLAY,((UiActions.ActionButton)tag(a,"output_test_Ton")).role);
        assertFalse(runtime.exists());assertFalse(LivePreferences.alarms(app));assertArrayEquals(output,Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath()));
    }
    @Test public void deletionLivesInTheEditorAndRequiresConfirmation()throws Exception{
        AlarmLevel level=level("delete_me",AlarmEngine.Kind.HIGH,210);LevelStore store=new LevelStore(app.getFilesDir());store.save(0,Collections.singletonList(level));
        ActivityController<AlarmLevelActivity> c=Robolectric.buildActivity(AlarmLevelActivity.class,new Intent(app,AlarmLevelActivity.class).putExtra("id",level.rule.id)).create().start().resume().visible();owners.add(c);
        action(c.get(),"Stufe löschen").performClick();assertEquals(1,store.load().levels.size());ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
        assertTrue(store.load().levels.isEmpty());assertFalse(LivePreferences.alarms(app));
    }
    private void configure(String language,AppAppearance.Mode theme,float scale){
        android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.setLocale(new Locale(language));config.fontScale=scale;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.scaledDensity=metrics.density*scale;app.getResources().updateConfiguration(config,metrics);AppAppearance.set(app,theme);AppText.configure(new Locale(language));
    }
    private DiagnosticSeries series(){long now=System.currentTimeMillis();List<DiagnosticSeries.Sample> points=new ArrayList<>();for(int i=0;i<=360;i++){if(i>=155 && i<=168)continue;double value=140+100*Math.sin((i-360)/45.0+Math.asin(-.17));points.add(new DiagnosticSeries.Sample(now-360*60000L+i*60000L,value));}return DiagnosticSeries.build(points,now);}
    private void image(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,411,view.getMeasuredHeight());
        for(View child:views(view)){child.jumpDrawablesToCurrentState();if(child instanceof UiActions.ActionButton && child.isShown()){UiActions.Role role=((UiActions.ActionButton)child).role;if(role==UiActions.Role.PLAY || role==UiActions.Role.STOP){View parent=(View)child.getParent();assertTrue("Preview target clipped: "+child.getContentDescription(),child.getLeft()>=0 && child.getRight()<=parent.getWidth());}}}
        Bitmap bitmap=Bitmap.createBitmap(411,view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));File dir=new File("build/ui-previews/ui-overhaul");assertTrue(dir.isDirectory() || dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    @Test @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void chartUsesTheWidgetColorsAndDoesNotConnectAcrossTheGap(){
        HistoryChartView chart=new HistoryChartView(app);chart.setSeries(series());chart.setAlarmLevels(Arrays.asList(level("l",AlarmEngine.Kind.LOW,80),level("h",AlarmEngine.Kind.HIGH,180)));chart.layout(0,0,411,170);
        Bitmap b=Bitmap.createBitmap(411,170,Bitmap.Config.ARGB_8888);chart.draw(new Canvas(b));int[] colors={0xff156747,0xff936000,0xffc02737};for(int color:colors){int count=0;for(int y=0;y<170;y++)for(int x=0;x<411;x++)if(b.getPixel(x,y)==color)count++;assertTrue(Integer.toHexString(color)+" count="+count,count>60);}
        DiagnosticSeries data=series();assertEquals(1,data.gaps.size());float x=42+(411-24-42)*(162/360f);for(int y=32;y<140;y++)for(int color:colors)assertNotEquals(color,b.getPixel((int)x,y));b.recycle();
    }
    @Test @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderTheNewPagesInBothLanguagesThemesAndLargeFonts()throws Exception{
        new LevelStore(app.getFilesDir()).save(0,Arrays.asList(level("Tag",AlarmEngine.Kind.HIGH,210),level("Nacht",AlarmEngine.Kind.HIGH,180),level("Niedrig",AlarmEngine.Kind.LOW,80)));
        for(String language:new String[]{"de","en"})for(AppAppearance.Mode theme:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK})for(float scale:new float[]{1f,2f}){
            configure(language,theme,scale);String suffix=language+"-"+theme.name().toLowerCase(Locale.ROOT)+"-"+scale;
            for(Class<? extends Activity> type:Arrays.asList(ProbeActivity.class,AlarmLevelsActivity.class,TrendNamesActivity.class,ReadinessActivity.class,OutputSettingsActivity.class,SettingsActivity.class,DisplaySettingsActivity.class,OutputTestActivity.class,ProbeDiagnosticsActivity.class,WatchSettingsActivity.class)){
                if(type==ProbeActivity.class)ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.FLAT,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic");
                ActivityController<? extends Activity> c=open(type);Activity a=c.get();
                if(a instanceof ProbeActivity){for(View v:views(a.getWindow().getDecorView()))if(v instanceof HistoryChartView){((HistoryChartView)v).setSeries(series());((HistoryChartView)v).setAlarmLevels(new LevelStore(app.getFilesDir()).load().levels);}}
                if(a instanceof TrendNamesActivity){speech((TrendNamesActivity)a);((EditText)tag(a,"trend_speech_word_FLAT")).setText("en".equals(language)?"steady at {value}":"stabil bei {wert}");((CheckBox)tag(a,"trend_speech_color_RED_FLAT_enabled")).setChecked(true);}
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
                if(a instanceof ProbeActivity){DiagnosticSeries data=series();for(View v:views(a.getWindow().getDecorView()))if(v instanceof HistoryChartView){((HistoryChartView)v).setSeries(data);((HistoryChartView)v).setAlarmLevels(new LevelStore(app.getFilesDir()).load().levels);}((TextView)tag(a,"history_summary")).setText(data.rawPoints+AppText.tr(" Werte · ")+data.gaps.size()+AppText.tr(" Lücken"));}
                ScrollView scroll=null;for(View v:views(a.getWindow().getDecorView()))if(v instanceof ScrollView){scroll=(ScrollView)v;break;}assertNotNull(scroll);image(scroll.getChildAt(0),type.getSimpleName()+"-"+suffix);
                c.pause().stop().destroy();owners.remove(c);
            }
        }
    }
}
