package dev.homecontrol.app;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Direct anchored menu. The arrow owns its column, so labels center in the remaining space. */
final class ChoiceView extends LinearLayout {
    private String selectedValue;
    ChoiceView(Context context,AppUi ui,String description,LinkedHashMap<String,String> choices,String selected,int width,Consumer<String> change){
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(ui.dp(8),0,ui.dp(8),0);setMinimumHeight(ui.dp(44));setBackground(ui.ripple(ui.pale,12));setFocusable(true);setClickable(true);
        selectedValue=selected;String value=choices.get(selected);if(value==null)value=choices.values().iterator().next();setContentDescription(description);setStateDescription(value);
        TextView label=ui.text(value,13,ui.text,false);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(label,new LayoutParams(0,-1,1));
        LayoutParams arrow=new LayoutParams(ui.dp(16),ui.dp(16));arrow.leftMargin=ui.dp(4);addView(ui.icon(AppUi.DOWN,ui.muted,16),arrow);setLayoutParams(new LayoutParams(ui.dp(width),ui.dp(44)));
        setOnClickListener(v->{
            LinearLayout rows=ui.column();rows.setPadding(ui.dp(5),ui.dp(5),ui.dp(5),ui.dp(5));ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(rows);
            int popupWidth=Math.min(getResources().getDisplayMetrics().widthPixels-ui.dp(32),Math.max(getWidth(),ui.dp(130)));
            PopupWindow popup=new PopupWindow(scroll,popupWidth,Math.min(ui.dp(54*choices.size()+10),ui.dp(330)),true);popup.setBackgroundDrawable(ui.shape(ui.surface,16));popup.setElevation(ui.dp(8));popup.setOutsideTouchable(true);scroll.setBackground(ui.shape(ui.surface,16));scroll.setClipToOutline(true);
            for(Map.Entry<String,String> entry:choices.entrySet()){TextView item=ui.text(entry.getValue(),14,ui.text,false);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(ui.dp(12),0,ui.dp(12),0);item.setSingleLine(false);item.setBackground(ui.ripple(entry.getKey().equals(selectedValue)?ui.pale:ui.surface,11));item.setContentDescription(description+"："+entry.getValue());item.setOnClickListener(w->{popup.dismiss();if(!entry.getKey().equals(selectedValue)){selectedValue=entry.getKey();label.setText(entry.getValue());setStateDescription(entry.getValue());change.accept(entry.getKey());}});rows.addView(item,new LayoutParams(-1,ui.dp(52)));}
            popup.showAsDropDown(this,0,ui.dp(4),Gravity.END);
        });
    }
    static LinkedHashMap<String,String> placements(boolean module){LinkedHashMap<String,String> out=new LinkedHashMap<>();out.put("home","首页");out.put("all","更多");if(!module)out.put("hidden","隐藏");out.put("excluded","不展示");return out;}
}
