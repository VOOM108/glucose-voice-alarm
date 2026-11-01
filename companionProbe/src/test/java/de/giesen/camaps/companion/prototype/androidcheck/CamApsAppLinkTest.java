// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Application;
import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={28,34})
public class CamApsAppLinkTest {
    Application app;
    @Before public void before(){app=RuntimeEnvironment.getApplication();AppText.configure(Locale.GERMAN);}
    void installed(String name){
        PackageInfo pi=new PackageInfo();pi.packageName=name;pi.applicationInfo=new ApplicationInfo();pi.applicationInfo.packageName=name;pi.applicationInfo.enabled=true;
        ActivityInfo activity=new ActivityInfo();activity.packageName=name;activity.name=name+".Main";activity.enabled=true;activity.exported=true;activity.applicationInfo=pi.applicationInfo;pi.activities=new ActivityInfo[]{activity};
        shadowOf(app.getPackageManager()).installPackage(pi);ResolveInfo info=new ResolveInfo();info.activityInfo=activity;
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(name),info);
    }
    @Test public void onlyKnownInstalledAppsCanProvideAPlainLaunchIntent(){
        assertNull(CamApsAppLink.intent(app));installed("org.example.other");assertNull(CamApsAppLink.intent(app));
        installed("com.camdiab.fx_alert.mgdl");Intent target=CamApsAppLink.intent(app);assertNotNull(target);assertEquals(Intent.ACTION_MAIN,target.getAction());assertEquals("com.camdiab.fx_alert.mgdl",target.getComponent().getPackageName());assertTrue(target.hasCategory(Intent.CATEGORY_LAUNCHER));assertNull(target.getData());assertNull(target.getExtras());
        assertNull(shadowOf(app).getNextStartedActivity());assertNull(shadowOf(app).getNextStartedService());
    }
    @Test public void multipleInstalledVariantsUseAChooserRatherThanGuessing(){
        installed("com.camdiab.fx_alert.mmoll");installed("com.camdiab.fx_alert.mgdl");Intent chooser=CamApsAppLink.intent(app);assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());
        Intent first=chooser.getParcelableExtra(Intent.EXTRA_INTENT);android.os.Parcelable[] rest=chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS);assertEquals(1,rest.length);
        Set<String> packages=new HashSet<>(Arrays.asList(first.getComponent().getPackageName(),((Intent)rest[0]).getComponent().getPackageName()));assertEquals(new HashSet<>(Arrays.asList("com.camdiab.fx_alert.mmoll","com.camdiab.fx_alert.mgdl")),packages);
        assertNull(shadowOf(app).getNextStartedActivity());
    }
    @Test public void previewNeedsNoInstalledAppsAndNeitherAreaCanLaunchAnything(){
        Context offline=new ContextWrapper(app){@Override public PackageManager getPackageManager(){throw new AssertionError("Preview must not inspect installed apps");}};
        for(int[] size:new int[][]{{400,180},{220,180},{400,120}}){View preview=HomeWidget.preview(offline,size[0],size[1],WidgetConfigureActivity.sample(),WidgetAppearance.Settings.defaults(WidgetAppearance.Mode.APP)).apply(app,new FrameLayout(app));for(int id:new int[]{R.id.widget_reading_area,R.id.widget_chart_area}){View area=preview.findViewById(id);assertFalse(area.performClick());assertFalse(area.getContentDescription().toString().contains("öffnen"));}}
        assertNull(shadowOf(app).getNextStartedActivity());assertNull(shadowOf(app).getNextStartedService());
    }
    @Test public void readingAndChartLaunchSeparateAppsWithoutSendingValues(){
        installed("com.camdiab.fx_alert.mgdl");
        View widget=HomeWidget.view(app,71,400,180,WidgetConfigureActivity.sample(),WidgetAppearance.Settings.defaults(WidgetAppearance.Mode.APP)).apply(app,new FrameLayout(app));
        View reading=widget.findViewById(R.id.widget_reading_area),chart=widget.findViewById(R.id.widget_chart_area);
        assertTrue(reading.getContentDescription().toString().contains("CamAPS öffnen"));assertTrue(chart.getContentDescription().toString().contains("Dashboard öffnen"));
        assertTrue(reading.performClick());Intent source=shadowOf(app).getNextStartedActivity();assertNotNull(source);assertEquals("com.camdiab.fx_alert.mgdl",source.getComponent().getPackageName());assertNull(source.getData());assertNull(source.getExtras());
        assertTrue(chart.performClick());Intent own=shadowOf(app).getNextStartedActivity();assertNotNull(own);assertEquals(ProbeActivity.class.getName(),own.getComponent().getClassName());assertNull(shadowOf(app).getNextStartedService());
    }
    @Test public void unavailableCamapsIsAnnouncedAndFallsBackToTheDashboard(){
        View widget=HomeWidget.view(app,71,220,120,WidgetConfigureActivity.sample(),WidgetAppearance.Settings.defaults(WidgetAppearance.Mode.APP)).apply(app,new FrameLayout(app));
        View reading=widget.findViewById(R.id.widget_reading_area);assertTrue(reading.getContentDescription().toString().contains("CamAPS nicht verfügbar"));assertTrue(reading.performClick());Intent own=shadowOf(app).getNextStartedActivity();assertNotNull(own);assertEquals(ProbeActivity.class.getName(),own.getComponent().getClassName());
    }
}
