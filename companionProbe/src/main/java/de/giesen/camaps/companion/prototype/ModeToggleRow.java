// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Independent routes; saves are validated before the displayed state changes durably. */
final class ModeToggleRow extends LinearLayout {
    private final Activity activity;
    private final Switch[] buttons=new Switch[4];
    private boolean rendering;
    ModeToggleRow(Activity activity) {
        super(activity); this.activity=activity; setOrientation(VERTICAL);
        String[] labels={AppText.tr("Ton"),AppText.tr("Alarme sprechen"),AppText.tr("Werte sprechen"),AppText.tr("Vibration")};
        for (int i=0;i<4;i++) {
            final int index=i;
            Switch button=new Switch(activity); buttons[i]=button;
            button.setText(labels[i]); button.setTextSize(16); button.setMinWidth(0);
            button.setMinHeight(LevelUi.dp(activity,48));
            button.setContentDescription(labels[i]); button.setTextColor(DashboardUi.ink(activity)); addView(button,new LayoutParams(-1,-2));
            button.setOnCheckedChangeListener((view,checked) -> { if(!rendering) { OutputModeUi.channel(activity,index,checked,this::render); render(); } });
        }
        render();
    }
    void render() {
        try {
            OutputPreferences p=new OutputPreferenceStore(activity.getFilesDir()).load().preferences; rendering=true;
            boolean[] checked={p.tonesEnabled,p.alarmSpeechEnabled,p.valueSpeechEnabled,p.vibrationEnabled};
            for (int i=0;i<4;i++) { buttons[i].setChecked(checked[i]); buttons[i].setEnabled(true); }
        } catch (IOException e) { rendering=true; for (Switch button:buttons) { button.setChecked(false); button.setEnabled(false); } }
        finally { rendering=false; }
    }
}
