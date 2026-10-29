// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.*;
/** Short visible labels, full accessible action names and consistent touch targets. */
final class UiActions {
    static void style(Button button,String title) {
        AppAppearance.Colors c=AppAppearance.colors(button.getContext()); button.setAllCaps(false); button.setTextSize(15);
        button.setMinHeight((int)(48*button.getResources().getDisplayMetrics().density)); button.setContentDescription(title);
        String icon=null;
        if (title.startsWith(AppText.tr("Zurück"))) icon="back";
        else if (title.equals(AppText.tr("Vorschau stoppen")) || title.equals(AppText.tr("Anhören stoppen")) || title.equals(AppText.tr("Sprache stoppen"))) icon="stop";
        else if (title.equals(AppText.tr("Pfeilzuordnung prüfen"))) icon="edit";
        else if (title.equals(AppText.tr("Vibration testen")) || title.equals(AppText.tr("Trend anhören")) || title.equals(AppText.tr("Ton anhören")) || title.equals(AppText.tr("Tonlautstärke anhören")) || title.equals(AppText.tr("Sprachlautstärke anhören")) || title.equals(AppText.tr("Werttext mit Testdaten anhören")) || title.equals(AppText.tr("Sprachtext mit Testdaten anhören"))) icon="play";
        if (icon!=null) {
            button.setText(""); button.setCompoundDrawablesRelative(null,null,null,null);
            if(button instanceof ActionButton) ((ActionButton)button).symbol=new Symbol(icon,c.accent,button.getResources().getDisplayMetrics().density);
            button.setGravity(android.view.Gravity.CENTER); button.setPadding(0,0,0,0);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(button,52),dp(button,48)); p.setMargins(dp(button,4),dp(button,6),dp(button,4),dp(button,6)); button.setLayoutParams(p);
        } else button.setText(shortLabel(title));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(c.soft); shape.setCornerRadius(12*button.getResources().getDisplayMetrics().density);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(c.line),shape,null)); button.setTextColor(c.ink);
        if (icon==null) {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); int gap=(int)(4*button.getResources().getDisplayMetrics().density); p.setMargins(0,gap,0,gap); button.setLayoutParams(p);
            button.setPadding(dp(button,16),dp(button,8),dp(button,16),dp(button,8)); button.setMinWidth(0);
        }
        button.setTooltipText(title);
    }
    private static int dp(Button button,int value) { return Math.round(value*button.getResources().getDisplayMetrics().density); }
    static final class ActionButton extends Button {
        private Symbol symbol;
        ActionButton(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(symbol!=null) {
                int width=symbol.getIntrinsicWidth(),height=symbol.getIntrinsicHeight(); int left=(getWidth()-width)/2,top=(getHeight()-height)/2;
                symbol.setBounds(left,top,left+width,top+height); symbol.setAlpha(isEnabled()?255:80); symbol.draw(canvas);
            }
        }
    }
    private static String shortLabel(String s) {
        if(s.startsWith(AppText.tr("Stufe für „"))) return AppText.tr("Stufe hinzufügen");
        switch(AppText.source(s)) {
            case "Ton und Sprache": return AppText.tr("Ausgabe");
            case "Alarme und Stufen": return AppText.tr("Alarmstufen");
            case "Anzeige und Hintergrund": return AppText.tr("Hintergrund & AOD");
            case "Verlauf und Diagnose": return AppText.tr("Verlauf & Diagnose");
            case "App-Glockentöne anhören und auswählen": return AppText.tr("Glockentöne");
            case "Ausgabe-Einstellungen speichern": case "Stufe speichern": return AppText.tr("Speichern");
            case "Dieses Set als Standard verwenden": return AppText.tr("Set verwenden");
            case "Live-Alarme einschalten": return AppText.tr("Live-Alarme ein");
            case "Pfeilgrafik zuordnen …": return AppText.tr("Pfeil zuordnen");
            case "Benachrichtigungszugriff öffnen": return AppText.tr("Zugriff öffnen");
            case "Benachrichtigungszugriff erlauben": return AppText.tr("Zugriff erlauben");
            case "Eigene Benachrichtigungen erlauben": return AppText.tr("Benachrichtigungen");
            case "Akku-Ausnahme erteilen": return AppText.tr("Akku-Ausnahme");
            case "Hintergrundnutzung erlauben": return AppText.tr("Hintergrund erlauben");
            case "Technische Prüfungen und Pfeildiagnose": return AppText.tr("Technische Diagnose");
            default: return s;
        }
    }
    static void title(Button button,String title) { button.setText(shortLabel(title)); button.setContentDescription(title); }
    static final class Symbol extends Drawable {
        private final String name; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final int size;
        Symbol(String name,int color,float density) { this.name=name; size=Math.round(24*density); paint.setColor(color); paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        @Override public int getIntrinsicWidth() { return size; } @Override public int getIntrinsicHeight() { return size; }
        @Override public void draw(Canvas canvas) {
            canvas.save(); Rect b=getBounds(); canvas.translate(b.left,b.top); canvas.scale(b.width()/24f,b.height()/24f);
            Path p=new Path();
            if (name.equals("back")) { paint.setStyle(Paint.Style.STROKE); p.moveTo(14,5); p.lineTo(7,12); p.lineTo(14,19); p.moveTo(7,12); p.lineTo(21,12); }
            else if (name.equals("play")) { paint.setStyle(Paint.Style.FILL); p.moveTo(8,5); p.lineTo(19,12); p.lineTo(8,19); p.close(); }
            else if (name.equals("edit")) { paint.setStyle(Paint.Style.STROKE); p.moveTo(5,15); p.lineTo(15,5); p.lineTo(19,9); p.lineTo(9,19); p.lineTo(4,20); p.close(); p.moveTo(13,7); p.lineTo(17,11); }
            else { paint.setStyle(Paint.Style.FILL); p.addRoundRect(6,6,18,18,2,2,Path.Direction.CW); }
            canvas.drawPath(p,paint); canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); } @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); } @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    private UiActions() {}
}
