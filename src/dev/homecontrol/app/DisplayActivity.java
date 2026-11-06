package dev.homecontrol.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Editing filters are independent of the user's flat, curated homepage. */
public final class DisplayActivity extends AppPage {
    private DisplayPreferences preferences;private DevicePresentation model;private String platform="all";
    private final LinkedHashMap<String,JSONObject> devices=new LinkedHashMap<>();
    private final Map<String,Boolean> folds=new HashMap<>();private final Map<String,String> homes=new HashMap<>(),rooms=new HashMap<>();
    private ReorderDrag drag;
    @Override public void onCreate(Bundle state){super.onCreate(state);preferences=new DisplayPreferences(this);platform=DeviceStore.preference(this,"editor_platform","all");
        for(String p:new String[]{"mijia","haier"}){homes.put(p,DeviceStore.preference(this,"editor_home:"+p,DeviceLocation.ANY));rooms.put(p,DeviceStore.preference(this,"editor_room:"+p,DeviceLocation.ANY));}
        for(String key:new String[]{"selected","hidden","excluded"})folds.put(key,"1".equals(DeviceStore.preference(this,"editor_fold:"+key,"0")));
        drag=new ReorderDrag(ui,(source,target)->{preferences.move(DevicePresentation.HOME,source,target);changed();},()->{},()->{});render();}
    @Override public void finish(){setResult(RESULT_OK);super.finish();}
    @Override protected void onResume(){super.onResume();if(preferences!=null)render();}
    private void changed(){DeviceWidget.updateAll(this);int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}
    private FoldView fold(String key,String title,String detail,int icon){return new FoldView(this,ui,title,detail,icon,Boolean.TRUE.equals(folds.get(key)),open->{folds.put(key,open);DeviceStore.putPreference(this,"editor_fold:"+key,open?"1":"0");});}
    private String title(String id){return DevicePresentation.HIDDEN.equals(id)?"隐藏设备":devices.containsKey(id)?devices.get(id).optString("title"):"暂不可用设备";}
    private View icon(String id){if(DevicePresentation.HIDDEN.equals(id))return ui.badge(AppUi.HIDDEN);ImageView image=new ImageView(this);image.setImageBitmap(DeviceIcons.make(this,devices.get(id),ui.accent));image.setPadding(ui.dp(5),ui.dp(5),ui.dp(5),ui.dp(5));image.setBackground(ui.shape(ui.pale,11));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
    private LinearLayout words(String id){LinearLayout words=ui.column();TextView name=ui.text(title(id),14,ui.text,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);TextView meta=ui.text(devices.containsKey(id)?DeviceLocation.meta(devices.get(id)):"折叠栏目",11,ui.muted,false);meta.setMaxLines(2);words.addView(meta);return words;}
    private LinearLayout row(String id,int fill){LinearLayout row=ui.row();row.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));row.setMinimumHeight(ui.dp(64));row.setBackground(ui.shape(fill,16));row.setContentDescription(title(id));row.addView(icon(id),new LinearLayout.LayoutParams(ui.dp(34),ui.dp(34)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=ui.dp(10);p.rightMargin=ui.dp(8);row.addView(words(id),p);return row;}
    private void editRow(LinearLayout parent,String id,int fill){LinearLayout row=row(id,fill);row.addView(new ChoiceView(this,ui,title(id)+"展示方式",ChoiceView.placements(false),model.placement(id),92,value->{preferences.place(id,value);if("hidden".equals(value))folds.put("hidden",true);if("excluded".equals(value))folds.put("excluded",true);changed();}));parent.addView(row);ui.space(parent,8);}
    private void render(){
        JSONArray snapshot=DeviceStore.devices(this);model=preferences.layout(snapshot);devices.clear();for(int i=0;i<snapshot.length();i++){JSONObject d=snapshot.optJSONObject(i);if(d!=null)devices.put(d.optString("id"),d);}List<String> ids=new ArrayList<>(devices.keySet());
        page("编辑设备",this::finish);LinearLayout heading=(LinearLayout)content.getChildAt(0);TextView done=ui.text("完成",13,ui.accent,true);done.setGravity(Gravity.CENTER);done.setContentDescription("完成编辑设备");done.setOnClickListener(v->finish());heading.addView(done,new LinearLayout.LayoutParams(ui.dp(50),ui.dp(44)));
        List<String> home=model.home(ids);FoldView chosen=fold("selected","首页已选",home.size()+" 项 · 拖动排序",AppUi.DRAG);TextView instruction=ui.text("拖住右侧图标调整顺序",12,ui.muted,false);chosen.body.addView(instruction);ui.space(chosen.body,10);
        for(String id:home){LinearLayout row=row(id,ui.pale);View handle=ui.icon(AppUi.DRAG,ui.accent,22);FrameLayout touch=new FrameLayout(this);touch.addView(handle,new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));row.addView(touch,new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));drag.handle(touch,row,id);drag.target(row,id);drag.accessible(touch,id,home);chosen.body.addView(row);ui.space(chosen.body,8);}if(home.isEmpty())chosen.body.addView(ui.text("尚未选择首页设备",13,ui.muted,false));content.addView(chosen);ui.space(content,22);
        content.addView(ui.text("选择设备",16,ui.text,true));ui.space(content,10);LinearLayout tabs=ui.row();for(String key:new String[]{"all","mijia","haier"}){TextView chip=ui.text("all".equals(key)?"全部平台":DeviceLocation.providerName(key),13,key.equals(platform)?ui.background:ui.muted,true);chip.setPadding(ui.dp(12),0,ui.dp(12),0);chip.setGravity(Gravity.CENTER);chip.setBackground(ui.ripple(key.equals(platform)?ui.accent:ui.surface,12));chip.setSelected(key.equals(platform));chip.setOnClickListener(v->{platform=key;DeviceStore.putPreference(this,"editor_platform",key);changed();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,ui.dp(44));p.rightMargin=ui.dp(6);tabs.addView(chip,p);}View accounts=iconButton("接入平台与账号",AppUi.ADD,()->startActivity(new Intent(this,SettingsActivity.class).putExtra("screen","accounts")));tabs.addView(accounts);content.addView(tabs);ui.space(content,14);
        for(String provider:new String[]{"mijia","haier"})if("all".equals(platform)||provider.equals(platform))providerSection(provider);
        View divider=new View(this);divider.setBackgroundColor(ui.line);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,ui.dp(1));line.topMargin=ui.dp(12);line.bottomMargin=ui.dp(18);content.addView(divider,line);
        List<String> hidden=model.concealed(ids);FoldView hide=fold("hidden","隐藏设备栏目",hidden.size()+" 台",AppUi.HIDDEN);LinearLayout place=ui.row();place.addView(ui.text("栏目展示位置",13,ui.text,false),new LinearLayout.LayoutParams(0,-2,1));place.addView(new ChoiceView(this,ui,"隐藏设备栏目展示方式",ChoiceView.placements(true),model.placement(DevicePresentation.HIDDEN),92,value->{preferences.place(DevicePresentation.HIDDEN,value);changed();}));hide.body.addView(place);ui.space(hide.body,10);hide.body.addView(ui.text(hidden.isEmpty()?"暂无隐藏设备，首页和更多中不显示栏目入口。":"设备默认折叠，点开栏目后访问。",12,ui.muted,false));ui.space(hide.body,10);for(String id:hidden)editRow(hide.body,id,ui.pale);content.addView(hide);ui.space(content,12);
        List<String> excluded=model.excluded(ids);FoldView off=fold("excluded","不展示设备",excluded.size()+" 台",AppUi.EYE_OFF);off.body.addView(ui.text("仅在编辑页管理，不出现在首页、更多或隐藏设备中。",12,ui.muted,false));ui.space(off.body,10);for(String id:excluded)editRow(off.body,id,ui.pale);if(excluded.isEmpty())off.body.addView(ui.text("暂无不展示的设备",12,ui.muted,false));content.addView(off);
    }
    private void providerSection(String provider){
        String home=homes.get(provider),room=rooms.get(provider);LinkedHashMap<String,String> homeOptions=DeviceLocation.homes(devices.values(),provider);if(!homeOptions.containsKey(home)){home=DeviceLocation.ANY;homes.put(provider,home);}LinkedHashMap<String,String> roomOptions=DeviceLocation.rooms(devices.values(),provider,home);if(!roomOptions.containsKey(room)){room=DeviceLocation.ANY;rooms.put(provider,room);}
        LinearLayout header=ui.row();TextView name=ui.text(DeviceLocation.providerName(provider),15,ui.text,true);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));header.addView(new ChoiceView(this,ui,DeviceLocation.providerName(provider)+"家庭",homeOptions,home,100,value->{homes.put(provider,value);rooms.put(provider,DeviceLocation.ANY);DeviceStore.putPreference(this,"editor_home:"+provider,value);DeviceStore.putPreference(this,"editor_room:"+provider,DeviceLocation.ANY);changed();}));View gap=new View(this);header.addView(gap,new LinearLayout.LayoutParams(ui.dp(6),1));header.addView(new ChoiceView(this,ui,DeviceLocation.providerName(provider)+"房间",roomOptions,room,100,value->{rooms.put(provider,value);DeviceStore.putPreference(this,"editor_room:"+provider,value);changed();}));content.addView(header);ui.space(content,10);
        boolean any=false;for(Map.Entry<String,JSONObject> entry:devices.entrySet()){String id=entry.getKey();if(!model.hidden.contains(id)&&!model.excluded.contains(id)&&DeviceLocation.matches(entry.getValue(),provider,home,room)){editRow(content,id,ui.surface);any=true;}}
        if(!any){note("此分类暂无可展示设备");ui.space(content,10);}String status="mijia".equals(provider)?DeviceStore.preference(this,"mijia_location_status",""):"";if(!status.isEmpty()){note(status);ui.space(content,8);}ui.space(content,12);
    }
}
