// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import android.content.SharedPreferences;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Learned source identity is device state, never part of a settings profile. */
public final class UnitPreferences {
    static final String PREFS="glucose_source_unit_v1";
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS,0); }
    public static GlucoseUnit source(Context context) {
        String name=prefs(context).getString("source",null);
        if(name==null)return null;
        try { return GlucoseUnit.valueOf(name); } catch(IllegalArgumentException e) { return null; }
    }
    static synchronized boolean accept(Context context,GlucoseUnit unit) {
        if(unit==null)return false;
        GlucoseUnit locked=source(context);
        if(prefs(context).contains("source") && locked==null) { markConflict(context); return false; }
        if(locked!=null && locked!=unit) { prefs(context).edit().putBoolean("conflict",true).commit(); return false; }
        if(locked==null)return prefs(context).edit().putString("source",unit.name()).putBoolean("conflict",false).commit();
        return true;
    }
    static long epoch(Context context) { return prefs(context).getLong("epoch",0); }
    static void markConflict(Context context) { prefs(context).edit().putBoolean("conflict",true).commit(); }
    static synchronized boolean reset(Context context) {
        SharedPreferences p=prefs(context);long epoch=p.getLong("epoch",0);
        if(epoch==Long.MAX_VALUE)return false;
        return p.edit().remove("source").putBoolean("conflict",false).putLong("epoch",epoch+1).commit();
    }
    public static boolean conflict(Context context) { return prefs(context).getBoolean("conflict",false); }
    public static GlucoseUnit display(Context context,OutputPreferences p) { return (p==null?GlucoseUnit.Mode.AUTO:p.unitMode).resolve(source(context)); }
    public static GlucoseUnit display(Context context) {
        try { return display(context,new OutputPreferenceStore(context.getFilesDir()).load().preferences); }
        catch(IOException e) { return GlucoseUnit.Mode.AUTO.resolve(source(context)); }
    }
    public static String status(Context context) {
        GlucoseUnit source=source(context);
        return (source==null?AppText.tr("CamAPS-Einheit noch nicht erkannt"):"CamAPS: "+source.label)+(conflict(context)?AppText.tr(" · Einheitenkonflikt: neu erkennen"):"");
    }
    private UnitPreferences() {}
}
