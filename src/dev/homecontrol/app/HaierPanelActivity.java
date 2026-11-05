package dev.homecontrol.app;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** The sheet is built once; background reads update values, never its view tree. */
public final class HaierPanelActivity extends Activity {
    private AppUi ui;private String id;private boolean running,destroyed,foreground,refreshQueued,accountUnavailable;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HaierPanelQueue commands=new HaierPanelQueue();
    private JSONObject device=new JSONObject();private TextView title,subtitle,feedback,power,light,stop;private ImageView icon;
    private final Map<String,TextView> speeds=new LinkedHashMap<>();
    private final Runnable poll=()->{if(foreground){refresh();schedulePoll();}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AppUi.systemBars(this);ui=new AppUi(this);id=getIntent().getStringExtra("id");
        if(!HaierClient.isDevice(id)){finish();return;}
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setDimAmount(.32f);getWindow().setLayout(-1,-1);
        buildSheet();updateValues();
    }
    @Override protected void onResume(){super.onResume();foreground=true;refresh();schedulePoll();}
    @Override protected void onPause(){foreground=false;main.removeCallbacks(poll);commands.clearWaiting();refreshQueued=false;super.onPause();}
    private void schedulePoll(){main.removeCallbacks(poll);if(foreground)main.postDelayed(poll,15000);}
    private void buildSheet(){
        FrameLayout root=new FrameLayout(this);root.setOnClickListener(v->finish());
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left+ui.dp(12),b.top,b.right+ui.dp(12),b.bottom+ui.dp(12));return insets;});
        LinearLayout card=ui.column();card.setClickable(true);card.setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(14));card.setBackground(ui.shape(ui.surface,26));
        LinearLayout header=ui.row();icon=new ImageView(this);header.addView(icon,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
        LinearLayout labels=ui.column();title=ui.text("吸油烟机",20,ui.text,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);labels.addView(title);
        subtitle=ui.text("状态待更新",13,ui.muted,false);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);labels.addView(subtitle);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=ui.dp(12);header.addView(labels,lp);
        FrameLayout refresh=new FrameLayout(this);refresh.setBackground(ui.ripple(ui.pale,14));refresh.addView(ui.icon(AppUi.REFRESH,ui.accent,20),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));refresh.setContentDescription("刷新海尔状态");refresh.setOnClickListener(v->refresh());header.addView(refresh,new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));card.addView(header);ui.space(card,14);
        feedback=ui.text("",13,ui.accent,false);feedback.setLines(2);feedback.setEllipsize(TextUtils.TruncateAt.END);card.addView(feedback,new LinearLayout.LayoutParams(-1,ui.dp(40)));ui.space(card,14);
        LinearLayout toggles=ui.row();power=control("电源",()->toggle("onOffStatus"));toggles.addView(power,new LinearLayout.LayoutParams(0,ui.dp(62),1));gap(toggles);light=control("照明",()->toggle("lightingStatus"));toggles.addView(light,new LinearLayout.LayoutParams(0,ui.dp(62),1));card.addView(toggles);ui.space(card,12);
        card.addView(ui.text("风速",14,ui.muted,true));ui.space(card,8);LinearLayout row=ui.row();
        for(String[] option:new String[][]{{"1","柔速"},{"3","高速"},{"4","爆炒"}}){if(row.getChildCount()>0)gap(row);String value=option[0];TextView b=control(option[1],()->command("windSpeedLevel",value));speeds.put(value,b);row.addView(b,new LinearLayout.LayoutParams(0,ui.dp(54),1));}card.addView(row);ui.space(card,8);
        stop=control("关闭风机",()->command("windSpeedLevel","0"));card.addView(stop,new LinearLayout.LayoutParams(-1,ui.dp(44)));ui.space(card,12);
        TextView more=ui.text("更多  ›",14,ui.accent,true);more.setGravity(Gravity.CENTER);more.setPadding(0,ui.dp(12),0,ui.dp(12));more.setBackground(ui.ripple(ui.background,16));more.setOnClickListener(v->startActivity(new Intent(this,HaierActivity.class).putExtra("device",id)));card.addView(more,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(card);int max=Math.round(getResources().getDisplayMetrics().heightPixels*.78f);card.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels-ui.dp(24),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.addView(scroll,new FrameLayout.LayoutParams(-1,Math.min(card.getMeasuredHeight(),max),Gravity.BOTTOM));setContentView(root);
    }
    private void gap(LinearLayout row){row.addView(new View(this),new LinearLayout.LayoutParams(ui.dp(8),1));}
    private TextView control(String label,Runnable action){TextView b=ui.text(label,15,ui.text,true);b.setGravity(Gravity.CENTER);b.setBackground(ui.ripple(ui.background,16));b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->action.run());return b;}
    private void updateValues(){
        if(destroyed)return;JSONObject latest=accountUnavailable?null:find();if(accountUnavailable)device=new JSONObject();else if(latest!=null)device=latest;
        text(title,device.optString("title","吸油烟机"));text(subtitle,device.optString("subtitle","状态待更新"));icon.setImageBitmap(DeviceIcons.make(this,device,ui.accent));
        Map<String,JSONObject> attrs=attributes();updateToggle(power,"电源","开机","关机",attrs.get("onOffStatus"));updateToggle(light,"照明","开灯","关灯",attrs.get("lightingStatus"));
        JSONObject wind=attrs.get("windSpeedLevel");for(Map.Entry<String,TextView> e:speeds.entrySet())style(e.getValue(),supports(wind,e.getKey()),wind!=null&&e.getKey().equals(wind.optString("value")));style(stop,supports(wind,"0"),wind!=null&&"0".equals(wind.optString("value")));
    }
    private void updateToggle(TextView button,String label,String on,String off,JSONObject attr){String value=attr==null?"":attr.optString("value");boolean known="true".equals(value)||"false".equals(value);text(button,label+(known?" · "+("true".equals(value)?off:on):""));style(button,known&&supports(attr,"true".equals(value)?"false":"true"),"true".equals(value));}
    private void style(TextView b,boolean enabled,boolean selected){boolean wasSelected=b.isSelected();if(wasSelected!=selected){b.setSelected(selected);b.setTextColor(selected?ui.accent:ui.text);b.setBackground(ui.ripple(selected?ui.pale:ui.background,16));}if(b.isEnabled()!=enabled){b.setEnabled(enabled);b.setAlpha(enabled?1f:.45f);}}
    private static void text(TextView v,String text){if(!text.contentEquals(v.getText()))v.setText(text);}
    private static boolean supports(JSONObject a,String value){if(a==null||!a.optBoolean("writable"))return false;JSONObject range=a.optJSONObject("range");JSONArray data=range==null?null:range.optJSONArray("dataList");if(data!=null)for(int i=0;i<data.length();i++)if(data.optJSONObject(i)!=null&&value.equals(data.optJSONObject(i).optString("data")))return true;return false;}
    private Map<String,JSONObject> attributes(){Map<String,JSONObject> map=new HashMap<>();JSONArray caps=device.optJSONArray("capabilities");if(caps!=null)for(int i=0;i<caps.length();i++){JSONObject a=caps.optJSONObject(i);if(a!=null)map.put(a.optString("key"),a);}return map;}
    private JSONObject find(){JSONArray list=DeviceStore.devices(this);for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))&&"haier_cloud".equals(item.optString("source")))return item;}return null;}
    private void toggle(String key){JSONObject a=attributes().get(key);if(a==null)return;String current=commands.desired(key,a.optString("value"));if(!"true".equals(current)&&!"false".equals(current))return;command(key,"true".equals(current)?"false":"true");}
    private void command(String key,String value){
        KeyguardManager km=getSystemService(KeyguardManager.class);if(km==null||km.isDeviceLocked()){showMessage("请先解锁手机");return;}
        commands.offer(key,value);pump();
    }
    private void refresh(){refreshQueued=true;pump();}
    private void pump(){
        if(running||destroyed||!foreground)return;HaierPanelQueue.Command command=commands.next();
        if(command==null&&!refreshQueued)return;boolean read=command==null;if(read)refreshQueued=false;running=true;
        new Thread(()->{
            String result=null;Exception failure=null;boolean missingLogin=false;
            try{if(read){if(!HaierCloud.loggedIn(this))missingLogin=true;else HaierCloud.refresh(this);}else result=HaierCloud.control(this,id,command.key,command.value,true);}
            catch(Exception e){failure=e;}
            final String outcome=result;final Exception error=failure;final boolean login=missingLogin;
            main.post(()->{
                running=false;if(command!=null)commands.completed(command);if(destroyed)return;
                if(login){accountUnavailable=true;showMessage("请点「更多」登录海尔账号");}
                else if(error!=null){if(command!=null)commands.clearWaiting();showMessage(errorMessage(error));}
                else {accountUnavailable=false;if(outcome!=null)showMessage(outcome);else if(feedback.getText().length()>0)showMessage("");}
                updateValues();DeviceStore.operation(this,id,"");DeviceWidget.updateAll(this);MainActivity.notifyChanged();
                if(command!=null){refreshQueued=true;}pump();
            });
        },read?"HaierPanelRead":"HaierPanelCommand").start();
    }
    private void showMessage(String message){if(!destroyed)text(feedback,message);}
    private String errorMessage(Exception error){if(error instanceof java.net.SocketTimeoutException)return "连接暂时超时，按钮保留，可稍后重试";if(error instanceof java.io.IOException&&error.getMessage()!=null)return error.getMessage();return "海尔暂不可用，已保留上次状态";}
    @Override protected void onDestroy(){destroyed=true;foreground=false;commands.clearWaiting();main.removeCallbacks(poll);super.onDestroy();}
}
