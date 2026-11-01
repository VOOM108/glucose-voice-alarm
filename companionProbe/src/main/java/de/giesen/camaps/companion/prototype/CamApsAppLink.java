// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.*;
import java.util.*;

/** Ordinary app launch only: no deep links, commands, values, notification reads or icon copying. */
final class CamApsAppLink {
    private static final String[] PACKAGES={"com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.hx.mgdl","com.camdiab.fx_alert.mmoll","com.camdiab.fx_alert.hx.mmoll","com.camdiab.fx_alert.mmoll.ca"};
    static Intent intent(Context c){
        List<Intent> found=new ArrayList<>();
        for(String name:PACKAGES)try{
            Intent launch=c.getPackageManager().getLaunchIntentForPackage(name);
            if(launch!=null && launch.getComponent()!=null && name.equals(launch.getComponent().getPackageName()))
                found.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(launch.getComponent()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }catch(RuntimeException e){/* A removed or unavailable app is not a launch target. */}
        if(found.isEmpty())return null;
        if(found.size()==1)return found.get(0);
        Intent chooser=Intent.createChooser(found.get(0),AppText.tr("CamAPS öffnen"));
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,found.subList(1,found.size()).toArray(new Intent[0]));
        return chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private CamApsAppLink(){}
}
