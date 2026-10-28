// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import android.content.Context;
/** Watch-local explicit acknowledgments; neither transport nor profiles can grant them. */
final class WatchConsent {
    static final int REVISION=2;
    private static boolean accepted(Context c,String key){try{return c.getSharedPreferences("watch_safety_v1",0).getInt(key+"_revision",0)==REVISION;}catch(ClassCastException e){return false;}}
    static boolean ready(Context c){return accepted(c,"warning")&&accepted(c,"terms");}
    static int step(Context c){return accepted(c,"warning")?2:1;}
    static void accept(Context c,int step){if(step!=1&&step!=2)return;if(step==2&&!accepted(c,"warning"))return;c.getSharedPreferences("watch_safety_v1",0).edit().putInt((step==1?"warning":"terms")+"_revision",REVISION).commit();}
    static boolean de(){return "de".equals(java.util.Locale.getDefault().getLanguage());}
    static String text(String de,String en){return de()?de:en;}
}
