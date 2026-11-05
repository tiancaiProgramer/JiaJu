package dev.homecontrol.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Shared spacing and navigation for the local app's secondary pages. */
abstract class AppPage extends Activity {
    AppUi ui;
    LinearLayout content;
    ScrollView scroll;
    @Override public void onCreate(Bundle state) {super.onCreate(state);AppUi.systemBars(this);ui=new AppUi(this);}
    void page(String title,Runnable back) {
        LinearLayout root=ui.column();root.setBackgroundColor(ui.background);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        scroll=new ScrollView(this);content=ui.column();content.setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        LinearLayout heading=ui.row();heading.addView(iconButton("返回",AppUi.BACK,back));TextView name=ui.text(title,22,ui.text,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=ui.dp(10);heading.addView(name,p);content.addView(heading);ui.space(content,22);
    }
    View iconButton(String label,int icon,Runnable action) {
        FrameLayout view=new FrameLayout(this);view.setBackground(ui.ripple(ui.surface,14));view.setContentDescription(label);view.setFocusable(true);view.setClickable(true);
        view.addView(ui.icon(icon,ui.accent,20),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));view.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));view.setOnClickListener(v->action.run());return view;
    }
    View option(String label,String detail,int icon,Runnable action) {
        LinearLayout row=ui.row();row.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));row.setMinimumHeight(ui.dp(68));row.setBackground(ui.ripple(ui.surface,22));row.setContentDescription(label);row.setFocusable(true);row.setClickable(true);
        row.addView(ui.badge(icon),new LinearLayout.LayoutParams(ui.dp(34),ui.dp(34)));LinearLayout words=ui.column();words.addView(ui.text(label,15,ui.text,true));if(detail!=null&&!detail.isEmpty()){ui.space(words,4);words.addView(ui.text(detail,12,ui.muted,false));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=ui.dp(12);p.rightMargin=ui.dp(8);row.addView(words,p);row.addView(ui.icon(AppUi.RIGHT,ui.muted,17));row.setOnClickListener(v->action.run());return row;
    }
    void addOption(String label,String detail,int icon,Runnable action) {content.addView(option(label,detail,icon,action));ui.space(content,10);}
    void note(String value) {TextView text=ui.text(value,13,ui.muted,false);text.setLineSpacing(ui.dp(4),1);content.addView(text);}
}
