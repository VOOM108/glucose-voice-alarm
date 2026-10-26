// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.util.DisplayMetrics;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="en-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BetaEnglishRenderTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private List<View> views(View v) { List<View> list=new ArrayList<>();list.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)list.addAll(views(((ViewGroup)v).getChildAt(i)));return list; }
    @Test public void renderEnglishHomeAndEditorsInBothThemesAndCheckToneButtonAlignment() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,SettingsTransaction.FILE_NAME,ProfileStore.FILE_NAME})new File(app.getFilesDir(),name).delete();
        for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) for(String page:new String[]{"home","home-large","output","output-words","gap","alarm","home-mmol","units","alarm-mmol"}) {
            boolean large=page.endsWith("large");int width=large?360:411;
            Configuration config=new Configuration(app.getResources().getConfiguration());config.setLocale(Locale.ENGLISH);config.fontScale=large?1.35f:1;config.screenWidthDp=width;
            DisplayMetrics metrics=new DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.density=1;metrics.densityDpi=160;metrics.scaledDensity=config.fontScale;app.getResources().updateConfiguration(config,metrics);
            UnitPreferences.reset(app);UnitPreferences.accept(app,page.endsWith("mmol")?GlucoseUnit.MMOL:GlucoseUnit.MGDL);
            AppAppearance.set(app,mode);AppText.configure(Locale.ENGLISH);
            long wall=System.currentTimeMillis(); List<HistoryAnalysis.Point> trendPoints=Arrays.asList(HistoryAnalysis.Point.received(132,wall-180000),HistoryAnalysis.Point.received(129,wall-120000),HistoryAnalysis.Point.received(126,wall-60000),HistoryAnalysis.Point.received(123,wall));
            ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.DOUBLE_UP,SystemClock.elapsedRealtime(),wall,"synthetic",ReceiptRate.calculate(trendPoints,wall));ProbeState.vectorCandidate=null;
            app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
            Class<? extends Activity> type=page.startsWith("home")?ProbeActivity.class:page.startsWith("output")?OutputSettingsActivity.class:page.equals("gap")?GapSettingsActivity.class:page.equals("units")?DeveloperOptionsActivity.class:AlarmLevelActivity.class;
            Intent intent=new Intent(app,type);if(type==AlarmLevelActivity.class)intent.putExtra("kind",AlarmEngine.Kind.HIGH.name());
            ActivityController<? extends Activity> controller=Robolectric.buildActivity(type,intent).create().start().resume().visible();
            try {
                if(page.equals("output-words")) for(View v:views(controller.get().getWindow().getDecorView()))if(v instanceof DisclosureRow && "Trend phrases".contentEquals(((TextView)v).getText()))v.performClick();
                ScrollView scroll=null;for(View v:views(controller.get().getWindow().getDecorView()))if(v instanceof ScrollView){scroll=(ScrollView)v;break;}assertNotNull(scroll);View body=scroll.getChildAt(0);
                body.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));body.layout(0,0,width,body.getMeasuredHeight());
                assertEquals("en",AppText.language());
                for(View v:views(body)) {
                    if(v instanceof DisclosureRow) { assertNull(v.getBackground());assertNotNull(((TextView)v).getCompoundDrawablesRelative()[0]); }
                    if(v instanceof Button && "Select sound".contentEquals(v.getContentDescription())) {
                        ViewGroup row=(ViewGroup)v.getParent();Button play=null;for(int i=0;i<row.getChildCount();i++)if("Preview sound".contentEquals(row.getChildAt(i).getContentDescription()))play=(Button)row.getChildAt(i);
                        assertNotNull(play);assertEquals(v.getTop()+v.getHeight()/2f,play.getTop()+play.getHeight()/2f,1f);
                    }
                    if("dashboard_time".equals(v.getTag()))assertTrue(v.getWidth()>=Math.ceil(((TextView)v).getPaint().measureText(((TextView)v).getText().toString())));
                    if("app_name".equals(v.getTag()))assertTrue("Full app name must fit",v.getWidth()>=Math.ceil(((TextView)v).getPaint().measureText(((TextView)v).getText().toString())));
                }
                Bitmap bitmap=Bitmap.createBitmap(width,body.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(AppAppearance.colors(app).background);body.draw(canvas);
                File folder=new File("build/ui-previews");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"beta-en-"+page+"-"+mode.name().toLowerCase(Locale.ROOT)+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
            } finally {controller.pause().stop().destroy();}
        }
        AppText.configure(Locale.GERMAN);
    }
}
