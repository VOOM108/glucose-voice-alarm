// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.os.Bundle;
import android.widget.*;
public final class AppearanceActivity extends UiActivity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return; LinearLayout body=LevelUi.screen(this); LevelUi.button(body,AppText.tr("Zurück"),v -> finish());
        LevelUi.text(body,AppText.tr("Darstellung"),28); LevelUi.text(body,AppText.tr("Farbschema"),18);
        String[] labels={AppText.tr("Wie das System"),AppText.tr("Hell"),AppText.tr("Dunkel")}; RadioGroup modes=new RadioGroup(this); body.addView(modes);
        for (AppAppearance.Mode mode:AppAppearance.Mode.values()) {
            RadioButton button=new RadioButton(this); button.setId(200+mode.ordinal()); button.setText(labels[mode.ordinal()]); modes.addView(button);
        }
        modes.check(200+AppAppearance.mode(this).ordinal());
        modes.setOnCheckedChangeListener((group,id) -> { AppAppearance.set(this,AppAppearance.Mode.values()[id-200]); recreate(); });
        LevelUi.button(body,"de".equals(AppText.language())?"Sicherheit und Bedingungen":"Safety and terms",v -> startActivity(new android.content.Intent(this,SafetyActivity.class)));
        LevelUi.text(body,AppText.tr("Die native Sperrbildschirm-Anzeige gestaltet Android."),14);
        LevelUi.text(body,UnitPreferences.status(this),14);
        LevelUi.button(body,WatchPublisher.tr("Uhr","Watch"),v -> startActivity(new android.content.Intent(this,WatchSettingsActivity.class)));
        LevelUi.button(body,AppText.tr("Entwickleroptionen"),v -> startActivity(new android.content.Intent(this,DeveloperOptionsActivity.class)));
    }
}
