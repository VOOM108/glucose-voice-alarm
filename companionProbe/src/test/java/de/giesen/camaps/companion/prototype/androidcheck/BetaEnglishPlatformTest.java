// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,34},qualifiers="en",shadows={AndroidUiTest.TestBellPlayer.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class BetaEnglishPlatformTest {
    private Context app; private final List<ActivityController<?>> activities=new ArrayList<>(); private final List<PreviewAudio> owners=new ArrayList<>();
    @Before public void before() {
        app=RuntimeEnvironment.getApplication(); AppText.configure(Locale.ENGLISH);
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME}) new File(app.getFilesDir(),name).delete();
        for(String name:new String[]{"live_controls_v1",SoundDefaults.PREFS,AppAppearance.PREFS,CamApsListener.PREFS,ArrowCoverage.PREFS}) app.getSharedPreferences(name,0).edit().clear().commit();
        ShadowTextToSpeech.addVoice(new Voice("de-test",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        ShadowTextToSpeech.addVoice(new Voice("en-test",Locale.US,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        ProbeState.latest=null; ProbeState.vectorCandidate=null;
    }
    @After public void after() { for(ActivityController<?> c:activities)c.pause().stop().destroy(); for(PreviewAudio audio:owners)audio.close(); AppText.configure(Locale.GERMAN); }
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state) { ActivityController<T> c=Robolectric.buildActivity(type).create(state).start().resume().visible(); activities.add(c); shadowOf(Looper.getMainLooper()).idle(); return c; }
    private List<View> views(View v) { List<View> all=new ArrayList<>(); all.add(v); if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)all.addAll(views(((ViewGroup)v).getChildAt(i))); return all; }
    private View action(Activity a,String title) { for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView && (title.contentEquals(((TextView)v).getText()) || title.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())))return v; throw new AssertionError(title); }
    @Test public void newEnglishInterfaceUsesEnglishDefaultsAndTheUntranslatedName() throws Exception {
        ProbeActivity a=open(ProbeActivity.class,null).get(); assertNotNull(action(a,"Glucose Voice Alarm")); assertNotNull(action(a,"Output enabled")); assertNotNull(action(a,"Speak alarms")); assertNotNull(action(a,"Speak readings"));
        OutputPreferences p=new OutputPreferenceStore(app.getFilesDir()).load().preferences; assertEquals("en",p.speechLanguage); assertEquals(SpeechTemplate.valueDefault("en"),p.valueTemplate); assertFalse(LivePreferences.alarms(app));
    }
    @Test public void languageChangePreservesUserTextAndSurvivesRotation() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir()); store.save(0,new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,"Eigener Text {wert}"));
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class,null); Spinner spinner=null; for(View v:views(first.get().getWindow().getDecorView()))if(v instanceof Spinner)spinner=(Spinner)v;
        assertNotNull(spinner); spinner.setSelection(1); shadowOf(Looper.getMainLooper()).idle(); assertNotNull(action(first.get(),"Eigener Text {wert}"));
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first); OutputSettingsActivity second=open(OutputSettingsActivity.class,state).get();
        action(second,"Save output settings").performClick(); OutputPreferences saved=store.load().preferences; assertEquals("en",saved.speechLanguage); assertEquals("Eigener Text {wert}",saved.valueTemplate); assertEquals("stabil",saved.trendSpeech.words.get(2));
    }
    @Test public void englishSpeechSelectsAnEnglishOfflineVoice() {
        PreviewAudio audio=new PreviewAudio(app,text -> {}); owners.add(audio); audio.prepareSpeech(); TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); assertNotNull(tts);
        shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle(); assertTrue(audio.speak("Synthetic English preview")); assertEquals("en",tts.getVoice().getLocale().getLanguage());
    }
    @Test public void disclosuresHaveChevronAndStateWithoutActionButtonSurface() {
        LinearLayout body=new LinearLayout(app); TextView content=new TextView(app); DisclosureRow row=DisclosureRow.add(body,"More details",content);
        assertFalse(((View)row) instanceof Button); assertNull(row.getBackground()); assertNotNull(row.getCompoundDrawablesRelative()[0]); assertEquals(View.GONE,content.getVisibility());
        row.performClick(); assertTrue(row.expanded()); assertEquals(View.VISIBLE,content.getVisibility()); row.performClick(); assertFalse(row.expanded());
    }
    @Test public void gapTextSurvivesRotationAndIsSavedWithoutLiveConsent() throws Exception {
        ActivityController<GapSettingsActivity> first=open(GapSettingsActivity.class,null); ((EditText)first.get().findViewById(R.id.gap_speech_template)).setText("No readings for {minutes}. {trend}");
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); activities.remove(first); GapSettingsActivity second=open(GapSettingsActivity.class,state).get();
        assertEquals("No readings for {minutes}. {trend}",((EditText)second.findViewById(R.id.gap_speech_template)).getText().toString()); action(second,"Save").performClick();
        assertEquals("No readings for {minutes}. {trend}",new OutputPreferenceStore(app.getFilesDir()).load().preferences.gap.speechTemplate); assertFalse(LivePreferences.alarms(app));
    }
    @Test public void oldFiveGroupCoverageCannotClaimDoubleArrowsAndNewCoverageCountsAllSeven() {
        app.getSharedPreferences(ArrowCoverage.PREFS,0).edit().putString("profile","fixed-test").putInt("mask",31).commit(); assertEquals(0,ArrowCoverage.count(app));
        for(NotificationParser.Trend trend:NotificationParser.Trend.values())ArrowCoverage.observe(app,"fixed-test",trend);
        assertEquals(7,ArrowCoverage.count(app)); assertEquals(5,ArrowCoverage.group(NotificationParser.Trend.DOUBLE_UP)); assertEquals(6,ArrowCoverage.group(NotificationParser.Trend.DOUBLE_DOWN));
    }
    @Test public void gapPreviewUsesTheDraftTemplateAndIsClearlySynthetic() {
        GapSettingsActivity a=open(GapSettingsActivity.class,null).get(); ((EditText)a.findViewById(R.id.gap_speech_template)).setText("My gap {minutes}. {trend}");
        TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance(); shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle();
        action(a,"Preview alarm speech with test data").performClick(); String spoken=shadowOf(tts).getLastSpokenText(); assertTrue(spoken.startsWith("This is a gap warning test.")); assertTrue(spoken.contains("My gap 4.")); assertFalse(spoken.contains("123"));
    }
    @Test public void englishPlaceholderButtonsInsertAliasesAtTheCursor() {
        LinearLayout body=new LinearLayout(app);EditText field=new EditText(app);field.setText("ab");field.setSelection(1);TokenButtons.add(body,field,"{wert}","{einheit}","{trend}");
        View value=null;for(View v:views(body))if(v instanceof Button && "{value}".contentEquals(((Button)v).getText()))value=v;assertNotNull(value);value.performClick();assertEquals("a{value}b",field.getText().toString());
    }
    @Test public void englishSpeechNeverFallsBackToAnAvailableGermanVoice() {
        ShadowTextToSpeech.reset(); ShadowTextToSpeech.addVoice(new Voice("de-only",Locale.GERMANY,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));
        PreviewAudio audio=new PreviewAudio(app,text -> {});owners.add(audio);audio.prepareSpeech();TextToSpeech tts=ShadowTextToSpeech.getLastTextToSpeechInstance();shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS);shadowOf(Looper.getMainLooper()).idle();assertFalse(audio.speak("English only"));
    }
}
