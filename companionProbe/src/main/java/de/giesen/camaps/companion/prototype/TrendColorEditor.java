// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.text.*;
import android.view.View;
import android.widget.*;
import de.giesen.gva.watch.ArrowColor;
import java.util.*;
import java.util.function.*;

/** Shared colour controls for the separate display and speech editors. */
final class TrendColorEditor {
    private final TrendColorOverrides initial;private final int maximum;private final String prefix;
    private final List<EditText> red=new ArrayList<>(),yellow=new ArrayList<>();
    private final List<CheckBox> redEnabled=new ArrayList<>(),yellowEnabled=new ArrayList<>();
    private final List<Runnable> refreshers=new ArrayList<>();
    TrendColorEditor(TrendColorOverrides initial,int maximum,String prefix){this.initial=initial;this.maximum=maximum;this.prefix=prefix;}
    void add(LinearLayout parent,int index,NotificationParser.Trend trend,EditText normal,Supplier<String> inherited,Consumer<String> preview,BooleanSupplier previewAllowed,Runnable changed){
        List<Runnable> refresh=new ArrayList<>();
        for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW}){
            boolean isRed=color==ArrowColor.RED;List<EditText> fields=isRed?red:yellow;List<CheckBox> flags=isRed?redEnabled:yellowEnabled;
            String name=AppText.tr(isRed?"Rot":"Gelb"),label=trend.symbol+" "+MetricNotification.trendTitle(trend)+" · "+name;
            CheckBox box=LevelUi.check(parent,AppText.tr(isRed?"Eigener Text für Rot":"Eigener Text für Gelb"),(isRed?initial.redEnabled:initial.yellowEnabled).get(index));
            box.setTag(prefix+"_"+color.name()+"_"+trend.name()+"_enabled");box.setContentDescription(label+" · "+AppText.tr("eigenen Text verwenden"));box.setMinHeight(Math.round(48*parent.getResources().getDisplayMetrics().density));flags.add(box);
            LinearLayout row=new LinearLayout(parent.getContext());row.setGravity(android.view.Gravity.CENTER_VERTICAL);parent.addView(row);
            EditText field=new EditText(parent.getContext());field.setInputType(InputType.TYPE_CLASS_TEXT);field.setSingleLine(true);field.setText((isRed?initial.red:initial.yellow).get(index));
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maximum)});field.setTag(prefix+"_"+color.name()+"_"+trend.name());field.setContentDescription(label);
            row.addView(field,new LinearLayout.LayoutParams(0,-2,1));fields.add(field);
            Button sample=preview==null?null:LevelUi.button(row,AppText.tr("Trend anhören"),v->preview.accept(field.getText().toString().trim().isEmpty()?inherited.get():field.getText().toString()));
            if(sample!=null)sample.setContentDescription(label+AppText.tr(" anhören"));
            Runnable update=()->{row.setVisibility(box.isChecked()?View.VISIBLE:View.GONE);field.setHint(inherited.get());if(sample!=null)sample.setEnabled(box.isChecked() && previewAllowed.getAsBoolean());};
            refresh.add(update);update.run();
            box.setOnCheckedChangeListener((v,checked)->{if(checked && field.getText().toString().trim().isEmpty())field.setText(inherited.get());update.run();changed.run();});
            field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(Editable e){}});
        }
        Runnable update=()->{for(Runnable action:refresh)action.run();};refreshers.add(update);
        normal.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){update.run();}public void afterTextChanged(Editable e){}});
    }
    void refresh(int index){refreshers.get(index).run();}
    TrendColorOverrides words(){return new TrendColorOverrides(text(red),flags(redEnabled),text(yellow),flags(yellowEnabled));}
    private static List<String> text(List<EditText> fields){List<String> result=new ArrayList<>();for(EditText field:fields)result.add(field.getText().toString());return result;}
    private static List<Boolean> flags(List<CheckBox> fields){List<Boolean> result=new ArrayList<>();for(CheckBox box:fields)result.add(box.isChecked());return result;}
    ArrayList<String> values(){ArrayList<String> result=new ArrayList<>();for(int i=0;i<7;i++){result.add(Boolean.toString(redEnabled.get(i).isChecked()));result.add(red.get(i).getText().toString());result.add(Boolean.toString(yellowEnabled.get(i).isChecked()));result.add(yellow.get(i).getText().toString());}return result;}
    void restore(List<String> values){if(values==null || values.size()!=28)return;for(int i=0;i<7;i++){redEnabled.get(i).setChecked(Boolean.parseBoolean(values.get(i*4)));red.get(i).setText(values.get(i*4+1));yellowEnabled.get(i).setChecked(Boolean.parseBoolean(values.get(i*4+2)));yellow.get(i).setText(values.get(i*4+3));refresh(i);}}
    void reset(){for(int i=0;i<7;i++){redEnabled.get(i).setChecked(false);yellowEnabled.get(i).setChecked(false);red.get(i).setText("");yellow.get(i).setText("");refresh(i);}}
    static List<String> defaultValues(){List<String> result=new ArrayList<>();for(int i=0;i<7;i++)result.addAll(Arrays.asList("false","","false",""));return result;}
}
