// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

/** Only isolated vectors are rendered, in memory on the phone. Never notification bitmaps. */
final class VectorReference {
    static final String PREFS = "confirmed_vector_references_v1";
    static final String SHAPE_PREFS = "confirmed_vector_shapes_v2";
    static final String HISTORY_PREFS = "vector_confirmation_history_v1";
    static final class Candidate {
        final String key, shapeKey, notificationKey;
        final Bitmap preview;
        final long receivedElapsed;
        Candidate(String key,String shapeKey,String notificationKey,Bitmap preview) {
            this.key=key; this.shapeKey=shapeKey; this.notificationKey=notificationKey; this.preview=preview;
            receivedElapsed=SystemClock.elapsedRealtime();
        }
        boolean current() {
            long age=SystemClock.elapsedRealtime()-receivedElapsed;
            return age>=0 && age<=60000;
        }
    }
    private Candidate unique;
    private boolean blocked;
    void block() { blocked=true; }
    void add(View view, Drawable drawable, String profile, String notificationKey) {
        if (!(drawable instanceof VectorDrawable)) return;
        // Transformed arrows cannot reuse an untransformed reference. Inspect all ancestors too.
        for (View v=view; v!=null;) {
            if (v.getRotation()!=0 || v.getRotationX()!=0 || v.getRotationY()!=0
                    || v.getScaleX()<=0 || v.getScaleY()<=0 || v.getAlpha()!=1) { block(); return; }
            ViewParent parent=v.getParent(); v=parent instanceof View?(View)parent:null;
        }
        if (view instanceof ImageView) {
            float[] m=new float[9]; ((ImageView)view).getImageMatrix().getValues(m);
            if (m[Matrix.MSKEW_X]!=0 || m[Matrix.MSKEW_Y]!=0 || m[Matrix.MSCALE_X]<=0
                    || m[Matrix.MSCALE_Y]<=0 || m[Matrix.MPERSP_0]!=0 || m[Matrix.MPERSP_1]!=0
                    || m[Matrix.MPERSP_2]!=1) { block(); return; }
        }
        int w=drawable.getIntrinsicWidth(),h=drawable.getIntrinsicHeight();
        if (w<=0 || h<=0 || w>256 || h>256 || profile==null) { block(); return; }
        Rect previous=new Rect(drawable.getBounds());
        Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        try {
            int rw=w>=h?64:Math.max(1,64*w/h),rh=h>=w?64:Math.max(1,64*h/w);
            drawable.setBounds((64-rw)/2,(64-rh)/2,(64+rw)/2,(64+rh)/2);
            drawable.draw(new Canvas(bitmap));
            int[] pixels=new int[64*64]; bitmap.getPixels(pixels,0,64,0,0,64,64);
            boolean visible=false; for (int pixel:pixels) if ((pixel>>>24)!=0) { visible=true; break; }
            if (!visible) { block(); return; }
            String key=TrendReference.fingerprint(profile+":"+w+":"+h,pixels);
            String shape=ShapeReference.fingerprint(profile,pixels,64,64);
            if (unique==null) unique=new Candidate(key,shape,notificationKey,bitmap);
            else if (!unique.key.equals(key) && (shape==null || !shape.equals(unique.shapeKey))) block();
        } catch (RuntimeException e) { block(); }
        finally { drawable.setBounds(previous); }
    }
    Candidate candidate() { return blocked?null:unique; }
    static boolean canConfirm(Candidate captured,Candidate current) {
        // A fresh notification requires an explicit new preview even if its pixels look identical.
        return captured!=null && captured==current && captured.current();
    }
}
