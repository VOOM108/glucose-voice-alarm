// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Display words have their own editor; saving never reloads or arms alarm output. */
public final class TrendDisplayActivity extends UiActivity {
    private final List<EditText> fields=new ArrayList<>();
    private ArrayList<String> initial;
    private TrendColorEditor colors;
    private long revision;
    private boolean ready;
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> onBackPressed());
        LevelUi.text(body,AppText.tr("Trendtexte"),28);
        LevelUi.text(body,AppText.tr("Für Dashboard und AOD. Sprachtexte änderst Du getrennt unter Ausgabe → Trendwörter."),14);
        LevelUi.text(body,AppText.tr("Leer = Standardtext. Kurz halten: Android kann lange AOD-Texte kürzen."),14);
        status=LevelUi.text(body,"",14);status.setTag("trend_display_status");
        try {
            DisplayTrendPreferences.Snapshot snapshot=DisplayTrendPreferences.load(this);
            revision=state==null?snapshot.revision:state.getLong("revision",snapshot.revision);
            colors=new TrendColorEditor(snapshot.words.colors,TrendDisplayWords.MAX_LENGTH,"trend_display");
            LevelUi.text(body,AppText.tr("Rot und Gelb übernehmen den normalen Text. Aktiviere je Richtung einen eigenen Farbtext, wenn Du ihn abweichend einstellen möchtest."),14);
            for(int i=0;i<TrendDisplayWords.COUNT;i++) {
                NotificationParser.Trend trend=TrendDisplayWords.TRENDS.get(i);
                LinearLayout card=DashboardUi.card(this,body);card.setTag("trend_display_card_"+trend.name());
                LevelUi.text(card,trend.symbol+" "+MetricNotification.trendTitle(trend),19);
                EditText field=LevelUi.field(card,AppText.tr("Normal (grau)"),snapshot.words.overrides.get(i),InputType.TYPE_CLASS_TEXT);
                field.setTag("trend_display_"+trend.name());field.setHint(MetricNotification.trendTitle(trend));field.setSingleLine(true);
                field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(TrendDisplayWords.MAX_LENGTH)});
                fields.add(field);
                colors.add(card,i,trend,field,()->field.getText().toString().trim().isEmpty()?MetricNotification.trendTitle(trend):field.getText().toString(),null,()->true,()->{});
            }
            initial=values();
            if(state!=null) {
                ArrayList<String> draft=state.getStringArrayList("fields"),original=state.getStringArrayList("initial");
                if(draft!=null && (draft.size()==TrendDisplayWords.COUNT || draft.size()==35)) {for(int i=0;i<fields.size();i++)fields.get(i).setText(draft.get(i));if(draft.size()==35)colors.restore(draft.subList(7,35));}
                if(original!=null && (original.size()==TrendDisplayWords.COUNT || original.size()==35)){initial=original;if(initial.size()==7)initial.addAll(TrendColorEditor.defaultValues());}
            }
            LevelUi.button(body,AppText.tr("Standardtexte"),v -> {
                for(EditText field:fields)field.setText("");colors.reset();
            });
            Button save=LevelUi.button(body,AppText.tr("Speichern"),v -> save());save.setTag("trend_display_save");
            ready=true;
            if(revision!=snapshot.revision) {save.setEnabled(false);status.setText(AppText.tr("Anzeigetexte wurden inzwischen geändert. Bitte neu öffnen."));}
        } catch(IOException e) {status.setText(e.getMessage());}
    }
    private ArrayList<String> values() {
        ArrayList<String> result=new ArrayList<>();for(EditText field:fields)result.add(field.getText().toString());result.addAll(colors.values());return result;
    }
    private void save() {
        if(!ready)return;
        try {
            DisplayTrendPreferences.save(this,revision,new TrendDisplayWords(values().subList(0,7),colors.words()));
            LiveAlarmService.displayChanged();HomeWidget.changed(this);finish();
        } catch(IOException | IllegalArgumentException e) {status.setText(e.getMessage());}
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);if(!ready)return;
        state.putLong("revision",revision);state.putStringArrayList("fields",values());state.putStringArrayList("initial",initial);
    }
    @Override public void onBackPressed() {
        if(ready && !initial.equals(values()))new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?"))
                .setMessage(AppText.tr("Die Anzeigetexte sind noch nicht gespeichert.")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null)
                .setPositiveButton(AppText.tr("Verwerfen"),(d,w) -> finish()).show();
        else super.onBackPressed();
    }
}
