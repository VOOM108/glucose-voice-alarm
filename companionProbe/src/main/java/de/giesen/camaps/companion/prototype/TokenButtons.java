// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.text.*;
import android.widget.*;
/** Cursor-aware literal token insertion, including selected text and input limits. */
final class TokenButtons {
    static void add(LinearLayout parent,EditText field,String... tokens) {
        HorizontalScrollView scroll=new HorizontalScrollView(parent.getContext()); scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true); scroll.setFadingEdgeLength(24);
        LinearLayout row=new LinearLayout(parent.getContext()); row.setOrientation(LinearLayout.HORIZONTAL); scroll.addView(row); parent.addView(scroll);
        field.setTag(row);
        for(String original:tokens) {
            final String token=AppText.language().equals("en")?englishToken(original):original;
            Button button=new Button(parent.getContext()); UiActions.style(button,token); button.setContentDescription(AppText.tr("Einfügen: ")+token);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2); p.setMargins(0,0,8,0); row.addView(button,p);
            button.setOnClickListener(v -> insert(field,token));
        }
    }
    private static String englishToken(String token) {
        switch(token) { case "{farbe}":return "{color}"; case "{wert}":return "{value}"; case "{einheit}":return "{unit}"; case "{stufe}":return "{level}"; case "{grenze}":return "{threshold}"; case "{dauer_minuten}":return "{duration_minutes}"; case "{alter_sekunden}":return "{age_seconds}"; default:return token; }
    }
    static void enabled(EditText field) { if(field.getTag() instanceof LinearLayout) { LinearLayout row=(LinearLayout)field.getTag(); for(int i=0;i<row.getChildCount();i++) row.getChildAt(i).setEnabled(field.isEnabled()); } }
    static boolean insert(EditText field,String token) {
        if(!field.isEnabled()) return false;
        Editable text=field.getText(); int a=field.getSelectionStart(),b=field.getSelectionEnd(); if(a<0 || b<0) a=b=text.length();
        int from=Math.min(a,b),to=Math.max(a,b);
        for(InputFilter filter:field.getFilters()) if(filter instanceof InputFilter.LengthFilter && text.length()-(to-from)+token.length()>((InputFilter.LengthFilter)filter).getMax()) {
            Toast.makeText(field.getContext(),AppText.tr("Kein Platz für den Platzhalter"),Toast.LENGTH_SHORT).show(); return false;
        }
        text.replace(from,to,token); field.requestFocus(); field.setSelection(Math.min(text.length(),from+token.length())); return true;
    }
    private TokenButtons() {}
}
