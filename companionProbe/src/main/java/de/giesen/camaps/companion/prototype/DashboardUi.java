// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;

final class DashboardUi {
    static int background(android.content.Context c) { return AppAppearance.colors(c).background; }
    static int ink(android.content.Context c) { return AppAppearance.colors(c).ink; }
    static int accent(android.content.Context c) { return AppAppearance.colors(c).accent; }
    static int muted(android.content.Context c) { return AppAppearance.colors(c).muted; }
    static LinearLayout card(Activity activity,LinearLayout parent) {
        LinearLayout card=new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL);
        int padding=LevelUi.dp(activity,16); card.setPadding(padding,padding,padding,padding);
        GradientDrawable background=new GradientDrawable(); background.setColor(AppAppearance.colors(activity).surface); background.setCornerRadius(LevelUi.dp(activity,18));
        card.setBackground(background);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,0,0,LevelUi.dp(activity,14)); parent.addView(card,params); return card;
    }
    static void type(TextView view,int color,boolean bold) {
        view.setTextColor(color); view.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
    }
    static LinearLayout navigationRow(Activity activity,LinearLayout parent) {
        LinearLayout row=new LinearLayout(activity);
        boolean narrow=activity.getResources().getConfiguration().screenWidthDp<360 || activity.getResources().getConfiguration().fontScale>1.2f;
        row.setOrientation(narrow?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL); parent.addView(row); return row;
    }
    static void navigationSize(Activity activity,LinearLayout row,Button button) {
        LinearLayout.LayoutParams p=row.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);
        int gap=LevelUi.dp(activity,3); p.setMargins(gap,gap,gap,gap); button.setLayoutParams(p);
    }
    private DashboardUi() {}
}
