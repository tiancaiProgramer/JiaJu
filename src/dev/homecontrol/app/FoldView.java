package dev.homecontrol.app;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** One clipped rounded surface in both collapsed and expanded states. */
final class FoldView extends LinearLayout {
    final LinearLayout body;
    FoldView(Context c,AppUi ui,String label,String detail,int icon,boolean open,Consumer<Boolean> changed){
        super(c);setOrientation(VERTICAL);setBackground(ui.shape(ui.surface,20));setClipToOutline(true);
        LinearLayout head=ui.row();head.setPadding(ui.dp(14),ui.dp(6),ui.dp(12),ui.dp(6));head.setMinimumHeight(ui.dp(56));head.addView(ui.icon(icon,ui.accent,18));
        TextView name=ui.text(label,15,ui.text,true);LayoutParams text=new LayoutParams(0,-2,1);text.leftMargin=ui.dp(10);head.addView(name,text);
        if(detail!=null&&!detail.isEmpty()){TextView count=ui.text(detail,12,ui.muted,false);LayoutParams p=new LayoutParams(-2,-2);p.leftMargin=ui.dp(6);p.rightMargin=ui.dp(6);head.addView(count,p);}
        View arrow=ui.icon(AppUi.DOWN,ui.muted,17);arrow.setRotation(open?180:0);head.addView(arrow);head.setContentDescription(label+(detail==null?"":" · "+detail));head.setStateDescription(open?"已展开":"已折叠");head.setFocusable(true);head.setClickable(true);head.setBackground(ui.ripple(ui.surface,20));addView(head);
        body=ui.column();body.setPadding(ui.dp(14),0,ui.dp(14),ui.dp(14));body.setVisibility(open?VISIBLE:GONE);addView(body);
        head.setOnClickListener(v->{boolean next=body.getVisibility()!=VISIBLE;body.setVisibility(next?VISIBLE:GONE);arrow.setRotation(next?180:0);head.setStateDescription(next?"已展开":"已折叠");if(changed!=null)changed.accept(next);});
    }
}
