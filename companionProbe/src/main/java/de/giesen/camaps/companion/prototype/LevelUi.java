// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.view.View;
import android.widget.*;

final class LevelUi {
    static int dp(Activity activity,int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    static LinearLayout screen(Activity activity) {
        LinearLayout body=new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(AppAppearance.colors(activity).background);
        int padding=dp(activity,20); body.setPadding(padding,padding,padding,padding);
        ScrollView scroll=new ScrollView(activity); scroll.setBackgroundColor(AppAppearance.colors(activity).background); scroll.setFillViewport(true); scroll.addView(body); activity.setContentView(scroll);
        return body;
    }
    static TextView text(LinearLayout body,String content,int size) {
        TextView text=new TextView(body.getContext()); text.setText(content); text.setTextSize(size);
        if(size>=21) text.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        text.setTextColor(size>=18?AppAppearance.colors(body.getContext()).ink:AppAppearance.colors(body.getContext()).muted);
        int gap=Math.round(8*body.getResources().getDisplayMetrics().density); text.setPadding(0,gap,0,gap);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        if(body.getOrientation()==LinearLayout.HORIZONTAL) params.setMargins(gap,0,gap,0);
        body.addView(text,params); return text;
    }
    static Button button(LinearLayout body,String title,View.OnClickListener listener) {
        if(body.getOrientation()==LinearLayout.HORIZONTAL) { body.setGravity(android.view.Gravity.CENTER_VERTICAL); body.setBaselineAligned(false); }
        Button button=new UiActions.ActionButton(body.getContext()); button.setText(title); button.setAllCaps(false);
        UiActions.style(button,title);
        button.setOnClickListener(listener); body.addView(button); return button;
    }
    static CheckBox check(LinearLayout body,String title,boolean checked) {
        CheckBox check=new CheckBox(body.getContext()); check.setContentDescription(title);
        check.setText(title.equals(AppText.tr("Alarmstufe einschalten"))?AppText.tr("Stufe aktiv"):title.equals(AppText.tr("Gesprochener Alarm für diese Stufe"))?AppText.tr("Alarm sprechen"):title.equals(AppText.tr("Ton für diese Stufe"))?AppText.tr("Ton"):title);
        check.setChecked(checked); body.addView(check); return check;
    }
    static EditText field(LinearLayout body,String label,String initial,int inputType) {
        TextView heading=text(body,label,16);
        EditText field=new EditText(body.getContext()); field.setId(View.generateViewId());
        field.setSingleLine(true); field.setInputType(inputType); field.setText(initial); heading.setLabelFor(field.getId()); body.addView(field);
        if((inputType&android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0) field.setSingleLine(false);
        return field;
    }
    static void help(LinearLayout body,String title,String content) {
        TextView explanation=new TextView(body.getContext()); explanation.setText(content); explanation.setTextSize(14); explanation.setTextColor(AppAppearance.colors(body.getContext()).muted); explanation.setPadding(0,8,0,12);
        DisclosureRow.add(body,title,explanation);
    }
    private LevelUi() {}
}
