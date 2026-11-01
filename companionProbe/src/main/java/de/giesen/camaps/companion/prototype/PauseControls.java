// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.widget.*;
final class PauseControls {
    private final Activity activity;private final Button pause,resume;private final TextView status;
    PauseControls(Activity activity,LinearLayout parent) {
        this.activity=activity;
        status=LevelUi.text(parent,"",14);status.setTag("pause_status");
        pause=LevelUi.button(parent,AppText.tr("Ausgaben pausieren …"),v->ask());
        resume=LevelUi.button(parent,AppText.tr("Pause beenden"),v->{LivePreferences.pauseUntil(activity,0);LiveAlarmService.pauseChanged();render();});
        render();
    }
    void render(){long until=LivePreferences.pauseUntil(activity);boolean paused=until>System.currentTimeMillis();
        status.setText(paused?AppText.tr("Pausiert bis")+" "+new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(until)):"");
        status.setVisibility(paused?android.view.View.VISIBLE:android.view.View.GONE);
        pause.setVisibility(!paused && LiveAlarmService.live()?android.view.View.VISIBLE:android.view.View.GONE);
        resume.setVisibility(paused?android.view.View.VISIBLE:android.view.View.GONE);
    }
    private void ask(){EditText minutes=new EditText(activity);minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minutes.setHint(AppText.tr("Minuten"));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(AppText.tr("Alle eigenen Ausgaben pausieren"))
                .setView(minutes).setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Pausieren"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{long n=Long.parseLong(minutes.getText().toString().trim());if(n<1 || n>Integer.MAX_VALUE/60000)throw new NumberFormatException();
                LivePreferences.pauseUntil(activity,Math.addExact(System.currentTimeMillis(),n*60000));LiveAlarmService.pauseChanged();dialog.dismiss();render();
            }catch(ArithmeticException | NumberFormatException e){minutes.setError(AppText.tr("Bitte eine positive ganze Minutenzahl eingeben"));}
        }));dialog.show();
    }
}
