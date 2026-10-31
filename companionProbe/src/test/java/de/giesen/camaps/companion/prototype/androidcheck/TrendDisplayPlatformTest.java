// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
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
public class TrendDisplayPlatformTest {
    private Context app;
    private final List<ActivityController<?>> controllers=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
        app.getSharedPreferences(AppAppearance.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("live_controls_v1",0).edit().clear().commit();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,SettingsTransaction.FILE_NAME,ProfileStore.FILE_NAME})Files.deleteIfExists(new File(app.getFilesDir(),name).toPath());
        ProbeState.clear();
    }
    @After public void after() {
        for(ActivityController<?> controller:controllers)controller.pause().stop().destroy();
        ProbeState.clear();AppText.configure(Locale.GERMANY);
    }
    private TrendDisplayWords words(String flat) {
        List<String> words=new ArrayList<>(Collections.nCopies(7,""));words.set(2,flat);return new TrendDisplayWords(words);
    }
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state) {
        ActivityController<T> controller=Robolectric.buildActivity(type).create(state).start().resume().visible();controllers.add(controller);return controller;
    }
    private List<View> views(View view) {
        List<View> result=new ArrayList<>();result.add(view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result.addAll(views(((ViewGroup)view).getChildAt(i)));return result;
    }
    private View tagged(Activity activity,String tag) {return activity.getWindow().getDecorView().findViewWithTag(tag);}
    @Test public void editingDisplayKeepsSpeechOutputBytesAndLiveConsentIntact() throws Exception {
        OutputPreferences speech=OutputPreferences.defaults().withTrendSpeech(new TrendSpeech(Arrays.asList("a","b","Sprachwort","d","e","f","g")));
        new OutputPreferenceStore(app.getFilesDir()).save(0,speech);LivePreferences.set(app,true,true);
        File output=new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME);byte[] before=Files.readAllBytes(output.toPath());
        TrendDisplayActivity editor=open(TrendDisplayActivity.class,null).get();
        ((EditText)tagged(editor,"trend_display_FLAT")).setText("Anzeige");tagged(editor,"trend_display_save").performClick();
        assertTrue(editor.isFinishing());assertEquals("Anzeige",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));
        assertArrayEquals(before,Files.readAllBytes(output.toPath()));assertTrue(LivePreferences.alarms(app));assertTrue(LivePreferences.monitor(app));
        assertEquals("Sprachwort",new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech.word(NotificationParser.Trend.FLAT));
        assertEquals("Anzeige",MetricNotification.displayTitle(app,NotificationParser.Trend.FLAT,System.currentTimeMillis(),true));
        assertTrue(MetricNotification.displayTitle(app,NotificationParser.Trend.FLAT,System.currentTimeMillis()-600000,false).contains("min alt"));
        assertEquals("Trend offen",DisplayTrendPreferences.title(app,NotificationParser.Trend.UNKNOWN));
    }
    @Test public void dashboardUsesSavedDisplayWordsForEveryRecognizedTrend() throws Exception {
        List<String> custom=Arrays.asList("Anzeige1","Anzeige2","Anzeige3","Anzeige4","Anzeige5","Anzeige6","Anzeige7");
        DisplayTrendPreferences.save(app,0,new TrendDisplayWords(custom));
        for(int i=0;i<7;i++) {
            ProbeState.latest=new ProbeState.Observation(123,TrendDisplayWords.TRENDS.get(i),SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic");
            ActivityController<ProbeActivity> controller=open(ProbeActivity.class,null);
            boolean found=false;for(View view:views(controller.get().getWindow().getDecorView()))if(view instanceof TextView && ("Pfeil: "+custom.get(i)).contentEquals(((TextView)view).getText()))found=true;
            assertTrue(custom.get(i),found);controller.pause().stop().destroy();controllers.remove(controller);
        }
    }
    @Test public void editorDraftAndDiscardSurviveRotationAndStaleSaveCannotOverwriteProfile() throws Exception {
        ActivityController<TrendDisplayActivity> first=open(TrendDisplayActivity.class,null);
        ((EditText)tagged(first.get(),"trend_display_FLAT")).setText("Entwurf");Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();controllers.remove(first);
        TrendDisplayActivity second=open(TrendDisplayActivity.class,state).get();
        assertEquals("Entwurf",((EditText)tagged(second,"trend_display_FLAT")).getText().toString());second.onBackPressed();
        AlertDialog discard=ShadowAlertDialog.getLatestAlertDialog();assertTrue(discard.isShowing());assertFalse(second.isFinishing());discard.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        DisplayTrendPreferences.save(app,0,words("Neuer Stand"));tagged(second,"trend_display_save").performClick();
        assertFalse(second.isFinishing());assertEquals("Neuer Stand",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));
        assertTrue(((TextView)tagged(second,"trend_display_status")).getText().toString().contains("inzwischen geändert"));
    }
    @Test public void profilesRestoreDisplayAndSpeechIndependentlyAfterInterruptedApply() throws Exception {
        DisplayTrendPreferences.save(app,0,words("Profilwort"));ConfigurationSnapshot saved=ProfileController.capture(app);
        new ProfileStore(app.getFilesDir()).save(0,0,new ProfileStore.Entry("Testprofil",saved));
        DisplayTrendPreferences.save(app,1,words("Andere Anzeige"));
        SettingsTransaction.begin(app.getFilesDir(),new ProfileStore(app.getFilesDir()).load().slots.get(0).settings);
        try {DisplayTrendPreferences.save(app,2,words("Darf nicht schreiben"));fail();}catch(IOException expected){}
        ProfileController.recover(app);
        assertEquals("Profilwort",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));assertFalse(SettingsTransaction.pending(app.getFilesDir()));
        assertFalse(LivePreferences.alarms(app));assertEquals(saved.output.preferences.trendSpeech.words,new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech.words);
        assertTrue(DisplayTrendPreferences.load(app).revision>2);
    }
    @Test public void blankDefaultsAndCorruptDisplayPreferenceDoNotAffectSpeechOrAlarmSettings() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir());store.save(0,OutputPreferences.defaults());
        byte[] output=Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath());
        DisplayTrendPreferences.save(app,0,words("Ruhig"));DisplayTrendPreferences.save(app,1,TrendDisplayWords.defaults());
        assertEquals("Stabil",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));
        AppText.configure(Locale.ENGLISH);assertEquals("Steady",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));AppText.configure(Locale.GERMANY);
        app.getSharedPreferences(AppAppearance.PREFS,0).edit().putInt("trend_FLAT",7).commit();
        assertEquals("Stabil",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT));
        TrendDisplayActivity editor=open(TrendDisplayActivity.class,null).get();assertNull(tagged(editor,"trend_display_save"));
        assertEquals(7,app.getSharedPreferences(AppAppearance.PREFS,0).getInt("trend_FLAT",0));
        assertArrayEquals(output,Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath()));
    }
    @Test @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderDisplayEditorInBothLanguagesAndThemesWithSyntheticText() throws Exception {
        for(String language:new String[]{"de","en"})for(AppAppearance.Mode theme:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) {
            android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.setLocale(new Locale(language));app.getResources().updateConfiguration(config,app.getResources().getDisplayMetrics());AppAppearance.set(app,theme);
            ActivityController<TrendDisplayActivity> controller=open(TrendDisplayActivity.class,null);TrendDisplayActivity editor=controller.get();
            ((EditText)tagged(editor,"trend_display_FLAT")).setText("de".equals(language)?"Ruhig":"Level");
            ScrollView scroll=null;for(View view:views(editor.getWindow().getDecorView()))if(view instanceof ScrollView){scroll=(ScrollView)view;break;}
            assertNotNull(scroll);View body=scroll.getChildAt(0);body.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));body.layout(0,0,411,body.getMeasuredHeight());
            for(View view:views(body))if(view instanceof EditText && view.isShown()){assertEquals(1,((EditText)view).getMaxLines());assertTrue(view.getWidth()>200);}
            Bitmap bitmap=Bitmap.createBitmap(411,body.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(AppAppearance.colors(app).background);body.draw(canvas);
            File folder=new File("build/ui-previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"trend-display-"+language+"-"+theme.name().toLowerCase(Locale.ROOT)+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
            controller.pause().stop().destroy();controllers.remove(controller);
        }
    }
}
