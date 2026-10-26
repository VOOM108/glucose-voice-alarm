// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.*;

/** Plain chevron row for local disclosure; action buttons keep their filled surfaces. */
final class DisclosureRow extends TextView {
    private final View content;
    private boolean expanded;
    DisclosureRow(Context context,String title,View content) {
        super(context); this.content=content; setText(title); setContentDescription(title); setTextSize(16);
        setTextColor(AppAppearance.colors(context).ink); setGravity(android.view.Gravity.CENTER_VERTICAL);
        int gap=Math.round(8*getResources().getDisplayMetrics().density); setPadding(0,gap,0,gap); setCompoundDrawablePadding(gap);
        setMinHeight(Math.round(48*getResources().getDisplayMetrics().density)); setClickable(true); setFocusable(true);
        setOnClickListener(v -> setExpanded(!expanded)); setExpanded(false);
    }
    static DisclosureRow add(LinearLayout parent,String title,View content) {
        DisclosureRow row=new DisclosureRow(parent.getContext(),title,content); parent.addView(row,new LinearLayout.LayoutParams(-1,-2)); parent.addView(content); return row;
    }
    void setExpanded(boolean expanded) {
        this.expanded=expanded; content.setVisibility(expanded?View.VISIBLE:View.GONE);
        setCompoundDrawablesRelativeWithIntrinsicBounds(new Chevron(expanded,AppAppearance.colors(getContext()).muted,getResources().getDisplayMetrics().density),null,null,null);
        if(Build.VERSION.SDK_INT>=30) setStateDescription(AppText.tr(expanded?"Ausgeklappt":"Eingeklappt"));
    }
    boolean expanded() { return expanded; }
    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
    private static final class Chevron extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final boolean down; private final int size;
        Chevron(boolean down,int color,float density) { this.down=down; size=Math.round(20*density); paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        @Override public int getIntrinsicWidth() { return size; } @Override public int getIntrinsicHeight() { return size; }
        @Override public void draw(Canvas canvas) { canvas.save(); Rect b=getBounds(); canvas.translate(b.left,b.top); canvas.scale(b.width()/20f,b.height()/20f); Path p=new Path(); if(down) { p.moveTo(5,8); p.lineTo(10,13); p.lineTo(15,8); } else { p.moveTo(8,5); p.lineTo(13,10); p.lineTo(8,15); } canvas.drawPath(p,paint); canvas.restore(); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); } @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); } @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
