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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Native graphics with fixed synthetic data only. Images stay in the external build directory. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AppearanceRenderTest {
    @org.junit.Before public void acknowledgeForExistingFeatureTests(){ConsentTestSupport.accept(org.robolectric.RuntimeEnvironment.getApplication());}
    private List<View> views(View v) { List<View> result=new ArrayList<>(); result.add(v); if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) result.addAll(views(((ViewGroup)v).getChildAt(i))); return result; }
    @Test public void renderGapWarningEditorWithSyntheticDefaultsInBothThemes() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) {
            AppAppearance.set(app,mode); ActivityController<GapSettingsActivity> controller=Robolectric.buildActivity(GapSettingsActivity.class).create().start().resume().visible();
            try {
                View content=controller.get().findViewById(android.R.id.content); ScrollView scroll=null; for(View v:views(content)) if(v instanceof ScrollView) { scroll=(ScrollView)v; break; }
                assertNotNull(scroll); View body=scroll.getChildAt(0); body.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)); body.layout(0,0,411,body.getMeasuredHeight());
                Bitmap bitmap=Bitmap.createBitmap(411,body.getHeight(),Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); canvas.drawColor(AppAppearance.colors(app).background); body.draw(canvas);
                File folder=new File("build/ui-previews"); folder.mkdirs(); try(FileOutputStream out=new FileOutputStream(new File(folder,"gap-"+mode.name().toLowerCase(Locale.ROOT)+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
            } finally { controller.pause().stop().destroy(); }
        }
    }
    @Test public void renderPopulatedAlarmOverviewAndProfilesInBothThemes() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        File files=app.getFilesDir(); for(String name:new String[]{LevelStore.FILE_NAME,OutputPreferenceStore.FILE_NAME,ProfileStore.FILE_NAME,SettingsTransaction.FILE_NAME}) new File(files,name).delete();
        AlarmLevel level=new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level;
        new LevelStore(files).save(0,Collections.singletonList(new AlarmLevel(level.rule,"Synthetischer Hochalarm",level.toneUri,Collections.emptyList(),"SHORT_LONG")));
        new ProfileStore(files).save(0,0,new ProfileStore.Entry("Alltag · Beispiel",ProfileController.capture(app)));
        for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) for(boolean profiles:new boolean[]{false,true}) {
            AppAppearance.set(app,mode); Class<? extends Activity> type=profiles?ProfilesActivity.class:AlarmLevelsActivity.class;
            ActivityController<? extends Activity> controller=Robolectric.buildActivity(type).create().start().resume().visible();
            try {
                View content=controller.get().findViewById(android.R.id.content); ScrollView scroll=null; for(View v:views(content)) if(v instanceof ScrollView) { scroll=(ScrollView)v; break; }
                assertNotNull(scroll); View body=scroll.getChildAt(0); body.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)); body.layout(0,0,411,body.getMeasuredHeight());
                Bitmap bitmap=Bitmap.createBitmap(411,body.getHeight(),Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); canvas.drawColor(AppAppearance.colors(app).background); body.draw(canvas);
                if(mode==AppAppearance.Mode.DARK) for(View v:views(body)) if(v instanceof TextView && "Synthetischer Hochalarm".contentEquals(((TextView)v).getText())) {
                    assertEquals(AppAppearance.colors(app).ink,((TextView)v).getCurrentTextColor());
                    android.graphics.drawable.GradientDrawable card=(android.graphics.drawable.GradientDrawable)((View)v.getParent()).getBackground(); assertEquals(AppAppearance.colors(app).surface,card.getColor().getDefaultColor());
                }
                File folder=new File("build/ui-previews"); folder.mkdirs(); try(FileOutputStream out=new FileOutputStream(new File(folder,(profiles?"profiles":"levels")+"-"+mode.name().toLowerCase(Locale.ROOT)+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
            } finally { controller.pause().stop().destroy(); }
        }
    }
    @Test public void iconInkIsCenteredInsideItsTouchTargetAtNormalAndPixelDensity() {
        Context app=RuntimeEnvironment.getApplication();
        for(int density:new int[]{1,3}) {
            DisplayMetrics metrics=new DisplayMetrics(); metrics.setTo(app.getResources().getDisplayMetrics()); metrics.density=density; metrics.densityDpi=160*density; metrics.scaledDensity=density;
            app.getResources().updateConfiguration(app.getResources().getConfiguration(),metrics);
            LinearLayout row=new LinearLayout(app); Button button=LevelUi.button(row,"Ton anhören",v -> {});
            int width=52*density,height=48*density; button.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); button.layout(0,0,width,height);
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); button.draw(new Canvas(bitmap));
            int minX=width,minY=height,maxX=-1,maxY=-1,accent=AppAppearance.colors(app).accent;
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) if(bitmap.getPixel(x,y)==accent) { minX=Math.min(minX,x); maxX=Math.max(maxX,x); minY=Math.min(minY,y); maxY=Math.max(maxY,y); }
            assertTrue(maxX>minX); assertEquals(width/2.0,(minX+maxX+1)/2.0,2*density); assertEquals(height/2.0,(minY+maxY+1)/2.0,density);
        }
    }
    @Test public void renderNativeEditorsAndTokenButtonsWithOnlySyntheticOrEmptyFields() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) for(boolean alarm:new boolean[]{false,true}) {
            AppAppearance.set(app,mode);
            Class<? extends Activity> type=alarm?AlarmLevelActivity.class:OutputSettingsActivity.class;
            Intent intent=new Intent(app,type); if(alarm) intent.putExtra("kind",AlarmEngine.Kind.HIGH.name());
            ActivityController<? extends Activity> controller=Robolectric.buildActivity(type,intent).create().start().resume().visible();
            try {
                View content=controller.get().findViewById(android.R.id.content); ScrollView scroll=null;
                for(View v:views(content)) if(v instanceof ScrollView) { scroll=(ScrollView)v; break; }
                assertNotNull(scroll); View body=scroll.getChildAt(0);
                body.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)); body.layout(0,0,411,body.getMeasuredHeight());
                Bitmap bitmap=Bitmap.createBitmap(411,body.getHeight(),Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); canvas.drawColor(AppAppearance.colors(app).background); body.draw(canvas);
                File folder=new File("build/ui-previews"); folder.mkdirs();
                try(FileOutputStream out=new FileOutputStream(new File(folder,(alarm?"alarm":"output")+"-"+mode.name().toLowerCase(Locale.ROOT)+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
            } finally { controller.pause().stop().destroy(); }
        }
    }
    @Test public void renderLightDarkAndLargeFontDashboardWithoutWrappedNavigationButtons() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        for(boolean stale:new boolean[]{false,true}) for(boolean large:new boolean[]{false,true}) for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK}) {
            Configuration config=new Configuration(app.getResources().getConfiguration()); config.fontScale=large?1.35f:1; config.screenWidthDp=large?360:411;
            DisplayMetrics metrics=new DisplayMetrics(); metrics.setTo(app.getResources().getDisplayMetrics()); metrics.density=1; metrics.densityDpi=160; metrics.scaledDensity=config.fontScale;
            app.getResources().updateConfiguration(config,metrics); AppAppearance.set(app,mode);
            app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
            long posted=System.currentTimeMillis()-(stale?SpeechPolicy.MAX_RECEIPT_AGE_MS+1000:0);
            ProbeState.latest=new ProbeState.Observation(123,NotificationParser.Trend.FLAT,SystemClock.elapsedRealtime(),posted,"synthetic");
            org.robolectric.util.ReflectionHelpers.setField(ProbeState.latest,"receivedWall",posted); ProbeState.vectorCandidate=null;
            ActivityController<ProbeActivity> controller=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible(); ProbeActivity activity=controller.get();
            try {
                View decor=activity.getWindow().getDecorView(); int width=large?360:411,height=large?800:891;
                decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); decor.layout(0,0,width,height);
                long now=System.currentTimeMillis(); List<DiagnosticSeries.Sample> samples=new ArrayList<>();
                for(int i=0;i<=360;i++) if(i<150 || i>165) samples.add(new DiagnosticSeries.Sample(now-(360-i)*60000L,(int)Math.round(135+40*Math.sin(i/43.0))));
                List<AlarmLevel> levels=Arrays.asList(new SyntheticAlarmScenario(AlarmEngine.Kind.HIGH).level,new SyntheticAlarmScenario(AlarmEngine.Kind.LOW).level);
                for(View v:views(decor)) {
                    if("dashboard_time".equals(v.getTag())) {
                        TextView time=(TextView)v; assertTrue(time.getText().toString().matches("[0-9]{2}:[0-9]{2}")); assertEquals(stale?AppAppearance.colors(activity).error:AppAppearance.colors(activity).muted,time.getCurrentTextColor());
                        assertNotNull(time.getLayout()); assertEquals(1,time.getLayout().getLineCount());
                        assertTrue("Full clock must fit",time.getWidth()>=Math.ceil(time.getPaint().measureText(time.getText().toString())));
                        assertEquals(stale,time.getContentDescription().toString().contains("nicht mehr aktuell"));
                    }
                    if("dashboard_value".equals(v.getTag())) { assertNotNull(((TextView)v).getLayout()); assertEquals(1,((TextView)v).getLayout().getLineCount()); assertTrue(v.getWidth()>0); }
                    if(v instanceof HistoryChartView) { ((HistoryChartView)v).setSeries(DiagnosticSeries.build(samples,now)); ((HistoryChartView)v).setAlarmLevels(levels); }
                    if("history_summary".equals(v.getTag())) ((TextView)v).setText(samples.size()+" Werte · 1 Lücke");
                    if(v instanceof Button && v.isShown() && ((Button)v).getText().length()>0 && ((Button)v).getLayout()!=null)
                        assertTrue("Wrapped navigation: "+((Button)v).getText(),((Button)v).getLayout().getLineCount()<=1);
                }
                decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); decor.layout(0,0,width,height);
                Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); canvas.drawColor(AppAppearance.colors(activity).background); decor.draw(canvas);
                Set<Integer> colors=new HashSet<>(); for(int y=0;y<height;y+=11) for(int x=0;x<width;x+=11) colors.add(bitmap.getPixel(x,y)); assertTrue("Native graphics must produce real pixels",colors.size()>15);
                File folder=new File("build/ui-previews"); folder.mkdirs(); File image=new File(folder,"dashboard-"+(stale?"stale-":"")+mode.name().toLowerCase(Locale.ROOT)+(large?"-large":"")+".png");
                try(FileOutputStream out=new FileOutputStream(image)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
                assertEquals(mode==AppAppearance.Mode.DARK,AppAppearance.dark(activity));
            } finally { controller.pause().stop().destroy(); }
        }
    }
}
