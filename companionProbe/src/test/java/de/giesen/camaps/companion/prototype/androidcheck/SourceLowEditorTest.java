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
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi",shadows={AndroidUiTest.TestRingtones.class,AndroidUiTest.TestRingtone.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SourceLowEditorTest {
    private Context app;private final List<ActivityController<?>> owners=new ArrayList<>();
    @Before public void before()throws Exception{
        app=RuntimeEnvironment.getApplication();android.content.res.Configuration base=new android.content.res.Configuration(app.getResources().getConfiguration());base.setLocale(Locale.GERMANY);base.fontScale=1f;android.util.DisplayMetrics display=new android.util.DisplayMetrics();display.setTo(app.getResources().getDisplayMetrics());display.scaledDensity=display.density;app.getResources().updateConfiguration(base,display);ConsentTestSupport.accept(app);AppText.configure(Locale.GERMANY);
        for(String n:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME})new File(app.getFilesDir(),n).delete();
        app.getSharedPreferences("live_controls_v1",0).edit().clear().commit();app.getSharedPreferences(AppAppearance.PREFS,0).edit().clear().commit();
        new OutputPreferenceStore(app.getFilesDir()).save(0,OutputPreferences.defaults());
    }
    @After public void after(){for(ActivityController<?> c:owners)c.pause().stop().destroy();AppText.configure(Locale.GERMANY);}
    private ActivityController<AlarmLevelActivity> editor(){
        Intent intent=new Intent(app,AlarmLevelActivity.class).putExtra("kind","SOURCE_LOW");
        ActivityController<AlarmLevelActivity> c=Robolectric.buildActivity(AlarmLevelActivity.class,intent).create().start().resume().visible();owners.add(c);return c;
    }
    private List<View> views(View root){List<View> all=new ArrayList<>();all.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)all.addAll(views(((ViewGroup)root).getChildAt(i)));return all;}
    private View text(Activity a,String text){for(View v:views(a.getWindow().getDecorView()))if(v instanceof TextView && (text.contentEquals(((TextView)v).getText()) || text.equals(v.getContentDescription())))return v;throw new AssertionError(text+"; synthetic text="+views(a.getWindow().getDecorView()).stream().filter(v->v instanceof TextView).map(v->((TextView)v).getText().toString()).collect(java.util.stream.Collectors.joining(" | ")));}
    @Test public void listOpensANewDisabledSourceLevelWithExplicitConditionAndNoThresholdOrTimers()throws Exception{
        ActivityController<AlarmLevelsActivity> list=Robolectric.buildActivity(AlarmLevelsActivity.class).create().start().resume().visible();owners.add(list);
        text(list.get(),"Stufe für „Bald niedrig“ hinzufügen").performClick();Intent destination=shadowOf(list.get()).getNextStartedActivity();assertEquals("SOURCE_LOW",destination.getStringExtra("kind"));
        ActivityController<AlarmLevelActivity> c=editor();AlarmLevelActivity a=c.get();
        assertNotNull(a.getWindow().getDecorView().findViewWithTag("source_low_condition"));
        assertFalse(org.robolectric.util.ReflectionHelpers.<CheckBox>getField(a,"enabled").isChecked());
        assertNull(org.robolectric.util.ReflectionHelpers.getField(a,"repeat"));assertNull(org.robolectric.util.ReflectionHelpers.getField(a,"snooze"));
        assertNull(org.robolectric.util.ReflectionHelpers.<EditText>getField(a,"threshold").getParent());
        assertNotNull(a.getWindow().getDecorView().findViewWithTag("source_low_missing_boundary"));
        text(a,"Stufe speichern").performClick();assertTrue(a.isFinishing());AlarmLevel saved=new LevelStore(app.getFilesDir()).load().levels.get(0);
        assertFalse(saved.rule.enabled);assertEquals(AlarmEngine.Kind.SOURCE_LOW,saved.rule.kind);assertEquals("Laut Datenquelle bald niedrig",saved.speechText(0));assertFalse(LivePreferences.alarms(app));
    }
    @Test public void draftTextAndEnabledChoiceSurviveRotationWithoutEnablingLiveOutput(){
        ActivityController<AlarmLevelActivity> first=editor();AlarmLevelActivity a=first.get();
        org.robolectric.util.ReflectionHelpers.<EditText>getField(a,"speechTexts").setText("CamAPS bald niedrig");
        org.robolectric.util.ReflectionHelpers.<CheckBox>getField(a,"enabled").setChecked(true);
        Bundle state=new Bundle();first.saveInstanceState(state).pause().stop().destroy();owners.remove(first);
        ActivityController<AlarmLevelActivity> next=Robolectric.buildActivity(AlarmLevelActivity.class,new Intent(app,AlarmLevelActivity.class).putExtra("kind","SOURCE_LOW")).create(state).start().resume().visible();owners.add(next);
        assertEquals("CamAPS bald niedrig",org.robolectric.util.ReflectionHelpers.<EditText>getField(next.get(),"speechTexts").getText().toString());
        assertTrue(org.robolectric.util.ReflectionHelpers.<CheckBox>getField(next.get(),"enabled").isChecked());assertFalse(LivePreferences.alarms(app));
    }
    @Test public void renderSourceEditorWithSyntheticSettingsInBothLanguagesThemesAndLargeFonts()throws Exception{
        new LevelStore(app.getFilesDir()).save(0,Collections.singletonList(LevelForm.parse("low",AlarmEngine.Kind.LOW,"Test niedrig","80","","3","5",true,true,true,AlarmLevel.DEFAULT_TONE,true,0,1440)));
        for(String language:new String[]{"de","en"})for(AppAppearance.Mode theme:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK})for(float scale:new float[]{1f,2f}){
            android.content.res.Configuration config=new android.content.res.Configuration(app.getResources().getConfiguration());config.fontScale=scale;config.setLocale(new Locale(language));
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.scaledDensity=metrics.density*scale;
            app.getResources().updateConfiguration(config,metrics);AppText.configure(new Locale(language));AppAppearance.set(app,theme);
            ActivityController<AlarmLevelActivity> c=editor();AlarmLevelActivity a=c.get();assertNotNull(a.getWindow().getDecorView().findViewWithTag("source_low_condition"));
            org.robolectric.util.ReflectionHelpers.<CheckBox>getField(a,"speech").setChecked(true);
            ScrollView scroll=null;for(View v:views(a.getWindow().getDecorView()))if(v instanceof ScrollView){scroll=(ScrollView)v;break;}assertNotNull(scroll);
            View body=scroll.getChildAt(0);body.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));body.layout(0,0,411,body.getMeasuredHeight());
            Bitmap image=Bitmap.createBitmap(411,body.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(AppAppearance.colors(app).surface);body.draw(canvas);
            File folder=new File("build/ui-previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"source-low-editor-"+language+"-"+theme.name().toLowerCase(Locale.ROOT)+"-"+scale+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
            c.pause().stop().destroy();owners.remove(c);
        }
    }
}
