package dev.homecontrol.app;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Shared device editors use the app palette in both system themes. */
final class AppSheet {
    static Dialog show(Activity owner,String title,View body,String save,Runnable action){
        AppUi ui=new AppUi(owner);Dialog dialog=new Dialog(owner);LinearLayout root=ui.column();
        root.setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(28));root.setBackground(ui.shape(ui.surface,24));
        root.addView(ui.text(title,19,ui.text,true));ui.space(root,16);
        ScrollView scroll=new ScrollView(owner){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.round(getResources().getDisplayMetrics().heightPixels*.62f),MeasureSpec.AT_MOST));}};
        scroll.setFillViewport(false);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-2));ui.space(root,18);
        LinearLayout buttons=ui.row();TextView cancel=button(owner,ui,save==null?"完成":"取消",false);cancel.setOnClickListener(v->dialog.dismiss());buttons.addView(cancel,new LinearLayout.LayoutParams(0,ui.dp(48),1));
        if(save!=null){View gap=new View(owner);buttons.addView(gap,new LinearLayout.LayoutParams(ui.dp(10),1));TextView submit=button(owner,ui,save,true);submit.setOnClickListener(v->{action.run();});buttons.addView(submit,new LinearLayout.LayoutParams(0,ui.dp(48),1));}
        root.addView(buttons);dialog.setContentView(root);dialog.setOnShowListener(v->{Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.28f);}});dialog.show();return dialog;
    }
    static TextView button(Activity owner,AppUi ui,String title,boolean primary){TextView text=ui.text(title,15,primary?ui.accent:ui.text,true);text.setGravity(Gravity.CENTER);text.setBackground(ui.ripple(primary?ui.pale:ui.background,14));text.setContentDescription(title);return text;}
    static void input(EditText edit,AppUi ui){edit.setTextColor(ui.text);edit.setHintTextColor(ui.muted);edit.setTextSize(15);edit.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(10));edit.setBackground(ui.shape(ui.background,12));edit.setMinimumHeight(ui.dp(48));edit.setSaveEnabled(false);edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);}
}
