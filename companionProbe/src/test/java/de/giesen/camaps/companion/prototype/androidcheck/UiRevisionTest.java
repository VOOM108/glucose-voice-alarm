// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.time.Duration;
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
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class UiRevisionTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private Context app;
    private final List<ActivityController<?>> opened=new ArrayList<>();
    @Before public void before() throws Exception {
        app=RuntimeEnvironment.getApplication();
        for(String prefs:new String[]{AppAppearance.PREFS,ArrowCoverage.PREFS,VectorReference.PREFS,VectorReference.SHAPE_PREFS,VectorReference.HISTORY_PREFS,"live_controls_v1",CamApsListener.PREFS}) app.getSharedPreferences(prefs,0).edit().clear().commit();
        for(String file:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,AlarmStateStore.FILE_NAME}) new File(app.getFilesDir(),file).delete();
        ProbeState.latest=null; ProbeState.vectorCandidate=null;
    }
    @After public void after() { for(ActivityController<?> c:opened) c.pause().stop().destroy(); }
    private <T extends Activity> ActivityController<T> open(Class<T> type,Bundle state) {
        ActivityController<T> c=Robolectric.buildActivity(type).create(state).start().resume().visible(); opened.add(c); return c;
    }
    private List<View> views(View view) { List<View> result=new ArrayList<>(); result.add(view); if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) result.addAll(views(((ViewGroup)view).getChildAt(i))); return result; }
    private View action(Activity activity,String name) {
        for(View view:views(activity.getWindow().getDecorView())) if(view instanceof TextView && (name.equals(((TextView)view).getText().toString()) || name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))) return view;
        throw new AssertionError("Missing action "+name);
    }
    private String hash(int value) { return String.format(Locale.ROOT,"%064x",value); }
    private VectorReference.Candidate candidate() { return new VectorReference.Candidate(hash(1),hash(2),"synthetic",Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)); }
    @Test public void tokensInsertAtTheCursorReplaceSelectionAndNeverLeaveATruncatedPlaceholder() {
        EditText field=new EditText(app); field.setText("Vor nach"); field.setSelection(4); assertTrue(TokenButtons.insert(field,"{wert}")); assertEquals("Vor {wert}nach",field.getText().toString());
        field.setSelection(4,10); assertTrue(TokenButtons.insert(field,"{trend}")); assertEquals("Vor {trend}nach",field.getText().toString());
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(field.length())}); field.setSelection(field.length()); assertFalse(TokenButtons.insert(field,"{wert}")); assertEquals("Vor {trend}nach",field.getText().toString());
        field.setEnabled(false); assertFalse(TokenButtons.insert(field,"{wert}"));
    }
    @Test public void trendWordDraftAndExpandedEditorSurviveRotationAndSaveWithOutputRoutes() throws Exception {
        ActivityController<OutputSettingsActivity> first=open(OutputSettingsActivity.class,null); action(first.get(),"Trendwörter").performClick();
        EditText stable=(EditText)action(first.get(),"stabil"); stable.setText("ruhig");
        Bundle state=new Bundle(); first.saveInstanceState(state).pause().stop().destroy(); opened.remove(first);
        OutputSettingsActivity restored=open(OutputSettingsActivity.class,state).get(); assertTrue(action(restored,"ruhig").isShown());
        action(restored,"Ausgabe-Einstellungen speichern").performClick();
        OutputPreferences saved=new OutputPreferenceStore(app.getFilesDir()).load().preferences; assertEquals("ruhig",saved.trendSpeech.word(NotificationParser.Trend.FLAT)); assertTrue(saved.tonesEnabled); assertTrue(saved.alarmSpeechEnabled);
        OutputModeUi.channel(restored,1,false,() -> {}); assertEquals("ruhig",new OutputPreferenceStore(app.getFilesDir()).load().preferences.trendSpeech.word(NotificationParser.Trend.FLAT));
    }
    @Test public void refreshedCandidateInvalidatesTheFrozenDialogEvenWithIdenticalPixelsAndShape() {
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        VectorReference.Candidate captured=candidate(); ProbeState.vectorCandidate=captured;
        ProbeDiagnosticsActivity activity=open(ProbeDiagnosticsActivity.class,null).get(); action(activity,"Pfeile prüfen").performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,2,2); assertTrue(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
        ProbeState.vectorCandidate=candidate(); assertFalse(VectorReference.canConfirm(captured,ProbeState.vectorCandidate));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600)); assertFalse(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
        assertFalse(app.getSharedPreferences(VectorReference.PREFS,0).contains(captured.key)); dialog.dismiss();
    }
    @Test public void explicitCorrectionQuarantinesAConflictingShapeAndKnownArrowKeepsCompactEditAction() {
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        VectorReference.Candidate current=candidate(); ProbeState.vectorCandidate=current;
        app.getSharedPreferences(VectorReference.PREFS,0).edit().putString(current.key,NotificationParser.Trend.UP.name()).commit();
        app.getSharedPreferences(VectorReference.SHAPE_PREFS,0).edit().putString(current.shapeKey,NotificationParser.Trend.UP.name()).commit();
        ProbeDiagnosticsActivity activity=open(ProbeDiagnosticsActivity.class,null).get(); action(activity,"Pfeile prüfen").performClick(); AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null,2,2); dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NotificationParser.Trend.UP_RIGHT,TrendReference.lookup(app.getSharedPreferences(VectorReference.PREFS,0).getAll(),current.key));
        assertEquals(ShapeReference.CONFLICT,app.getSharedPreferences(VectorReference.SHAPE_PREFS,0).getString(current.shapeKey,""));
        ProbeState.vectorCandidate=current; ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.UP_RIGHT,SystemClock.elapsedRealtime(),System.currentTimeMillis(),"synthetic");
        ProbeActivity home=open(ProbeActivity.class,null).get(); assertFalse(action(home,"Bekannte Pfeilgrafik prüfen/ändern …").isShown());
        assertTrue(action(home,"Pfeilzuordnung prüfen").isShown());
    }
    @Test public void coverageIsScopedToTheSourceVersionAndUnknownNeverCompletesADirection() {
        for(NotificationParser.Trend trend:new NotificationParser.Trend[]{NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.UP,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,NotificationParser.Trend.DOWN}) ArrowCoverage.observe(app,"synthetic-v1",trend);
        assertEquals(5,ArrowCoverage.count(app)); ArrowCoverage.observe(app,"synthetic-v1",NotificationParser.Trend.UNKNOWN); assertEquals(5,ArrowCoverage.count(app));
        ArrowCoverage.observe(app,"synthetic-v2",NotificationParser.Trend.UP_RIGHT); assertEquals(1,ArrowCoverage.count(app));
    }
    @Test public void theSevenChoiceDialogCanExplicitlyConfirmADoubleUpArrow() {
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        VectorReference.Candidate captured=candidate(); ProbeState.vectorCandidate=captured; ProbeDiagnosticsActivity activity=open(ProbeDiagnosticsActivity.class,null).get(); action(activity,"Pfeile prüfen").performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); assertEquals(7,dialog.getListView().getAdapter().getCount()); dialog.getListView().performItemClick(null,0,0); assertTrue(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled()); dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals(NotificationParser.Trend.DOUBLE_UP,TrendReference.lookup(app.getSharedPreferences(VectorReference.PREFS,0).getAll(),captured.key));
    }
    @Test public void valueSpeechCanBeSwitchedOnWithoutEnablingAlarmSpeechOrChangingLiveConsent() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir()); store.save(0,new OutputPreferences(AlarmEngine.OutputMode.TONES_ONLY,3,SpeechTemplate.VALUE_DEFAULT));
        ProbeActivity home=open(ProbeActivity.class,null).get(); action(home,"Werte sprechen").performClick();
        OutputPreferences p=store.load().preferences; assertTrue(p.valueSpeechEnabled); assertFalse(p.alarmSpeechEnabled); assertTrue(p.tonesEnabled); assertFalse(LivePreferences.alarms(app));
        action(home,"Ton").performClick(); assertTrue(store.load().preferences.tonesEnabled); assertTrue(((Switch)action(home,"Ton")).isChecked());
    }
    @Test public void enabledAlarmStagesMustHaveAnAvailableOutputEvenWhenAnotherGlobalRouteExists() throws Exception {
        AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        level=new AlarmLevel(new AlarmEngine.Rule(level.rule.id,level.rule.kind,true,level.rule.threshold,0,true,0,1440,new AlarmEngine.Outputs(true,false,3,5)),"Testton",level.toneUri);
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(level));
        ProbeActivity home=open(ProbeActivity.class,null).get(); action(home,"Ton").performClick(); assertTrue(new OutputPreferenceStore(app.getFilesDir()).load().preferences.tonesEnabled);
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
    }
    @Test public void changingAppearanceNeverChangesAlarmPreferencesOrLiveConsent() throws Exception {
        OutputPreferenceStore store=new OutputPreferenceStore(app.getFilesDir()); store.save(0,OutputPreferences.defaults());
        File file=new File(app.getFilesDir(),OutputPreferenceStore.FILE_NAME); byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
        AppearanceActivity activity=open(AppearanceActivity.class,null).get(); action(activity,"Dunkel").performClick();
        assertEquals(AppAppearance.Mode.DARK,AppAppearance.mode(app)); assertTrue(AppAppearance.dark(app)); assertFalse(LivePreferences.alarms(app));
        assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
    }
    @Test public void leavingTheArrowDialogDismissesItsFrozenPreviewAndStopsItsRefreshGuard() {
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit(); ProbeState.vectorCandidate=candidate();
        ActivityController<ProbeDiagnosticsActivity> controller=open(ProbeDiagnosticsActivity.class,null); action(controller.get(),"Pfeile prüfen").performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); assertTrue(dialog.isShowing()); controller.pause(); assertFalse(dialog.isShowing());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)); assertFalse(dialog.isShowing());
    }
}
