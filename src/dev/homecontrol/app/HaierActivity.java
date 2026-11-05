package dev.homecontrol.app;

import android.app.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Own account and device UI. Opening this Activity only reads device state. */
public final class HaierActivity extends Activity {
    private AppUi ui;private LinearLayout content;private String id,message="";private boolean busy,authenticated,destroyed,loginForm;
    private final Handler main=new Handler(Looper.getMainLooper());private EditText phone,password;
    private interface Work {String run()throws Exception;}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppUi.systemBars(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);ui=new AppUi(this);id=getIntent().getStringExtra("device");render();run(()->{authenticated=HaierCloud.loggedIn(this);if(authenticated){HaierCloud.refresh(this);return HaierClient.summary(this);}return "请先登录海尔账号";});}
    private void render(){
        if(destroyed)return;LinearLayout page=ui.column();page.setBackgroundColor(ui.background);
        page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        ScrollView scroll=new ScrollView(this);content=ui.column();content.setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(24));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(page);
        button("返回设备列表",true,this::finish);ui.space(content,16);content.addView(ui.text("海尔账号与设备",24,ui.text,true));ui.space(content,8);
        content.addView(ui.text("独立接入 · 试验版，无需安装海尔智家",13,ui.muted,false));ui.space(content,12);
        content.addView(ui.text(busy?"正在连接海尔云端…":message,14,ui.accent,false));ui.space(content,18);
        if(busy)return;
        if(!authenticated||loginForm){login();return;}
        content.addView(ui.text("登录会话已加密保存在本机。设备按钮位于卡片弹窗中；这里管理账号和设备同步。",14,ui.text,false));ui.space(content,14);
        button("刷新海尔设备",true,()->run(()->{HaierCloud.refresh(this);return HaierClient.summary(this);}));
        if(id!=null)button("检查控制能力（不操作设备）",true,()->run(()->HaierCloud.control(this,id,"onOffStatus","true",false)));
        ui.space(content,18);button("重新登录",true,()->{loginForm=true;message="";render();});button("退出海尔账号",true,()->new AlertDialog.Builder(this).setTitle("退出海尔账号？").setMessage("将删除本应用保存的海尔会话和设备缓存。常驻卡片仍保留为待登录状态。").setPositiveButton("退出",(d,w)->run(()->{HaierCloud.logout(this);authenticated=false;return "已退出海尔账号";})).setNegativeButton("取消",null).show());
    }
    private void login(){
        button("短信验证码登录（海尔官方授权页）",true,()->startActivityForResult(new android.content.Intent(this,HaierOAuthActivity.class),70));
        content.addView(ui.text("验证码由海尔官方页面发送和验证，不需要安装海尔智家。试验接入使用公开的 Home Assistant 授权配置，是否开放油烟机仍需登录后验证。",13,ui.muted,false));ui.space(content,14);
        content.addView(ui.text("使用海尔智家手机号和密码登录。密码仅发送给海尔登录接口，不保存、不写日志；会话通过 Android Keystore 加密保存。此功能使用社区协议实现，尚需实机验证。",14,ui.text,false));ui.space(content,14);
        phone=new EditText(this);phone.setHint("海尔账号手机号");phone.setInputType(InputType.TYPE_CLASS_PHONE);phone.setSingleLine(true);phone.setSaveEnabled(false);phone.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);content.addView(phone,new LinearLayout.LayoutParams(-1,ui.dp(56)));
        password=new EditText(this);password.setHint("海尔账号密码");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSingleLine(true);password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);content.addView(password,new LinearLayout.LayoutParams(-1,ui.dp(56)));ui.space(content,12);
        button("登录并读取设备",true,()->{String number=phone.getText().toString().trim(),secret=password.getText().toString();password.setText("");run(()->{HaierCloud.login(this,number,secret);authenticated=true;loginForm=false;HaierCloud.refresh(this);return HaierClient.summary(this);});});
        if(authenticated)button("取消重新登录",true,()->{loginForm=false;render();});
        content.addView(ui.text("密码登录如需额外验证，可改用上方官方短信授权。",12,ui.muted,false));
    }
    private void button(String label,boolean enabled,Runnable action){TextView b=ui.text(label,15,enabled?ui.accent:ui.muted,true);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));b.setBackground(ui.ripple(ui.surface,18));b.setEnabled(enabled&&!busy);b.setAlpha(enabled?1f:.5f);b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->{if(!busy)action.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=ui.dp(8);b.setMinimumHeight(ui.dp(48));content.addView(b,p);}
    private void run(Work work){if(busy)return;busy=true;render();new Thread(()->{String result;try{result=work.run();}catch(Exception e){result=safeError(e);DeviceStore.putPreference(this,"haier_error",result);}final String text=result;main.post(()->{busy=false;message=text;if(id!=null)DeviceStore.operation(this,id,"");DeviceWidget.updateAll(this);MainActivity.notifyChanged();if(!destroyed)render();});},"HaierCloudUi").start();}
    private String safeError(Exception e){if(e instanceof java.net.SocketTimeoutException)return "连接海尔超时，请检查网络后重试";if(e instanceof javax.net.ssl.SSLException)return "海尔安全连接未建立，请检查网络";if(e instanceof java.net.UnknownHostException)return "无法连接海尔，请检查网络";if(e instanceof org.json.JSONException)return "海尔响应格式不兼容";return e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"海尔接入未完成，请重试或重新登录";}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==70&&result==RESULT_OK){authenticated=true;loginForm=false;run(()->{HaierCloud.refresh(this);return HaierClient.summary(this);});}}
    @Override protected void onDestroy(){destroyed=true;if(password!=null)password.setText("");super.onDestroy();}
}
