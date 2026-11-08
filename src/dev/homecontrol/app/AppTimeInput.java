package dev.homecontrol.app;
import android.content.Context;
import android.widget.*;
import java.util.*;

/** Time fields use the same palette and choice menus as other device settings. */
final class AppTimeInput extends LinearLayout {
 private int hour,minute;
 AppTimeInput(Context context,AppUi ui,String title,int minutes){super(context);if(minutes<0||minutes>1439)throw new IllegalArgumentException("时间无效");hour=minutes/60;minute=minutes%60;setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);setContentDescription(title);TextView titleView=ui.text(title.replace("循环",""),13,ui.muted,false);titleView.setGravity(android.view.Gravity.CENTER_VERTICAL);addView(titleView,new LayoutParams(ui.dp(64),ui.dp(48)));LinkedHashMap<String,String> hours=new LinkedHashMap<>(),mins=new LinkedHashMap<>();for(int i=0;i<24;i++)hours.put(Integer.toString(i),String.format(Locale.ROOT,"%02d",i));for(int i=0;i<60;i++)mins.put(Integer.toString(i),String.format(Locale.ROOT,"%02d",i));ChoiceView hourView=new ChoiceView(context,ui,title+"·小时",hours,Integer.toString(hour),110,key->hour=Integer.parseInt(key)),minuteView=new ChoiceView(context,ui,title+"·分钟",mins,Integer.toString(minute),110,key->minute=Integer.parseInt(key));addView(hourView,new LayoutParams(0,ui.dp(48),1));TextView separator=ui.text(":",18,ui.muted,true);separator.setGravity(android.view.Gravity.CENTER);addView(separator,new LayoutParams(ui.dp(28),ui.dp(48)));addView(minuteView,new LayoutParams(0,ui.dp(48),1));}
 int getHour(){return hour;}int getMinute(){return minute;}
}
