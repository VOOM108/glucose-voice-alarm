// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.*;
import java.util.*;
import java.io.IOException;

/** Configure one widget. Every preview uses fixed synthetic data and has no actions. */
public final class WidgetConfigureActivity extends UiActivity {
    private int widget,background,text;private long revision;private WidgetAppearance.Settings initial;
    private RadioGroup modes,tones,period;private SeekBar opacity,chartOpacity,horizontalPadding,verticalPadding;private CheckBox controls;private LinearLayout custom,textChoice;
    private Button backgroundButton,textButton;private TextView opacityLabel,chartOpacityLabel,horizontalPaddingLabel,verticalPaddingLabel,status;private FrameLayout preview;
    private final Handler handler=new Handler(Looper.getMainLooper());private final Runnable redraw=()->renderPreview();private boolean ready;
    @Override public void onCreate(Bundle saved){
        setResult(RESULT_CANCELED);super.onCreate(saved);if(!SafetyConsent.ready(this))return;
        widget=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);if(!HomeWidget.owns(this,widget)){finish();return;}
        WidgetAppearance.Snapshot snapshot=WidgetAppearance.load(this,widget);revision=snapshot.revision;initial=snapshot.settings;
        if(!WidgetAppearance.configured(this,widget) && !getSharedPreferences(HomeWidget.PREFS,0).contains("controls_"+widget))initial=WidgetAppearance.Settings.defaults(WidgetAppearance.Mode.SYSTEM);
        WidgetAppearance.Settings draft=initial;if(saved!=null&&saved.getInt("widget",-1)==widget){revision=saved.getLong("revision");initial=unpack(saved.getBundle("initial"));draft=unpack(saved.getBundle("draft"));}
        background=draft.background;text=draft.text;
        LinearLayout body=LevelUi.screen(this);LevelUi.back(body,AppText.tr("Zurück"),v->onBackPressed());LevelUi.text(body,AppText.tr("Widget gestalten"),28);
        LevelUi.text(body,AppText.tr("Vorschau · Beispieldaten"),18);
        preview=new Checkerboard(this);preview.setTag("widget_preview");body.addView(preview,new LinearLayout.LayoutParams(-1,LevelUi.dp(this,180)));
        preview.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)queue();});
        LevelUi.text(body,AppText.tr("Farbschema"),18);modes=new RadioGroup(this);body.addView(modes);modes.setTag("widget_modes");
        String[] names={AppText.tr("Android-Systemfarben"),AppText.tr("GVA-App-Theme"),AppText.tr("Eigene Farben")};
        for(WidgetAppearance.Mode mode:WidgetAppearance.Mode.values()){RadioButton b=new RadioButton(this);b.setId(9400+mode.ordinal());b.setText(names[mode.ordinal()]);modes.addView(b);}
        modes.check(9400+draft.mode.ordinal());
        custom=new LinearLayout(this);custom.setOrientation(LinearLayout.VERTICAL);body.addView(custom);
        backgroundButton=LevelUi.navigation(custom,"",v->WidgetColorDialog.show(this,AppText.tr("Hintergrundfarbe"),background,color->{background=color;labels();queue();}));backgroundButton.setTag("widget_background_color");
        textButton=LevelUi.navigation(custom,"",v->WidgetColorDialog.show(this,AppText.tr("Textfarbe"),text,color->{text=color;labels();queue();}));textButton.setTag("widget_text_color");
        opacityLabel=LevelUi.text(body,"",18);opacity=new SeekBar(this);opacity.setTag("widget_opacity");opacity.setContentDescription(AppText.tr("Hintergrund-Deckkraft"));opacity.setMax(100);opacity.setProgress(draft.opacity);body.addView(opacity);
        textChoice=new LinearLayout(this);textChoice.setOrientation(LinearLayout.VERTICAL);body.addView(textChoice);LevelUi.text(textChoice,AppText.tr("Schrift auf dem Hintergrund"),18);
        tones=new RadioGroup(this);textChoice.addView(tones);String[] toneNames={AppText.tr("Automatisch"),AppText.tr("Helle Schrift"),AppText.tr("Dunkle Schrift")};
        for(WidgetAppearance.TextTone tone:WidgetAppearance.TextTone.values()){RadioButton b=new RadioButton(this);b.setId(9410+tone.ordinal());b.setText(toneNames[tone.ordinal()]);tones.addView(b);}tones.check(9410+draft.textTone.ordinal());
        LevelUi.text(body,AppText.tr("Kurve"),22);
        LevelUi.text(body,AppText.tr("Zeitraum"),18);period=new RadioGroup(this);period.setTag("widget_history");period.setOrientation(RadioGroup.HORIZONTAL);body.addView(period);
        for(int hours=1;hours<=2;hours++){RadioButton b=new RadioButton(this);b.setId(9420+hours);b.setText(hours+" h");b.setContentDescription(AppText.tr(hours==1?"1 Stunde":"2 Stunden"));period.addView(b);}period.check(9420+draft.historyHours);
        chartOpacityLabel=LevelUi.text(body,"",18);chartOpacity=slider(body,"widget_chart_opacity","Deckkraft der Kurvengrafik",100,draft.chartOpacity);
        LevelUi.text(body,AppText.tr("Innenabstand"),22);
        horizontalPaddingLabel=LevelUi.text(body,"",18);horizontalPadding=slider(body,"widget_padding_horizontal","Innenabstand seitlich",24,draft.paddingHorizontal);
        verticalPaddingLabel=LevelUi.text(body,"",18);verticalPadding=slider(body,"widget_padding_vertical","Innenabstand oben/unten",24,draft.paddingVertical);
        controls=LevelUi.check(body,AppText.tr("Schalterzeile anzeigen"),draft.controls);controls.setId(R.id.widget_controls);
        LevelUi.help(body,AppText.tr("Zu Farben und Vorschau"),AppText.tr("Hintergrund und Kurvengrafik haben eine eigene Deckkraft. Die Kurvengrafik umfasst Linie, Farbflächen und Stundenpunkte. Ein Punkt bedeutet eine Stunde, zwei Punkte bedeuten zwei Stunden. Wert, Pfeil und Schalter bleiben davon unberührt. Tippe auf Wert und Pfeil, um CamAPS zu öffnen, oder auf die Kurvenfläche für GVA. Ist CamAPS nicht verfügbar, öffnet sich GVA. Bei kleinen Widgets wird der obere und untere Innenabstand begrenzt. Das Schachbrett zeigt transparente Bereiche; die Vorschau verwendet Beispieldaten. Android-Systemfarben folgen dem Gerät, das App-Theme folgt GVA. Vor Android 12 werden für Systemfarben die GVA-Farben im Systemmodus verwendet."));
        status=LevelUi.text(body,"",14);status.setTag("widget_editor_status");
        LevelUi.primary(body,AppText.tr("Speichern"),v->save());LevelUi.button(body,AppText.tr("Abbrechen"),v->onBackPressed());
        modes.setOnCheckedChangeListener((g,id)->{labels();queue();});tones.setOnCheckedChangeListener((g,id)->queue());period.setOnCheckedChangeListener((g,id)->queue());controls.setOnCheckedChangeListener((b,on)->queue());
        for(SeekBar slider:new SeekBar[]{opacity,chartOpacity,horizontalPadding,verticalPadding})slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){labels();queue();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        ready=true;labels();queue();
    }
    private SeekBar slider(LinearLayout body,String tag,String label,int max,int value){SeekBar bar=new SeekBar(this);bar.setTag(tag);bar.setContentDescription(AppText.tr(label));bar.setMax(max);bar.setProgress(value);body.addView(bar);return bar;}
    private WidgetAppearance.Settings draft(){return new WidgetAppearance.Settings(WidgetAppearance.Mode.values()[modes.getCheckedRadioButtonId()-9400],opacity.getProgress(),background,text,WidgetAppearance.TextTone.values()[tones.getCheckedRadioButtonId()-9410],controls.isChecked(),chartOpacity.getProgress(),period.getCheckedRadioButtonId()-9420,horizontalPadding.getProgress(),verticalPadding.getProgress());}
    private void labels(){if(opacity==null||tones==null)return;boolean own=modes.getCheckedRadioButtonId()==9402;custom.setVisibility(own?View.VISIBLE:View.GONE);textChoice.setVisibility(!own&&opacity.getProgress()<100?View.VISIBLE:View.GONE);
        opacityLabel.setText(AppText.tr("Hintergrund-Deckkraft")+": "+opacity.getProgress()+" %");
        chartOpacityLabel.setText(AppText.tr("Deckkraft der Kurvengrafik")+": "+chartOpacity.getProgress()+" %");
        horizontalPaddingLabel.setText(AppText.tr("Innenabstand seitlich")+": "+horizontalPadding.getProgress());verticalPaddingLabel.setText(AppText.tr("Innenabstand oben/unten")+": "+verticalPadding.getProgress());
        UiActions.title(backgroundButton,AppText.tr("Hintergrundfarbe")+" · "+WidgetColorDialog.format(background));UiActions.title(textButton,AppText.tr("Textfarbe")+" · "+WidgetColorDialog.format(text));
    }
    private void queue(){if(ready){handler.removeCallbacks(redraw);handler.postDelayed(redraw,100);}}
    private void renderPreview(){if(!ready||preview.getWidth()==0)return;float density=getResources().getDisplayMetrics().density;int width=Math.max(220,Math.min(600,Math.round(preview.getWidth()/density)));
        View view=HomeWidget.preview(this,width,180,sample(),draft()).apply(this,preview);inert(view);preview.removeAllViews();preview.addView(view,new FrameLayout.LayoutParams(-1,-1));
    }
    private static void inert(View view){view.setOnClickListener(null);view.setClickable(false);view.setFocusable(false);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)inert(((ViewGroup)view).getChildAt(n));}
    static HomeWidget.Model sample(){long now=System.currentTimeMillis();List<WatchSnapshot.Point> points=new ArrayList<>();for(int i=0;i<=120;i++){if(i>=54&&i<=59)continue;points.add(new WatchSnapshot.Point(now-(120-i)*60000L,123-90*Math.sin((i-120)/15.0)));}
        WatchSnapshot reading=new WatchSnapshot(now,now,123,WatchSnapshot.Trend.DOWN_RIGHT,false,true,-1.2,80.,180.,points,ArrowColor.RED);
        return new HomeWidget.Model(reading,true,false,false,false,true,new OutputPreferenceStore.Snapshot(0,OutputPreferences.defaults()));
    }
    private void save(){if(!ready)return;try{WidgetAppearance.save(this,widget,revision,draft());HomeWidget.changed(this);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widget));finish();}catch(IOException e){status.setText(e.getMessage());}}
    private static Bundle pack(WidgetAppearance.Settings s){Bundle b=new Bundle();b.putString("mode",s.mode.name());b.putInt("opacity",s.opacity);b.putInt("background",s.background);b.putInt("text",s.text);b.putString("tone",s.textTone.name());b.putBoolean("controls",s.controls);b.putInt("chartOpacity",s.chartOpacity);b.putInt("hours",s.historyHours);b.putInt("horizontal",s.paddingHorizontal);b.putInt("vertical",s.paddingVertical);return b;}
    private static WidgetAppearance.Settings unpack(Bundle b){return new WidgetAppearance.Settings(WidgetAppearance.Mode.valueOf(b.getString("mode")),b.getInt("opacity"),b.getInt("background"),b.getInt("text"),WidgetAppearance.TextTone.valueOf(b.getString("tone")),b.getBoolean("controls"),b.getInt("chartOpacity",100),b.getInt("hours",2),b.getInt("horizontal",8),b.getInt("vertical",8));}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(ready){b.putInt("widget",widget);b.putLong("revision",revision);b.putBundle("initial",pack(initial));b.putBundle("draft",pack(draft()));}}
    @Override public void onBackPressed(){if(ready&&!initial.equals(draft()))new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?")).setNegativeButton(AppText.tr("Weiter bearbeiten"),null).setPositiveButton(AppText.tr("Verwerfen"),(d,w)->finish()).show();else super.onBackPressed();}
    @Override public void onResume(){super.onResume();queue();}
    @Override public void onPause(){handler.removeCallbacks(redraw);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private static final class Checkerboard extends FrameLayout {
        private final Paint paint=new Paint();Checkerboard(android.content.Context c){super(c);setWillNotDraw(false);}
        @Override protected void onDraw(Canvas canvas){int cell=LevelUi.dp((android.app.Activity)getContext(),12);for(int y=0;y<getHeight();y+=cell)for(int x=0;x<getWidth();x+=cell){paint.setColor(((x/cell+y/cell)&1)==0?0xffdce1e4:0xffb8c4ca);canvas.drawRect(x,y,x+cell,y+cell,paint);}super.onDraw(canvas);}
    }
}
