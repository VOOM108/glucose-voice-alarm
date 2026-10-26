// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SafetyRenderTest {
    private void readable(View v){if(v instanceof TextView){TextView t=(TextView)v;assertFalse("Notice must not be ellipsized",t.getEllipsize()!=null);}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)readable(((ViewGroup)v).getChildAt(i));}
    @Test public void warningAndTermsAreScrollableInBothLanguagesThemesAndLargeFont() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        for(String language:new String[]{"de","en"})for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK})for(boolean large:new boolean[]{false,true})for(boolean terms:new boolean[]{false,true}){
            int width=large?360:411;Configuration config=new Configuration(app.getResources().getConfiguration());config.setLocale(Locale.forLanguageTag(language));config.fontScale=large?1.35f:1;config.screenWidthDp=width;
            DisplayMetrics metrics=new DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.density=1;metrics.densityDpi=160;metrics.scaledDensity=config.fontScale;app.getResources().updateConfiguration(config,metrics);AppText.configure(Locale.forLanguageTag(language));AppAppearance.set(app,mode);
            app.getSharedPreferences(SafetyConsent.PREFS,0).edit().clear().commit();if(terms)assertTrue(SafetyConsent.acknowledge(app,SafetyConsent.Stage.WARNING,true));
            ActivityController<SafetyActivity> c=Robolectric.buildActivity(SafetyActivity.class).create().start().resume().visible();
            try{
                ScrollView scroll=(ScrollView)((ViewGroup)c.get().findViewById(android.R.id.content)).getChildAt(0);View body=scroll.getChildAt(0);
                body.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));body.layout(0,0,width,body.getMeasuredHeight());readable(body);assertTrue(body.getHeight()>700);
                Bitmap bitmap=Bitmap.createBitmap(width,body.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(AppAppearance.colors(app).background);body.draw(canvas);
                File folder=new File("build/ui-previews");folder.mkdirs();String name="safety-"+language+"-"+(terms?"terms":"warning")+"-"+mode.name().toLowerCase(Locale.ROOT)+(large?"-large":"")+".png";
                try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
            }finally{c.pause().stop().destroy();}
        }
        AppText.configure(Locale.GERMAN);
    }
}
