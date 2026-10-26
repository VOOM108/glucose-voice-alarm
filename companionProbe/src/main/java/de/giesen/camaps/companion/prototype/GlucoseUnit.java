// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Canonical mg/dl internally; presentation rounding never feeds alarm calculations. */
public enum GlucoseUnit {
    MGDL("mg/dl"), MMOL("mmol/l");
    // Same factor as pinned xDrip utilitymodels.Constants (805ba044).
    public static final double MMOL_TO_MGDL=18.0182;
    public final String label;
    GlucoseUnit(String label) { this.label=label; }
    public enum Mode {
        AUTO,MGDL,MMOL;
        public GlucoseUnit resolve(GlucoseUnit detected) { return this==MMOL?GlucoseUnit.MMOL:this==MGDL?GlucoseUnit.MGDL:detected==null?GlucoseUnit.MGDL:detected; }
        public String title() { return this==AUTO?AppText.tr("Automatisch (CamAPS)"):this==MMOL?GlucoseUnit.MMOL.label:GlucoseUnit.MGDL.label; }
    }
    public double canonical(double input) { return this==MMOL?input*MMOL_TO_MGDL:input; }
    public double fromCanonical(double input) { return this==MMOL?input/MMOL_TO_MGDL:input; }
    public String number(double mgdl,Locale locale) { return format(fromCanonical(mgdl),this==MMOL?1:0,locale); }
    public String rate(double mgdlPerMinute,Locale locale) {
        String number=format(Math.abs(fromCanonical(mgdlPerMinute)),this==MMOL?2:1,locale);
        return (mgdlPerMinute<0?"-":"+")+number+" "+label+"/min";
    }
    public String threshold(double mgdl,boolean rate) {
        return threshold(mgdl,rate,Locale.getDefault());
    }
    public String threshold(double mgdl,boolean rate,Locale locale) {
        return format(fromCanonical(mgdl),this==MMOL?(rate?3:1):(rate?3:0),locale).replaceAll("([.,][0-9]*?)0+$","$1").replaceAll("[.,]$","");
    }
    public double parse(String text) {
        if(text==null || !text.trim().matches("[0-9]+(?:[.,][0-9]+)?")) throw new IllegalArgumentException(AppText.tr("Bitte eine positive Zahl eingeben (Komma oder Punkt möglich)"));
        double value=new BigDecimal(text.trim().replace(',','.')).doubleValue(),converted=canonical(value);
        if(!Double.isFinite(converted) || converted<=0 || converted>Integer.MAX_VALUE) throw new IllegalArgumentException(AppText.tr("Grenzwert ist nicht als positive Zahl speicherbar"));
        return converted;
    }
    public String spokenUnit(String language) { return this==MMOL?("en".equals(language)?"millimoles per liter":"Millimol pro Liter"):("en".equals(language)?"milligrams per deciliter":"Milligramm pro Deziliter"); }
    public static String format(double value,int decimals,Locale locale) {
        if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite glucose presentation");
        String text=BigDecimal.valueOf(value==0?0:value).setScale(decimals,RoundingMode.HALF_UP).toPlainString();
        return "de".equals(locale.getLanguage())?text.replace('.',','):text;
    }
}
