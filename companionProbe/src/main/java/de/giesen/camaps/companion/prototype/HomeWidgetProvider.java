// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;

/** Display refresh only. Broadcasts can never toggle output or load a profile. */
public final class HomeWidgetProvider extends AppWidgetProvider {
    @Override public void onReceive(Context c,Intent i) {
        if(i==null)return;
        String a=i.getAction();
        if(HomeWidget.REFRESH.equals(a)||Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_LOCALE_CHANGED.equals(a)||Intent.ACTION_CONFIGURATION_CHANGED.equals(a)){
            PendingResult result=goAsync();HomeWidget.request(c,result==null?null:result::finish);return;
        }
        super.onReceive(c,i);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){PendingResult result=goAsync();HomeWidget.request(c,result==null?null:result::finish);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){HomeWidget.changed(c);}
    @Override public void onDeleted(Context c,int[] ids){SharedPreferences.Editor edit=c.getSharedPreferences(HomeWidget.PREFS,0).edit();for(int id:ids)edit.remove("controls_"+id);edit.apply();HomeWidget.changed(c);}
    @Override public void onDisabled(Context c){HomeWidget.changed(c);}
    @Override public void onRestored(Context c,int[] oldIds,int[] newIds){SharedPreferences p=c.getSharedPreferences(HomeWidget.PREFS,0);SharedPreferences.Editor edit=p.edit();for(int n=0;n<Math.min(oldIds.length,newIds.length);n++)edit.putBoolean("controls_"+newIds[n],p.getBoolean("controls_"+oldIds[n],true)).remove("controls_"+oldIds[n]);edit.apply();HomeWidget.changed(c);}
}
