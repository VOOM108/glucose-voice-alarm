// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import java.io.IOException;
import java.util.*;

/** One discoverable destination; display-only saves never touch the output store or alarm flow. */
public final class TrendNamesActivity extends UiActivity {
    private final List<EditText> display=new ArrayList<>(Collections.nCopies(7,null)),speech=new ArrayList<>(Collections.nCopies(7,null));
    private final List<CheckBox> spoken=new ArrayList<>(Collections.nCopies(7,null));
    private final List<LinearLayout> displayCards=new ArrayList<>(Collections.nCopies(7,null)),speechCards=new ArrayList<>(Collections.nCopies(7,null));
    private final List<Button> directions=new ArrayList<>();
    private TrendColorEditor displayColors,speechColors;
    private DisplayTrendPreferences.Snapshot displayBase;private OutputPreferenceStore.Snapshot outputBase;
    private ArrayList<String> initialDisplay,initialSpeech;
    private TextView status,preview;private Button save;private PreviewAudio audio;
    private int direction=0;private boolean speechMode,ready;
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!SafetyConsent.ready(this))return;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->onBackPressed());
        LevelUi.text(body,AppText.tr("Trendbezeichnungen"),28);
        RadioGroup mode=new RadioGroup(this);mode.setOrientation(getResources().getConfiguration().fontScale>1.3?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);body.addView(mode);
        RadioButton d=new RadioButton(this);d.setId(9101);d.setText(AppText.tr("Displaytext"));d.setContentDescription(AppText.tr("Displaytext"));mode.addView(d);
        RadioButton s=new RadioButton(this);s.setId(9102);s.setText(AppText.tr("Sprachtext"));s.setContentDescription(AppText.tr("Sprachtext"));mode.addView(s);
        LinearLayout row=null;
        for(int i=0;i<TrendOrder.ALL.size();i++){
            if(i==0 || i==4){row=new LinearLayout(this);body.addView(row);}
            final int selected=i;NotificationParser.Trend trend=TrendOrder.ALL.get(i);
            Button choice=LevelUi.button(row,trend.symbol,v->{if(audio!=null)audio.stop();direction=selected;show();});
            choice.setTag("trend_direction_"+trend.name());choice.setContentDescription(trend.symbol+" "+MetricNotification.trendTitle(trend));
            choice.setTextSize(22);choice.setMinHeight(LevelUi.dp(this,52));choice.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));directions.add(choice);
        }
        status=LevelUi.text(body,"",14);status.setTag("trend_names_status");
        audio=new PreviewAudio(this,text->status.setText(text));
        try{
            displayBase=DisplayTrendPreferences.load(this);outputBase=new OutputPreferenceStore(getFilesDir()).load();
            displayColors=new TrendColorEditor(displayBase.words.colors,40,"trend_display");speechColors=new TrendColorEditor(outputBase.preferences.trendSpeech.colors,80,"trend_speech_color");
            for(NotificationParser.Trend trend:TrendOrder.ALL){
                int index=TrendOrder.storedIndex(trend);
                LinearLayout dc=DashboardUi.card(this,body);dc.setTag("trend_display_card_"+trend.name());displayCards.set(index,dc);
                LevelUi.text(dc,trend.symbol+" · "+AppText.tr("Displaytext"),21);
                EditText df=LevelUi.field(dc,AppText.tr("Normal (grau)"),displayBase.words.overrides.get(index),InputType.TYPE_CLASS_TEXT);df.setTag("trend_display_"+trend.name());df.setHint(MetricNotification.trendTitle(trend));df.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});display.set(index,df);
                displayColors.add(dc,index,trend,df,()->df.getText().toString().trim().isEmpty()?MetricNotification.trendTitle(trend):df.getText().toString(),null,()->true,()->{});
                LevelUi.text(dc,AppText.tr("Leer übernimmt den Standard. Rot und Gelb übernehmen den normalen Text, bis Du einen eigenen aktivierst."),14);
                LinearLayout sc=DashboardUi.card(this,body);sc.setTag("trend_speech_card_"+trend.name());speechCards.set(index,sc);
                LevelUi.text(sc,trend.symbol+" · "+AppText.tr("Sprachtext"),21);
                CheckBox enabled=LevelUi.check(sc,AppText.tr("Diese Richtung ansagen"),outputBase.preferences.trendSpeech.enabled.get(index));enabled.setTag("trend_speech_"+index);spoken.set(index,enabled);
                EditText sf=LevelUi.field(sc,AppText.tr("Normal (grau)"),outputBase.preferences.trendSpeech.words.get(index),InputType.TYPE_CLASS_TEXT);speech.set(index,sf);
                sf.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});TokenButtons.add(sc,sf,"{wert}","{einheit}");sf.setTag("trend_speech_word_"+trend.name());
                speechColors.add(sc,index,trend,sf,()->sf.getText().toString(),null,()->enabled.isChecked(),()->{audio.stop();updatePreview();});
                // Optional color fields use the same bounded token insertion controls.
                for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW}){
                    EditText cf=sc.findViewWithTag("trend_speech_color_"+color.name()+"_"+trend.name());
                    LinearLayout tokens=new LinearLayout(this);tokens.setOrientation(LinearLayout.VERTICAL);sc.addView(tokens,sc.indexOfChild((View)cf.getParent())+1);
                    TokenButtons.add(tokens,cf,"{wert}","{einheit}");cf.setTag("trend_speech_color_"+color.name()+"_"+trend.name());
                    CheckBox flag=sc.findViewWithTag("trend_speech_color_"+color.name()+"_"+trend.name()+"_enabled");
                    Runnable visibility=()->tokens.setVisibility(flag.isChecked()?View.VISIBLE:View.GONE);visibility.run();
                    flag.setOnCheckedChangeListener((v,on)->{speechColors.refresh(index);if(on && cf.getText().toString().trim().isEmpty())cf.setText(sf.getText().toString());visibility.run();audio.stop();updatePreview();});
                }
                enabled.setOnCheckedChangeListener((v,on)->{audio.stop();updatePreview();});
                sf.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){updatePreview();}public void afterTextChanged(Editable e){}});
                LevelUi.help(sc,AppText.tr("Wertposition und Farben"),AppText.tr("Ohne {wert} steht der Wert bei Wertansagen am Anfang. Mit {wert} im Trendtext wird er dorthin verschoben. {einheit} setzt die Ausgabeeinheit ein. Die optionale Farbe kommt weiterhin aus {farbe} im Ansagetext. Die Richtungsauswahl gilt für alle Farben."));
            }
            initialDisplay=displayValues();initialSpeech=speechValues();
            if(state!=null){direction=Math.max(0,Math.min(6,state.getInt("direction",0)));speechMode=state.getBoolean("speech_mode");
                restoreDisplay(state.getStringArrayList("display"));restoreSpeech(state.getStringArrayList("speech"));
                ArrayList<String> id=state.getStringArrayList("initial_display"),is=state.getStringArrayList("initial_speech");if(id!=null)initialDisplay=id;if(is!=null)initialSpeech=is;
                displayBase=new DisplayTrendPreferences.Snapshot(state.getLong("display_revision",displayBase.revision),displayBase.words);
                outputBase=new OutputPreferenceStore.Snapshot(state.getLong("output_revision",outputBase.revision),outputBase.preferences);
            }
            preview=LevelUi.text(body,"",16);preview.setTag("trend_full_preview");
            LinearLayout samples=new LinearLayout(this);samples.setOrientation(LinearLayout.VERTICAL);body.addView(samples);samples.setTag("trend_samples");
            for(ArrowColor color:ArrowColor.values()){
                String title=AppText.tr(color==ArrowColor.NORMAL?"Normal (grau)":color==ArrowColor.RED?"Rot":"Gelb");
                LinearLayout sampleRow=new LinearLayout(this);samples.addView(sampleRow);
                TextView label=LevelUi.text(sampleRow,title,16);label.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                if(color.colored())label.setTextColor(color==ArrowColor.RED?AppAppearance.colors(this).error:AppAppearance.dark(this)?0xffffd28c:0xff936000);
                Button play=LevelUi.play(sampleRow,title+" · "+AppText.tr("Anhören"),v->speak(color));play.setTag("trend_preview_"+color.name());
                LevelUi.stop(sampleRow,AppText.tr("Vorschau stoppen"),v->audio.stop());
            }
            save=LevelUi.primary(body,AppText.tr("Speichern"),v->save());save.setTag("trend_names_save");
            LevelUi.button(body,AppText.tr("Standardtexte"),v->new AlertDialog.Builder(this).setTitle(AppText.tr("Standardtexte einsetzen?"))
                    .setMessage(AppText.tr(speechMode?"Alle sieben Sprachtexte werden zurückgesetzt, eigene Farbtexte ausgeschaltet und geleert. Die Ansageschalter bleiben erhalten.":"Alle sieben Displaytexte und ihre Farbtexte werden zurückgesetzt."))
                    .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Ersetzen"),(dialog,w)->reset()).show());
            mode.check(speechMode?9102:9101);mode.setOnCheckedChangeListener((g,id)->{audio.stop();speechMode=id==9102;if(speechMode)audio.previewLanguage(outputBase.preferences.speechLanguage);show();});ready=true;if(speechMode)audio.previewLanguage(outputBase.preferences.speechLanguage);show();
        }catch(IOException | IllegalArgumentException e){status.setText(AppText.tr("Trendbezeichnungen nicht lesbar. Gespeicherte Texte bleiben erhalten."));}
    }
    private ArrayList<String> displayValues(){ArrayList<String> v=new ArrayList<>();for(EditText f:display)v.add(f.getText().toString());v.addAll(displayColors.values());return v;}
    private ArrayList<String> speechValues(){ArrayList<String> v=new ArrayList<>();for(EditText f:speech)v.add(f.getText().toString());for(CheckBox c:spoken)v.add(Boolean.toString(c.isChecked()));v.addAll(speechColors.values());return v;}
    private void restoreDisplay(List<String> v){if(v==null || v.size()!=35)return;for(int i=0;i<7;i++)display.get(i).setText(v.get(i));displayColors.restore(v.subList(7,35));}
    private void restoreSpeech(List<String> v){if(v==null || v.size()!=42)return;for(int i=0;i<7;i++){speech.get(i).setText(v.get(i));spoken.get(i).setChecked(Boolean.parseBoolean(v.get(7+i)));}speechColors.restore(v.subList(14,42));}
    private TrendSpeech words(){List<String> w=new ArrayList<>();List<Boolean> e=new ArrayList<>();for(EditText f:speech)w.add(f.getText().toString());for(CheckBox c:spoken)e.add(c.isChecked());return new TrendSpeech(w,e,speechColors.words());}
    private String sample(ArrowColor color){return SpeechTemplate.render(outputBase.preferences.valueTemplate,null,new SpeechTemplate.Values(123,TrendOrder.ALL.get(direction),0,0,words(),outputBase.preferences.speechLanguage,UnitPreferences.display(this),color),true,true);}
    private void speak(ArrowColor color){try{audio.previewLanguage(outputBase.preferences.speechLanguage);audio.speak(sample(color));preview.setText(sample(color));}catch(IllegalArgumentException e){status.setText(AppText.tr(e.getMessage()));}}
    private void updatePreview(){if(!ready || preview==null)return;if(speechMode)try{preview.setText(sample(ArrowColor.NORMAL));}catch(IllegalArgumentException e){preview.setText(AppText.tr(e.getMessage()));}}
    private void show(){if(!ready)return;int chosen=TrendOrder.storedIndex(TrendOrder.ALL.get(direction));
        for(int i=0;i<7;i++){displayCards.get(i).setVisibility(!speechMode && i==chosen?View.VISIBLE:View.GONE);speechCards.get(i).setVisibility(speechMode && i==chosen?View.VISIBLE:View.GONE);
            Button button=directions.get(i);button.setSelected(i==direction);button.setTextColor(i==direction?AppAppearance.colors(this).onAccent:AppAppearance.colors(this).ink);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(i==direction?AppAppearance.colors(this).accent:AppAppearance.colors(this).soft));}
        preview.setVisibility(speechMode?View.VISIBLE:View.GONE);((View)save.getParent()).findViewWithTag("trend_samples").setVisibility(speechMode?View.VISIBLE:View.GONE);
        UiActions.title(save,AppText.tr(speechMode?"Sprachtexte speichern":"Displaytexte speichern"));updatePreview();
    }
    private void save(){if(!ready)return;try{if(speechMode){OutputPreferenceStore store=new OutputPreferenceStore(getFilesDir());
                outputBase=store.save(outputBase.revision,outputBase.preferences.withTrendSpeech(words()));initialSpeech=speechValues();PreviewAudio.configurationChanged();LiveAlarmService.settingsChanged();
            }else{DisplayTrendPreferences.save(this,displayBase.revision,new TrendDisplayWords(displayValues().subList(0,7),displayColors.words()));displayBase=DisplayTrendPreferences.load(this);initialDisplay=displayValues();LiveAlarmService.displayChanged();HomeWidget.changed(this);}
            status.setText(AppText.tr("Gespeichert"));
        }catch(IOException | IllegalArgumentException e){status.setText(AppText.tr(e.getMessage()));}}
    private void reset(){if(speechMode){TrendSpeech defaults=TrendSpeech.defaults(outputBase.preferences.speechLanguage);for(int i=0;i<7;i++){speech.get(i).setText(defaults.words.get(i));}speechColors.reset();}
        else{for(EditText f:display)f.setText("");displayColors.reset();}audio.stop();updatePreview();}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(!ready)return;state.putInt("direction",direction);state.putBoolean("speech_mode",speechMode);state.putLong("display_revision",displayBase.revision);state.putLong("output_revision",outputBase.revision);state.putStringArrayList("display",displayValues());state.putStringArrayList("speech",speechValues());state.putStringArrayList("initial_display",initialDisplay);state.putStringArrayList("initial_speech",initialSpeech);}
    @Override public void onBackPressed(){if(audio!=null)audio.stop();if(ready && (!initialDisplay.equals(displayValues()) || !initialSpeech.equals(speechValues())))new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null).setPositiveButton(AppText.tr("Verwerfen"),(d,w)->finish()).show();else super.onBackPressed();}
    @Override public void onPause(){if(audio!=null)audio.pause();super.onPause();}
    @Override public void onDestroy(){if(audio!=null)audio.close();super.onDestroy();}
}
