// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.os.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

public final class ToneSetsActivity extends UiActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AlarmEngine.Kind[] order={AlarmEngine.Kind.HIGH,AlarmEngine.Kind.PERSISTENT_HIGH,AlarmEngine.Kind.LOW,AlarmEngine.Kind.FALLING,AlarmEngine.Kind.RISING};
    private PreviewAudio audio;
    private TextView status,selected;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return; LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish()); LevelUi.text(body,AppText.tr("Kurze Glockentöne"),27);
        selected=LevelUi.text(body,AppText.tr("Standard: ")+ToneCatalog.setName(SoundDefaults.preferredSet(this)),18);
        LevelUi.text(body,AppText.tr("Hoch: hoher Einzelton. Dauerhoch: derselbe Ton länger. Niedrig: tiefer Einzelton. Schnell sinkend: hoch–tief. Schnell steigend: tief–hoch."),16);
        status=LevelUi.text(body,"",15); audio=new PreviewAudio(this,message -> status.setText(message));
        String[] descriptions={AppText.tr("Weich und warm"),AppText.tr("Heller und etwas prägnanter"),AppText.tr("Tiefer und rund")}; int index=0;
        for (String set:ToneCatalog.SETS) {
            LinearLayout card=DashboardUi.card(this,body); LevelUi.text(card,ToneCatalog.setName(set),23); LevelUi.text(card,descriptions[index++],15);
            LevelUi.button(card,AppText.tr("Set anhören"),v -> { stop(); play(set,0); });
            for (AlarmEngine.Kind kind:order) LevelUi.button(card,LevelForm.kindLabel(kind)+AppText.tr(" anhören"),v -> { stop(); audio.tone(ToneCatalog.uri(set,kind)); });
            LevelUi.button(card,AppText.tr("Dieses Set als Standard verwenden"),v -> {
                stop();
                try { SoundDefaults.useSet(this,set); selected.setText(AppText.tr("Standard: ")+ToneCatalog.setName(set)); status.setText(AppText.tr("App-Töne aktualisiert. Eigene Android-Töne bleiben erhalten.")); }
                catch (IOException e) { status.setText(AppText.tr("Klangset konnte nicht vollständig übernommen werden. Bitte erneut öffnen.")); }
            });
        }
        LevelUi.button(body,AppText.tr("Anhören stoppen"),v -> stop());
        LevelUi.text(body,AppText.tr("Die Töne sind direkt in der App enthalten. Die eigene Android-Tonauswahl bleibt je Alarmstufe verfügbar. Ein Klangset schaltet keine Alarmstufe und keinen abgewählten Ton ein."),14);
    }
    private void play(String set,int index) {
        if (index>=order.length) return;
        String uri=ToneCatalog.uri(set,order[index]);
        if (!audio.toneThen(uri,() -> handler.postDelayed(() -> play(set,index+1),200))) return;
        status.setText(ToneCatalog.title(uri));
    }
    private void stop() { handler.removeCallbacksAndMessages(null); if (audio!=null) audio.stop(); }
    @Override public void onPause() { stop(); if (audio!=null) audio.pause(); super.onPause(); }
    @Override public void onDestroy() { stop(); if (audio!=null) audio.close(); super.onDestroy(); }
}
