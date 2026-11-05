package dev.homecontrol.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Homepage selection and three independent orders, matching the fusion card model. */
public final class DisplayActivity extends AppPage {
    private DisplayPreferences preferences;
    private String tab=DevicePresentation.HOME;
    private DevicePresentation model;
    private final LinkedHashMap<String,JSONObject> devices=new LinkedHashMap<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);preferences=new DisplayPreferences(this);if(state!=null)tab=state.getString("tab",DevicePresentation.HOME);render();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);}
    private void changed(){DeviceWidget.updateAll(this);int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}
    private String title(String id){return DevicePresentation.HIDDEN.equals(id)?"隐藏设备":devices.containsKey(id)?devices.get(id).optString("title"):"暂不可用设备";}
    private void render(){
        JSONArray snapshot=DeviceStore.devices(this);model=preferences.layout(snapshot);devices.clear();for(int i=0;i<snapshot.length();i++){JSONObject d=snapshot.optJSONObject(i);if(d!=null)devices.put(d.optString("id"),d);}List<String> ids=new ArrayList<>(devices.keySet());
        page("展示设置",this::finish);LinearLayout tabs=ui.row();String[] keys={DevicePresentation.HOME,DevicePresentation.ALL,DevicePresentation.CONCEALED},labels={"首页展示","全部排序","隐藏设备"};
        for(int i=0;i<keys.length;i++){String key=keys[i];boolean selected=key.equals(tab);TextView v=ui.text(labels[i],13,selected?ui.background:ui.text,true);v.setGravity(Gravity.CENTER);v.setBackground(ui.ripple(selected?ui.accent:ui.surface,14));v.setSelected(selected);v.setOnClickListener(w->{tab=key;render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(44),1);if(i>0)p.leftMargin=ui.dp(8);tabs.addView(v,p);}content.addView(tabs);ui.space(content,20);
        if(DevicePresentation.HOME.equals(tab)){
            List<String> home=model.home(ids);content.addView(ui.text("首页常驻 · "+home.size()+" 个模块",17,ui.text,true));ui.space(content,8);note("勾选哪些模块，首页就展示哪些模块，数量不限。用上下箭头调整顺序。");ui.space(content,14);rows(home,true,true);
            ui.space(content,20);content.addView(ui.text("添加到首页",15,ui.text,true));ui.space(content,12);List<String> available=model.all(ids);available.removeAll(home);rows(available,true,false);
            note("新设备加入全部设备列表；首页展示由你选择。隐藏设备栏目也可以常驻首页。");
        }else if(DevicePresentation.ALL.equals(tab)){
            note("更多设备包含首页设备。这里的排序独立保存，不影响首页顺序。");ui.space(content,14);rows(model.all(ids),false,true);
        }else{
            note("隐藏设备收纳在默认折叠的栏目中，可独立排序或取消隐藏。");ui.space(content,14);List<String> hidden=model.concealed(ids);if(hidden.isEmpty())note("暂无隐藏设备。可在全部排序中点击设备的管理按钮。");else rows(hidden,false,true);
        }
    }
    private void rows(List<String> ids,boolean selectable,boolean sortable){for(int index=0;index<ids.size();index++){
        String id=ids.get(index);int position=index;LinearLayout row=ui.row();row.setPadding(ui.dp(10),ui.dp(8),ui.dp(8),ui.dp(8));row.setMinimumHeight(ui.dp(64));row.setBackground(ui.shape(ui.surface,20));
        if(selectable){CheckBox check=new CheckBox(this);check.setChecked(model.pinned.contains(id));check.setContentDescription("首页展示"+title(id));row.addView(check,new LinearLayout.LayoutParams(ui.dp(38),ui.dp(44)));check.setOnCheckedChangeListener((v,value)->{preferences.pin(id,value);changed();});}
        TextView name=ui.text(title(id),14,ui.text,true);name.setMaxLines(2);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(!selectable)row.addView(small("管理"+title(id),AppUi.MORE,()->manage(id)));
        if(sortable){View up=small("上移"+title(id),AppUi.UP,()->{if(position>0){preferences.move(tab,id,ids.get(position-1));changed();}});up.setEnabled(position>0);up.setAlpha(position>0?1:.25f);row.addView(up);View down=small("下移"+title(id),AppUi.DOWN,()->{if(position+1<ids.size()){preferences.move(tab,id,ids.get(position+1));changed();}});down.setEnabled(position+1<ids.size());down.setAlpha(position+1<ids.size()?1:.25f);row.addView(down);}
        content.addView(row);ui.space(content,8);
    }}
    private View small(String label,int icon,Runnable run){View v=iconButton(label,icon,run);v.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(34),ui.dp(44)));return v;}
    private void manage(String id){boolean group=DevicePresentation.HIDDEN.equals(id);new AlertDialog.Builder(this).setTitle(title(id)).setItems(group?new String[]{model.pinned.contains(id)?"取消首页常驻":"常驻首页"}:new String[]{model.hidden.contains(id)?"取消隐藏":"隐藏设备"},(d,w)->{if(group)preferences.pin(id,!model.pinned.contains(id));else preferences.hide(id,!model.hidden.contains(id));changed();}).setNegativeButton("关闭",null).show();}
}
