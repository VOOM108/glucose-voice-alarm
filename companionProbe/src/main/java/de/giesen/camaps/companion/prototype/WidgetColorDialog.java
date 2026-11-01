// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.*;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Local color chooser with visible samples; changes apply to the editor draft only. */
final class WidgetColorDialog {
    static void show(Activity a,String title,int initial,IntConsumer accept){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setTag("widget_color_picker");int gap=LevelUi.dp(a,16);body.setPadding(gap,gap,gap,gap);
        TextView sample=LevelUi.text(body,AppText.tr("Farbvorschau"),18);sample.setGravity(Gravity.CENTER);sample.setMinHeight(LevelUi.dp(a,48));
        EditText hex=LevelUi.field(body,AppText.tr("Farbcode (#RRGGBB)"),format(initial),android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        SeekBar[] sliders=new SeekBar[3];TextView[] labels=new TextView[3];boolean[] updating={false};String[] names={AppText.tr("Rot"),AppText.tr("Grün"),AppText.tr("Blau")};int[] channels={Color.red(initial),Color.green(initial),Color.blue(initial)};
        Runnable paint=()->{int color=Color.rgb(sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress());sample.setBackgroundColor(color);sample.setTextColor((Color.red(color)*.2126+Color.green(color)*.7152+Color.blue(color)*.0722)>125?Color.BLACK:Color.WHITE);for(int i=0;i<3;i++)labels[i].setText(names[i]+": "+sliders[i].getProgress());};
        for(int i=0;i<3;i++){labels[i]=LevelUi.text(body,names[i],16);sliders[i]=new SeekBar(a);sliders[i].setMax(255);sliders[i].setProgress(channels[i]);sliders[i].setContentDescription(names[i]);body.addView(sliders[i]);}
        Runnable changed=()->{paint.run();if(!updating[0]){updating[0]=true;hex.setText(format(Color.rgb(sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress())));updating[0]=false;}};
        for(SeekBar slider:sliders)slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){changed.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(updating[0])return;try{int color=parse(s.toString());updating[0]=true;sliders[0].setProgress(Color.red(color));sliders[1].setProgress(Color.green(color));sliders[2].setProgress(Color.blue(color));updating[0]=false;paint.run();hex.setError(null);}catch(IllegalArgumentException e){/* A partial draft is validated on Apply. */}}public void afterTextChanged(Editable e){}});
        LinearLayout swatches=new LinearLayout(a);body.addView(swatches);for(int color:new int[]{0xff19252a,0xff000000,0xffffffff,0xffeee6d8}){
            Button b=new Button(a);b.setText("●");b.setTextColor(color);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,24);b.setContentDescription(format(color));swatches.addView(b,new LinearLayout.LayoutParams(0,LevelUi.dp(a,48),1));b.setOnClickListener(v->hex.setText(format(color)));
        }
        ScrollView scroll=new ScrollView(a);scroll.addView(body);paint.run();AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Übernehmen"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int color=parse(hex.getText().toString());accept.accept(color);dialog.dismiss();}catch(IllegalArgumentException e){hex.setError(AppText.tr("Bitte sechs hexadezimale Stellen eingeben, zum Beispiel #19252A."));}}));dialog.show();
    }
    static String format(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    static int parse(String text){if(!text.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color");return 0xff000000|Integer.parseInt(text.substring(1),16);}
    private WidgetColorDialog(){}
}
