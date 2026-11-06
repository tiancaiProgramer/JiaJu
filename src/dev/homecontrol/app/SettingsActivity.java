package dev.homecontrol.app;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public final class SettingsActivity extends AppPage {
    private String screen="settings";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean foreground;
    @Override public void onCreate(Bundle state) {super.onCreate(state);screen=getIntent().getStringExtra("screen");if(screen==null)screen="settings";if(state!=null)screen=state.getString("screen","settings");render();}
    @Override protected void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);state.putString("screen",screen);}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed() {if(!"settings".equals(screen)){screen="settings";render();}else super.onBackPressed();}
    private void open(String target) {screen=target;render();}
    private void render() {
        if("accounts".equals(screen)) {
            page("账号管理",this::onBackPressed);
            addOption("海尔智家","登录、同步与退出账号",AppUi.DEVICES,()->startActivity(new Intent(this,HaierActivity.class)));
            addOption("米家","登录、同步与退出账号",AppUi.DEVICES,()->startActivity(new Intent(this,MijiaActivity.class)));
        } else if("about".equals(screen)) {
            page("关于家聚",this::onBackPressed);content.addView(ui.text("家聚",30,ui.text,true));ui.space(content,8);note("版本 "+BuildIdentity.VERSION_NAME+" · 米家 + 海尔独立控制");ui.space(content,22);
            addOption("检查更新","查看 GitHub 最新发布",AppUi.REFRESH,()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/tiancaiProgramer/JiaJu/releases/latest"))));
            note("米家与海尔的独立设备面板。无需安装官方 App 或启用 LSPosed 模块，设备可用功能由云端能力定义决定。");
        } else {
            page("设置",this::finish);
            addOption("账号管理","海尔智家 · 米家",AppUi.DEVICES,()->open("accounts"));
            addOption("添加桌面小组件","在桌面查看与打开常用设备",AppUi.DESKTOP,this::addDesktop);
            addOption("添加负一屏","手动添加指引 · 兼容适配中",AppUi.NEGATIVE,this::addNegative);
            addOption("关于","版本 "+BuildIdentity.VERSION_NAME,AppUi.HELP,()->open("about"));
        }
    }
    private void addDesktop() {
        AppWidgetManager manager=getSystemService(AppWidgetManager.class);
        try {
            if(shortcutPermissionBlocked()){desktopPermission();return;}
            ComponentName provider=new ComponentName(this,DeviceWidget.class);int previous=manager.getAppWidgetIds(provider).length;
            if(manager.isRequestPinAppWidgetSupported()&&manager.requestPinAppWidget(provider,null,null)){
                main.postDelayed(()->{if(isDestroyed())return;if(manager.getAppWidgetIds(provider).length>previous)Toast.makeText(this,"桌面小组件已添加",Toast.LENGTH_SHORT).show();else if(foreground)desktopPermission();},8000);return;
            }
            desktopHelp();
        } catch(SecurityException e) {
            desktopPermission();
        }
    }
    private boolean shortcutPermissionBlocked(){try{android.app.AppOpsManager ops=getSystemService(android.app.AppOpsManager.class);int mode=(Integer)ops.getClass().getMethod("checkOpNoThrow",int.class,int.class,String.class).invoke(ops,10017,android.os.Process.myUid(),getPackageName());return mode==android.app.AppOpsManager.MODE_IGNORED||mode==android.app.AppOpsManager.MODE_ERRORED;}catch(Exception e){return false;}}
    private void desktopPermission(){new AlertDialog.Builder(this).setTitle("允许添加桌面小组件").setMessage("尚未收到桌面添加确认。HyperOS 需要允许家聚「创建桌面快捷方式」，请开启后返回重试。也可以长按桌面，从小组件列表手动添加「家聚设备」。").setPositiveButton("前往权限设置",(d,w)->{try{startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter").putExtra("extra_pkgname",getPackageName()));}catch(RuntimeException e){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}}).setNegativeButton("关闭",null).show();}
    private void desktopHelp() {new AlertDialog.Builder(this).setTitle("添加桌面小组件").setMessage("长按桌面空白处 → 添加小组件 → 安卓小组件 → 家聚设备。此系统暂未提供应用内直接固定入口。").setPositiveButton("知道了",null).show();}
    private void addNegative() {
        new AlertDialog.Builder(this).setTitle("添加到负一屏").setMessage("当前版本尚未支持一键添加。请在桌面向右滑到负一屏，点击右上角「＋」，在安卓小组件中查找「家聚设备」。若系统没有该组件入口，请先使用桌面小组件。负一屏兼容仍在适配中。").setPositiveButton("返回桌面",(d,w)->startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))).setNegativeButton("知道了",null).show();
    }
}
