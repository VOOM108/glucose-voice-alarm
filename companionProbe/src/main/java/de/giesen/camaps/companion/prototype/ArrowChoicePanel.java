// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import de.giesen.gva.watch.ArrowColor;
import java.util.function.IntConsumer;

/** One bounded dialog panel: pinned source preview and a separately scrolling seven-row choice grid. */
final class ArrowChoicePanel extends LinearLayout {
    static final NotificationParser.Trend[] DIRECTIONS={NotificationParser.Trend.DOUBLE_UP,NotificationParser.Trend.UP,
        NotificationParser.Trend.UP_RIGHT,NotificationParser.Trend.FLAT,NotificationParser.Trend.DOWN_RIGHT,
        NotificationParser.Trend.DOWN,NotificationParser.Trend.DOUBLE_DOWN};
    private final Button[] choices=new Button[DIRECTIONS.length*ArrowColor.values().length];
    private final ImageView preview;
    private final ScrollView scroll;
    ArrowChoicePanel(Context context,Bitmap image,NotificationParser.Trend assigned,ArrowColor assignedColor,IntConsumer selected) {
        super(context);setOrientation(VERTICAL);setTag("arrow_choice_panel");setPadding(dp(16),dp(8),dp(16),dp(8));
        preview=new ImageView(context);preview.setTag("vector_preview");preview.setImageBitmap(image);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(0xffb0b0b0);
        preview.setContentDescription(AppText.tr("Isolierte CamAPS-Vektorgrafik zur manuellen Zuordnung")
            +(assigned==NotificationParser.Trend.UNKNOWN?"":". "+AppText.tr("Bisher zugeordnet:")+" "+DisplayTrendPreferences.title(context,assigned,assignedColor)));
        addView(preview,new LayoutParams(-1,dp(64)));
        scroll=new ScrollView(context);scroll.setTag("arrow_choice_scroll");scroll.setFillViewport(false);
        LayoutParams viewport=new LayoutParams(-1,0,1);viewport.topMargin=dp(4);addView(scroll,viewport);
        LinearLayout grid=new LinearLayout(context);grid.setOrientation(VERTICAL);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        for(int direction=0;direction<DIRECTIONS.length;direction++) {
            LinearLayout row=new LinearLayout(context);row.setTag("arrow_choice_row_"+direction);row.setGravity(Gravity.CENTER_VERTICAL);
            grid.addView(row,new LayoutParams(-1,dp(52)));
            for(ArrowColor color:ArrowColor.values()) {
                final int index=color.ordinal()*DIRECTIONS.length+direction;
                NotificationParser.Trend trend=DIRECTIONS[direction];Button button=new Button(context);choices[index]=button;
                button.setTag("arrow_choice_"+index);button.setText(trend.symbol);button.setAllCaps(false);
                // These are icon-sized glyphs; accessible full labels remain independent of font size.
                button.setTextSize(TypedValue.COMPLEX_UNIT_DIP,32);button.setIncludeFontPadding(false);
                button.setSingleLine(true);button.setGravity(Gravity.CENTER);button.setPadding(0,0,0,0);
                button.setMinWidth(0);button.setMinimumWidth(dp(48));button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));
                button.setBackgroundTintList(null);
                String label=color==ArrowColor.NORMAL?MetricNotification.trendTitle(trend):DisplayTrendPreferences.colorTitle(trend,color);
                button.setContentDescription(label);button.setTooltipText(label);
                button.setTextColor(color.colored()?color.phone:AppAppearance.colors(context).ink);
                LayoutParams cell=new LayoutParams(0,dp(48),1);cell.setMargins(dp(2),0,dp(2),0);row.addView(button,cell);
                button.setOnClickListener(v->{mark(index);selected.accept(index);});
            }
        }
        int assignedIndex=-1;
        for(int i=0;i<DIRECTIONS.length;i++)if(DIRECTIONS[i]==assigned)assignedIndex=assignedColor.ordinal()*DIRECTIONS.length+i;
        mark(assignedIndex);
    }
    private void mark(int selected) {
        AppAppearance.Colors colors=AppAppearance.colors(getContext());
        for(int i=0;i<choices.length;i++) {
            Button button=choices[i];boolean active=i==selected;button.setSelected(active);
            GradientDrawable shape=new GradientDrawable();shape.setColor(active?colors.soft:colors.surface);
            shape.setCornerRadius(dp(8));shape.setStroke(dp(active?3:1),active?colors.accent:colors.line);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.line),shape,null));
        }
    }
    static void fitActions(AlertDialog dialog) {
        int[] ids={AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE};
        int available=dialog.getContext().getResources().getDisplayMetrics().widthPixels-Math.round(64*dialog.getContext().getResources().getDisplayMetrics().density);
        float required=0;
        for(int id:ids){Button b=dialog.getButton(id);required+=Math.max(b.getMinimumWidth(),b.getPaint().measureText(b.getText().toString())+b.getPaddingLeft()+b.getPaddingRight());}
        if(required<=available)return;
        String[] icons={"↻","×","✓"};
        for(int i=0;i<ids.length;i++) {
            Button b=dialog.getButton(ids[i]);CharSequence label=b.getText();b.setContentDescription(label);b.setTooltipText(label);
            b.setAllCaps(false);b.setText(icons[i]);b.setTextSize(TypedValue.COMPLEX_UNIT_DIP,24);b.setIncludeFontPadding(false);
            int touch=Math.round(48*b.getResources().getDisplayMetrics().density);b.setMinWidth(touch);b.setMinimumWidth(touch);b.setMinHeight(touch);b.setMinimumHeight(touch);b.setPadding(0,0,0,0);
        }
        if(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent() instanceof LinearLayout) {
            LinearLayout bar=(LinearLayout)dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent();
            // Native ButtonBarLayout can retain its earlier stacked orientation after captions shrink.
            bar.setOrientation(HORIZONTAL);bar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            for(int i=0;i<bar.getChildCount();i++)if(bar.getChildAt(i) instanceof Space)bar.getChildAt(i).setVisibility(INVISIBLE);
            for(int id:ids)bar.bringChildToFront(dialog.getButton(id));
            bar.requestLayout();
        }
    }
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec) {
        int desired=dp(64+4+DIRECTIONS.length*52+16),mode=View.MeasureSpec.getMode(heightMeasureSpec);
        int available=mode==View.MeasureSpec.UNSPECIFIED?Math.max(0,getResources().getDisplayMetrics().heightPixels-dp(160)):View.MeasureSpec.getSize(heightMeasureSpec);
        int height=Math.min(desired,available);
        preview.getLayoutParams().height=Math.min(dp(64),Math.max(dp(32),height/4));
        super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
