// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.os.Bundle;
public class UiActivity extends Activity {
    private AppAppearance.Mode applied;
    @Override public void onCreate(Bundle saved) {
        AppText.configure(getResources().getConfiguration().getLocales().get(0));
        if(SafetyConsent.ready(this)) try {ProfileController.recover(this);} catch(java.io.IOException e) {LivePreferences.set(this,LivePreferences.monitor(this),false);}
        applied=AppAppearance.mode(this);AppAppearance.apply(this);super.onCreate(saved);
        SafetyConsent.require(this);
    }
    @Override public void onResume() {super.onResume();if(!SafetyConsent.ready(this)){if(!isFinishing())SafetyConsent.require(this);return;}if(applied!=AppAppearance.mode(this))recreate();}
}
