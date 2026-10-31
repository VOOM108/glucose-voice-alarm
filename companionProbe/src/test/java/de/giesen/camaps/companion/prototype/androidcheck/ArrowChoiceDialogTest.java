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
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Whole native dialog geometry, not only its custom view. Synthetic isolated arrow only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="de-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class ArrowChoiceDialogTest {
    private Context app;
    @Before public void before(){
        app=RuntimeEnvironment.getApplication();ConsentTestSupport.accept(app);AppText.configure(Locale.GERMAN);ProbeState.clear();
        app.getSharedPreferences(CamApsListener.PREFS,0).edit().putBoolean(CamApsListener.ENABLED,true).commit();
        for(String prefs:new String[]{VectorReference.PREFS,VectorReference.SHAPE_PREFS,VectorReference.HISTORY_PREFS})app.getSharedPreferences(prefs,0).edit().clear().commit();
    }
    @After public void after(){ProbeState.clear();ProbeState.vectorCandidate=null;}
    private VectorReference.Candidate source(){
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.RED);c.drawCircle(32,32,28,p);p.setColor(Color.WHITE);p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);
        Path arrow=new Path();arrow.moveTo(13,32);arrow.lineTo(49,32);arrow.moveTo(37,20);arrow.lineTo(49,32);arrow.lineTo(37,44);c.drawPath(arrow,p);
        return new VectorReference.Candidate(String.join("",Collections.nCopies(64,"a")),null,"synthetic",b);
    }
    private Rect full(View view){int[] at=new int[2];view.getLocationOnScreen(at);return new Rect(at[0],at[1],at[0]+view.getWidth(),at[1]+view.getHeight());}
    private Rect visible(View view){Rect r=new Rect();assertTrue(view.getTag()+" visible",view.getGlobalVisibleRect(r));return r;}
    private void check(int width,int height,String name)throws Exception {
        for(AppAppearance.Mode mode:new AppAppearance.Mode[]{AppAppearance.Mode.LIGHT,AppAppearance.Mode.DARK})for(float scale:new float[]{1f,1.5f,2f}){
            Configuration config=new Configuration(app.getResources().getConfiguration());config.fontScale=scale;
            DisplayMetrics metrics=new DisplayMetrics();metrics.setTo(app.getResources().getDisplayMetrics());metrics.scaledDensity=metrics.density*scale;
            app.getResources().updateConfiguration(config,metrics);AppAppearance.set(app,mode);
            ProbeState.vectorCandidate=source();ActivityController<ProbeActivity> owner=Robolectric.buildActivity(ProbeActivity.class).create().start().resume().visible();
            try {
                owner.get().getWindow().getDecorView().findViewWithTag("edit_vector").performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
                View decor=dialog.getWindow().getDecorView();int w=width-24,h=height-24;
                decor.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
                assertTrue(decor.getHeight()<=h);
                ImageView preview=decor.findViewWithTag("vector_preview");assertSame(ProbeState.vectorCandidate.preview,((android.graphics.drawable.BitmapDrawable)preview.getDrawable()).getBitmap());
                assertEquals(full(preview),visible(preview));assertTrue(preview.getHeight()>=32);
                for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
                    Button button=dialog.getButton(id);assertEquals(button.getText().toString(),full(button),visible(button));assertTrue(full(decor).contains(full(button)));
                }
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                ScrollView scroll=decor.findViewWithTag("arrow_choice_scroll");Rect before=full(preview);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
                decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());assertEquals(before,full(preview));assertEquals(full(preview),visible(preview));
                for(int direction=0;direction<7;direction++){
                    Button normal=decor.findViewWithTag("arrow_choice_"+direction),red=decor.findViewWithTag("arrow_choice_"+(direction+7)),yellow=decor.findViewWithTag("arrow_choice_"+(direction+14));
                    assertEquals(normal.getText().toString(),red.getText().toString());assertEquals(normal.getText().toString(),yellow.getText().toString());
                    assertTrue(normal.getLeft()<red.getLeft());assertTrue(red.getLeft()<yellow.getLeft());assertEquals(normal.getTop(),red.getTop());assertEquals(normal.getTop(),yellow.getTop());assertTrue(normal.getWidth()>=48);assertTrue(normal.getHeight()>=48);
                    assertTrue(normal.getContentDescription().length()>1);assertTrue(red.getContentDescription().toString().contains("rot"));assertTrue(yellow.getContentDescription().toString().contains("gelb"));
                }
                Button last=decor.findViewWithTag("arrow_choice_20");assertEquals(full(last),visible(last));last.performClick();assertTrue(last.isSelected());assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                File folder=new File("build/ui-previews");assertTrue(folder.isDirectory()||folder.mkdirs());Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
                try(FileOutputStream out=new FileOutputStream(new File(folder,"arrow-grid-"+name+"-"+mode.name().toLowerCase(Locale.ROOT)+"-"+scale+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
                dialog.dismiss();
            }finally{owner.pause().stop().destroy();}
        }
    }
    @Test public void portraitKeepsSourcePreviewActionsAndEveryColourReachable()throws Exception{check(411,891,"portrait");}
    @Test @Config(qualifiers="de-w320dp-h480dp-mdpi") public void smallPortraitKeepsSourcePreviewAndActionsReachableWithLargeFonts()throws Exception{check(320,480,"small");}
    @Test @Config(qualifiers="de-w640dp-h360dp-mdpi") public void landscapeKeepsSourcePreviewAndActionsReachableWithLargeFonts()throws Exception{check(640,360,"landscape");}
}
