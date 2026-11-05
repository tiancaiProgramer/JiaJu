package dev.homecontrol.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.*;
import android.widget.*;

public final class MijiaActivity extends AppPage {
    private boolean busy,authenticated;private String message="";private Spinner regions;private final Handler main=new Handler(Looper.getMainLooper());
    private static final String[] LABELS={"中国大陆","德国 / 欧洲","美国","俄罗斯","中国台湾","新加坡","印度","印度（i2）"};
    @Override public void onCreate(Bundle state){super.onCreate(state);render();check();}
    private void check(){run(()->{authenticated=MijiaCloud.loggedIn(this);return authenticated?MijiaCloud.summary(this):"请先登录米家账号";});}
    private void render(){if(isDestroyed())return;page("米家账号",this::finish);note(busy?"正在连接米家…":message);ui.space(content,20);content.addView(ui.text("账号地区",15,ui.text,true));ui.space(content,8);regions=new Spinner(this);regions.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,LABELS));int index=MijiaWire.REGIONS.indexOf(DeviceStore.preference(this,"mijia_region","cn"));regions.setSelection(Math.max(0,index));regions.setEnabled(!busy);content.addView(regions);ui.space(content,20);
        if(!busy){addOption(authenticated?"重新登录米家":"登录米家账号","在小米官方网页自行完成登录与验证",AppUi.DEVICES,()->{String region=MijiaWire.REGIONS.get(regions.getSelectedItemPosition());startActivityForResult(new Intent(this,MijiaLoginActivity.class).putExtra("region",region),81);});if(authenticated){addOption("同步米家设备","所选地区须与米家 App 一致",AppUi.REFRESH,()->run(()->{MijiaCloud.refresh(this);return MijiaCloud.summary(this);}));addOption("退出米家账号","删除本机米家会话，保留首页模块选择",AppUi.OUT,()->new AlertDialog.Builder(this).setTitle("退出米家账号？").setMessage("常驻设备会保留为待登录状态，海尔账号不受影响。").setPositiveButton("退出",(d,w)->run(()->{MijiaCloud.logout(this);authenticated=false;return "已退出米家账号";})).setNegativeButton("取消",null).show());}}
        ui.space(content,12);note("地区选择在重新登录后生效，请与设备所在米家地区保持一致。设备读取与控制由家聚独立完成，无需安装米家或启用模块。新设备在更多设备中展示，可自行选择常驻首页。");
    }
    private interface Work{String run()throws Exception;}
    private void run(Work work){if(busy)return;busy=true;render();new Thread(()->{String result;try{result=work.run();}catch(Exception e){result=CloudDevices.error(e,"米家暂不可用，请稍后重试");}final String text=result;main.post(()->{busy=false;message=text;DeviceWidget.updateAll(this);if(!isDestroyed())render();});},"MijiaAccount").start();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK){authenticated=true;run(()->{MijiaCloud.refresh(this);return MijiaCloud.summary(this);});}}
}
