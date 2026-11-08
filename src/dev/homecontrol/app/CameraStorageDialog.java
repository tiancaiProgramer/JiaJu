package dev.homecontrol.app;
import android.app.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** Opens over the current video, without recreating its session. */
final class CameraStorageDialog {
    private final Activity owner;private final AppUi ui;private final String id;private JSONObject device;private TextView feedback;private LinearLayout rows;private Dialog sheet;private boolean busy;
    CameraStorageDialog(Activity owner,AppUi ui,String id,JSONObject device){this.owner=owner;this.ui=ui;this.id=id;this.device=device;}
    void show(){LinearLayout body=ui.column();rows=ui.column();body.addView(rows);ui.space(body,16);TextView nas=AppSheet.button(owner,ui,"NAS 存储访问设置",false);nas.setOnClickListener(v->nas());body.addView(nas,new LinearLayout.LayoutParams(-1,ui.dp(52)));ui.space(body,10);feedback=ui.text("",12,ui.muted,false);body.addView(feedback);sheet=AppSheet.show(owner,"存储设置",body,null,null);patch();refresh();}
    private void patch(){rows.removeAllViews();JSONArray props=CameraStorage.items(device);for(int i=0;i<props.length();i++){JSONObject p=props.optJSONObject(i);if(p==null)continue;TextView row=ui.text(CameraStorage.title(p)+"  "+CameraStorage.value(p),14,ui.text,false);row.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));row.setContentDescription(CameraStorage.title(p));row.setBackground(ui.ripple(ui.background,12));if("recording-mode".equals(p.optString("name"))&&p.optBoolean("writable"))row.setOnClickListener(v->edit(p));rows.addView(row,new LinearLayout.LayoutParams(-1,-2));ui.space(rows,6);}if(props.length()==0)rows.addView(ui.text("暂未读取到存储卡信息",14,ui.muted,false));}
    private void refresh(){if(busy)return;busy=true;new Thread(()->{JSONObject fresh=null;String error="";try{fresh=MijiaCloud.readDevice(owner,id);}catch(Exception e){error="读取失败，保留上次状态";}JSONObject next=fresh;String message=error;owner.runOnUiThread(()->{busy=false;if(!sheet.isShowing())return;if(next!=null)device=next;patch();feedback.setText(message);});},"JiaJuStorageRead").start();}
    private void edit(JSONObject p){if(busy)return;LinearLayout body=ui.column();JSONArray options=p.optJSONArray("choices");if(options==null)return;final Dialog[] picker=new Dialog[1];for(int i=0;i<options.length();i++){JSONObject option=options.optJSONObject(i);if(option==null)continue;TextView choice=AppSheet.button(owner,ui,option.optString("description"),false);choice.setOnClickListener(v->{picker[0].dismiss();write(p,option.opt("value"));});body.addView(choice,new LinearLayout.LayoutParams(-1,ui.dp(50)));ui.space(body,6);}picker[0]=AppSheet.show(owner,"录制模式",body,null,null);}
    private void write(JSONObject p,Object value){if(busy||!MijiaModel.valid(p,value))return;if(owner.getSystemService(KeyguardManager.class).isDeviceLocked()){feedback.setText("请先解锁手机");return;}busy=true;new Thread(()->{String result;try{JSONArray values=new JSONArray().put(value);String encoded=values.toString();result=MijiaCloud.control(owner,id,p.optString("key"),encoded.substring(1,encoded.length()-1));}catch(Exception e){result="设置未确认，不会自动重发";}String text=result;owner.runOnUiThread(()->{busy=false;if(!sheet.isShowing())return;feedback.setText(text);refresh();});},"JiaJuStorageWrite").start();}
    private void nas(){new CameraNasDialog(owner,ui,id).show();}
}
