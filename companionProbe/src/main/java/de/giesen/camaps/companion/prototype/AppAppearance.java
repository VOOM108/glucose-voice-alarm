// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.content.*;
import android.content.res.*;
import android.graphics.Color;
import android.view.View;

/** Presentation only; changing appearance never reloads or arms alarm delivery. */
final class AppAppearance {
    static final String PREFS="appearance_v1";
    enum Mode { SYSTEM,LIGHT,DARK }
    static Mode mode(Context context) {
        try { return Mode.valueOf(context.getSharedPreferences(PREFS,0).getString("mode","SYSTEM")); }
        catch (IllegalArgumentException | ClassCastException e) { return Mode.SYSTEM; }
    }
    static void set(Context context,Mode mode) { context.getSharedPreferences(PREFS,0).edit().putString("mode",mode.name()).commit(); }
    static boolean dark(Context context) {
        Mode choice=mode(context);
        return choice==Mode.DARK || choice==Mode.SYSTEM && (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    static final class Colors {
        final int background,surface,ink,muted,accent,onAccent,line,soft,error;
        Colors(boolean dark) {
            background=dark?0xff10191c:0xfff3f6f6; surface=dark?0xff19252a:Color.WHITE;
            ink=dark?0xffe9f1f2:0xff19363e; muted=dark?0xffa8bec4:0xff526b74;
            accent=dark?0xff86d5d1:0xff006b70; onAccent=dark?0xff093338:Color.WHITE;
            line=dark?0xff31454d:0xffdce5e8; soft=dark?0xff263a41:0xffe5eeee; error=dark?0xffffaaa8:0xffa52736;
        }
    }
    static Colors colors(Context context) { return new Colors(dark(context)); }
    static void apply(Activity activity) {
        boolean dark=dark(activity);
        activity.setTheme(dark?R.style.XCamDark:R.style.XCamLight);
        Colors c=colors(activity); activity.getWindow().setStatusBarColor(c.background); activity.getWindow().setNavigationBarColor(c.background);
        activity.getWindow().getDecorView().setBackgroundColor(c.background);
        activity.getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private AppAppearance() {}
}
