// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import android.content.SharedPreferences;
/** Local seven-direction coverage, scoped to the current CamAPS package/version profile. */
final class ArrowCoverage {
    static final String PREFS="arrow_coverage_v1";
    static int group(NotificationParser.Trend trend) {
        switch(trend) {
            case UP_RIGHT: return 0; case UP: return 1; case FLAT: return 2;
            case DOWN_RIGHT: return 3; case DOWN: return 4; case DOUBLE_UP: return 5; case DOUBLE_DOWN: return 6; default: return -1;
        }
    }
    static void observe(Context context,String profile,NotificationParser.Trend trend) {
        SharedPreferences p=context.getSharedPreferences(PREFS,0); SharedPreferences.Editor edit=p.edit();
        int mask=profile.equals(p.getString("profile",""))?p.getInt("mask_seven",0):0;
        int group=group(trend); if (group>=0) mask|=1<<group;
        edit.putString("profile",profile).putInt("mask_seven",mask&127).apply();
    }
    static int count(Context context) { return Integer.bitCount(context.getSharedPreferences(PREFS,0).getInt("mask_seven",0)&127); }
    static String summary(Context context) {
        int mask=context.getSharedPreferences(PREFS,0).getInt("mask_seven",0)&127;
        String[] words={AppText.tr("↗ Steigend"),AppText.tr("↑ Schnell steigend"),AppText.tr("→ Stabil"),AppText.tr("↘ Fallend"),AppText.tr("↓ Schnell fallend"),AppText.tr("⇈ Doppelt aufwärts"),AppText.tr("⇊ Doppelt abwärts")};
        StringBuilder text=new StringBuilder(AppText.tr("In diesem CamAPS-Stand empfangen: ")+Integer.bitCount(mask)+"/7");
        for(int i=0;i<7;i++) text.append('\n').append(words[i]).append(AppText.tr((mask&(1<<i))!=0?" · erkannt":" · noch nicht wiedererkannt"));
        return text.toString();
    }
    private ArrowCoverage() {}
}
