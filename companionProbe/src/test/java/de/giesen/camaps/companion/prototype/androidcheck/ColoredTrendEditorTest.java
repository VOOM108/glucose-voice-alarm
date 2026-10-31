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
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="de-w411dp-h891dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ColoredTrendEditorTest {
    private Context app;private final List<ActivityController<?>> owners=new ArrayList<>();
    @Before public void before()throws Exception{
        app=RuntimeEnvironment.getApplication();android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.setLocale(Locale.GERMANY);config.fontScale=1;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.scaledDensity=metrics.density;app.getResources().updateConfiguration(config,metrics);
        ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);ProbeState.clear();
        for(String name:new String[]{AppAppearance.PREFS,"live_controls_v1"})app.getSharedPreferences(name,0).edit().clear().commit();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),name).delete();
        new OutputPreferenceStore(app.getFilesDir()).save(0,OutputPreferences.defaults());
    }
    @After public void after(){for(ActivityController<?> c:owners)c.pause().stop().destroy();ProbeState.clear();AppText.configure(Locale.GERMANY);}
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state){ActivityController<T> c=Robolectric.buildActivity(type).create(state).start().resume().visible();owners.add(c);return c;}
    private View tag(Activity a,String tag){return a.getWindow().getDecorView().findViewWithTag(tag);}
    private void set(Activity a,String tag,String value){((EditText)tag(a,tag)).setText(value);}
    private void check(Activity a,String tag,boolean value){((CheckBox)tag(a,tag)).setChecked(value);}
    private List<View> views(View v){List<View> result=new ArrayList<>();result.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(views(((ViewGroup)v).getChildAt(i)));return result;}
    private View action(Activity a,String text){for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView && (text.contentEquals(((TextView)v).getText()) || text.equals(v.getContentDescription())))return v;throw new AssertionError(text);}
    @Test public void allDisplayDirectionsHaveIndependentColorCheckboxesAndSaveWithoutChangingSpeech()throws Exception{
        byte[] output=Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath());LivePreferences.set(app,true,true);
        TrendDisplayActivity a=open(TrendDisplayActivity.class,null).get();
        for(NotificationParser.Trend t:TrendDisplayWords.TRENDS){String normal="Basis "+t.name();set(a,"trend_display_"+t.name(),normal);
            for(ArrowColor c:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW}){String key="trend_display_"+c.name()+"_"+t.name();assertFalse(((CheckBox)tag(a,key+"_enabled")).isChecked());check(a,key+"_enabled",true);assertEquals(normal,((EditText)tag(a,key)).getText().toString());set(a,key,c.name()+" "+t.name());}}
        tag(a,"trend_display_save").performClick();assertTrue(a.isFinishing());
        for(NotificationParser.Trend t:TrendDisplayWords.TRENDS){assertEquals("Basis "+t.name(),DisplayTrendPreferences.title(app,t));assertEquals("RED "+t.name(),DisplayTrendPreferences.title(app,t,ArrowColor.RED));assertEquals("YELLOW "+t.name(),DisplayTrendPreferences.title(app,t,ArrowColor.YELLOW));}
        assertArrayEquals(output,Files.readAllBytes(new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME).toPath()));assertTrue(LivePreferences.alarms(app));
    }
    @Test public void disablingOwnTextRestoresInheritanceAndRetainsTheDormantDraft()throws Exception{
        TrendDisplayActivity a=open(TrendDisplayActivity.class,null).get();set(a,"trend_display_FLAT","Ruhig");check(a,"trend_display_RED_FLAT_enabled",true);set(a,"trend_display_RED_FLAT","Am unteren Rand");check(a,"trend_display_RED_FLAT_enabled",false);set(a,"trend_display_FLAT","Gleichbleibend");tag(a,"trend_display_save").performClick();
        assertEquals("Gleichbleibend",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.RED));
        TrendDisplayActivity b=open(TrendDisplayActivity.class,null).get();check(b,"trend_display_RED_FLAT_enabled",true);assertEquals("Am unteren Rand",((EditText)tag(b,"trend_display_RED_FLAT")).getText().toString());tag(b,"trend_display_save").performClick();assertEquals("Am unteren Rand",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.RED));
    }
    @Test public void displayColorDraftsSurviveRotationAndAStaleEditorCannotOverwriteNewSettings()throws Exception{
        ActivityController<TrendDisplayActivity> first=open(TrendDisplayActivity.class,null);check(first.get(),"trend_display_YELLOW_FLAT_enabled",true);set(first.get(),"trend_display_YELLOW_FLAT","Gelber Entwurf");Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();owners.remove(first);
        TrendDisplayActivity a=open(TrendDisplayActivity.class,state).get();assertTrue(((CheckBox)tag(a,"trend_display_YELLOW_FLAT_enabled")).isChecked());assertEquals("Gelber Entwurf",((EditText)tag(a,"trend_display_YELLOW_FLAT")).getText().toString());
        DisplayTrendPreferences.save(app,0,new TrendDisplayWords(Collections.nCopies(7,"Neu")));tag(a,"trend_display_save").performClick();assertFalse(a.isFinishing());assertEquals("Neu",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.YELLOW));
    }
    @Test public void speechColorsSaveIndependentlyAndSurviveGlobalChannelChanges()throws Exception{
        OutputSettingsActivity a=open(OutputSettingsActivity.class,null).get();action(a,"Trendwörter").performClick();set(a,"trend_speech_word_FLAT","gleichbleibend");check(a,"trend_speech_color_RED_FLAT_enabled",true);assertEquals("gleichbleibend",((EditText)tag(a,"trend_speech_color_RED_FLAT")).getText().toString());set(a,"trend_speech_color_RED_FLAT","gleichbleibend unten");check(a,"trend_speech_color_YELLOW_FLAT_enabled",true);set(a,"trend_speech_color_YELLOW_FLAT","gleichbleibend oben");action(a,"Ausgabe-Einstellungen speichern").performClick();assertTrue(a.isFinishing());
        OutputPreferences saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences;assertEquals("gleichbleibend unten",saved.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("gleichbleibend oben",saved.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.YELLOW));assertEquals("gleichbleibend",saved.trendSpeech.word(NotificationParser.Trend.FLAT));assertEquals("Stabil",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.RED));
        OutputModeUi.channel(a,1,false,()->{});assertEquals("gleichbleibend unten",new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));
    }
    @Test public void speechColorDraftAndPerDirectionMuteSurviveRotation(){
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class,null);action(first.get(),"Trendwörter").performClick();check(first.get(),"trend_speech_color_RED_FLAT_enabled",true);set(first.get(),"trend_speech_color_RED_FLAT","unten gleich");check(first.get(),"trend_speech_2",false);Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();owners.remove(first);
        OutputSettingsActivity a=open(OutputSettingsActivity.class,state).get();assertTrue(tag(a,"trend_speech_color_RED_FLAT").isShown());assertEquals("unten gleich",((EditText)tag(a,"trend_speech_color_RED_FLAT")).getText().toString());assertFalse(((CheckBox)tag(a,"trend_speech_2")).isChecked());action(a,"Ausgabe-Einstellungen speichern").performClick();
        try{TrendSpeech saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech;assertEquals("",saved.word(NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("unten gleich",saved.colors.red.get(2));}catch(IOException e){throw new AssertionError(e);}
    }
    @Test public void interruptedProfileApplyRestoresBothColorSetsAndClearsStaleFlags()throws Exception{
        TrendColorOverrides colors=new TrendColorOverrides(Collections.nCopies(7,"Profil rot"),Collections.nCopies(7,true),Collections.nCopies(7,""),Collections.nCopies(7,false));DisplayTrendPreferences.save(app,0,new TrendDisplayWords(Collections.nCopies(7,"Profil normal"),colors));
        OutputPreferenceStore outputs=new OutputPreferenceStore(app.getFilesDir());OutputPreferenceStore.Snapshot old=outputs.load();outputs.save(old.revision,old.preferences.withTrendSpeech(new TrendSpeech(old.preferences.trendSpeech.words,old.preferences.trendSpeech.enabled,colors)));ConfigurationSnapshot saved=ProfileController.capture(app);
        DisplayTrendPreferences.save(app,1,new TrendDisplayWords(Collections.nCopies(7,"Andere Basis"),new TrendColorOverrides(Collections.nCopies(7,"Alt"),Collections.nCopies(7,false),Collections.nCopies(7,"Alt gelb"),Collections.nCopies(7,true))));
        SettingsTransaction.begin(app.getFilesDir(),saved);ProfileController.recover(app);assertFalse(SettingsTransaction.pending(app.getFilesDir()));assertEquals("Profil rot",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.RED));assertEquals("Profil normal",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.YELLOW));assertFalse(DisplayTrendPreferences.load(app).words.colors.yellowEnabled.get(2));assertEquals("Profil rot",outputs.load().preferences.trendSpeech.word(NotificationParser.Trend.FLAT,ArrowColor.RED));assertFalse(LivePreferences.alarms(app));
    }
    @Test public void malformedColorPreferencesArePreservedAndBlockEditing()throws Exception{
        app.getSharedPreferences(AppAppearance.PREFS,0).edit().putInt("trend_RED_FLAT_enabled",7).commit();TrendDisplayActivity a=open(TrendDisplayActivity.class,null).get();assertNull(tag(a,"trend_display_save"));assertEquals(7,app.getSharedPreferences(AppAppearance.PREFS,0).getInt("trend_RED_FLAT_enabled",0));assertEquals("Stabil",DisplayTrendPreferences.title(app,NotificationParser.Trend.FLAT,ArrowColor.RED));
    }
    @Test @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderDisplayAndSpeechColorControlsInBothLanguagesThemesAndLargeFonts()throws Exception{
        for(String language:new String[]{"de","en"})for(AppAppearance.Mode theme:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK})for(float scale:new float[]{1f,2f}){
            android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.setLocale(new Locale(language));config.fontScale=scale;android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.scaledDensity=metrics.density*scale;app.getResources().updateConfiguration(config,metrics);AppAppearance.set(app,theme);
            ActivityController<TrendDisplayActivity> d=open(TrendDisplayActivity.class,null);ActivityController<OutputSettingsActivity> s=open(OutputSettingsActivity.class,null);
            for(Activity a:Arrays.asList(d.get(),s.get())){
                String prefix=a instanceof TrendDisplayActivity?"trend_display":"trend_speech_color";check(a,prefix+"_RED_FLAT_enabled",true);check(a,prefix+"_YELLOW_FLAT_enabled",true);
                set(a,prefix+"_RED_FLAT","de".equals(language)?"Gleichbleibend unten":"Steady below");set(a,prefix+"_YELLOW_FLAT","de".equals(language)?"Gleichbleibend oben":"Steady above");
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));for(View child:views(a.getWindow().getDecorView()))child.jumpDrawablesToCurrentState();
                View card=tag(a,a instanceof TrendDisplayActivity?"trend_display_card_FLAT":"trend_speech_card_FLAT");card.measure(View.MeasureSpec.makeMeasureSpec(371,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));card.layout(0,0,371,card.getMeasuredHeight());
                Bitmap image=Bitmap.createBitmap(371,card.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(AppAppearance.colors(app).surface);card.draw(canvas);File folder=new File("build/ui-previews");folder.mkdirs();
                try(FileOutputStream out=new FileOutputStream(new File(folder,"color-words-"+(a instanceof TrendDisplayActivity?"display":"speech")+"-"+language+"-"+theme.name().toLowerCase(Locale.ROOT)+"-"+scale+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
            }
            d.pause().stop().destroy();owners.remove(d);s.pause().stop().destroy();owners.remove(s);
        }
    }
}
