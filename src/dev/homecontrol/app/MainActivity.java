package dev.homecontrol.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.lang.ref.WeakReference;

public final class MainActivity extends AppPage {
    private static WeakReference<MainActivity> visible=new WeakReference<>(null);
    private DisplayPreferences preferences;
    private boolean foreground,loading,all,hiddenExpanded;
    private LinearLayout grid;
    private TextView section,status,more;
    private String structure="";
    private final Map<String,CardBinding> bindings=new LinkedHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable poll=()->{if(foreground){refresh();schedule();}};
    @Override public void onCreate(Bundle state){super.onCreate(state);preferences=new DisplayPreferences(this);if(state!=null){all=state.getBoolean("all");hiddenExpanded=state.getBoolean("hidden");}readWidgetIntent(getIntent());buildHome();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readWidgetIntent(intent);syncCards();}
    private void readWidgetIntent(Intent intent){if(intent.getBooleanExtra("showHidden",false)){all=true;hiddenExpanded=true;}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("all",all);state.putBoolean("hidden",hiddenExpanded);}
    @Override protected void onResume(){super.onResume();visible=new WeakReference<>(this);foreground=true;syncCards();refresh();schedule();}
    @Override protected void onPause(){foreground=false;main.removeCallbacks(poll);super.onPause();}
    private void schedule(){main.removeCallbacks(poll);if(foreground)main.postDelayed(poll,30000);}
    static void notifyChanged(){MainActivity a=visible.get();if(a!=null)a.main.post(()->{if(!a.isDestroyed()&&a.foreground)a.syncCards();});}
    private void buildHome(){
        LinearLayout root=ui.column();root.setBackgroundColor(ui.background);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        scroll=new ScrollView(this);content=ui.column();content.setPadding(ui.dp(20),ui.dp(24),ui.dp(20),ui.dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        content.addView(ui.text("家聚",30,ui.text,true));ui.space(content,6);content.addView(ui.text("让常用设备触手可及",14,ui.muted,false));ui.space(content,24);
        LinearLayout heading=ui.row();section=ui.text("常用设备",21,ui.text,true);heading.addView(section,new LinearLayout.LayoutParams(0,-2,1));
        TextView display=ui.text("展示设置",13,ui.accent,true);display.setGravity(Gravity.CENTER);display.setPadding(ui.dp(10),0,ui.dp(10),0);display.setBackground(ui.ripple(ui.surface,14));display.setOnClickListener(v->startActivity(new Intent(this,DisplayActivity.class)));heading.addView(display,new LinearLayout.LayoutParams(-2,ui.dp(44)));View spacer=new View(this);heading.addView(spacer,new LinearLayout.LayoutParams(ui.dp(8),1));heading.addView(iconButton("刷新家聚设备",AppUi.REFRESH,this::refresh));content.addView(heading);ui.space(content,12);
        grid=ui.column();content.addView(grid);ui.space(content,8);
        more=ui.text(all?"收起设备":"更多设备",15,ui.text,true);more.setGravity(Gravity.CENTER);more.setBackground(ui.ripple(ui.surface,22));more.setOnClickListener(v->{all=!all;hiddenExpanded=false;syncCards();});content.addView(more,new LinearLayout.LayoutParams(-1,ui.dp(52)));ui.space(content,10);
        content.addView(option("设置",null,AppUi.SETTINGS,()->startActivity(new Intent(this,SettingsActivity.class))));
        status=ui.text("",12,ui.muted,false);status.setPadding(ui.dp(4),ui.dp(14),ui.dp(4),0);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);syncCards();
    }
    /** Background state updates only touch existing labels, leaving buttons and scroll intact. */
    private void syncCards(){
        if(isDestroyed()||grid==null)return;
        JSONArray list=DeviceStore.devices(this);DevicePresentation model=preferences.layout(list);LinkedHashMap<String,JSONObject> devices=new LinkedHashMap<>();
        for(int i=0;i<list.length();i++){JSONObject d=list.optJSONObject(i);if(d!=null)devices.put(d.optString("id"),d);}
        List<String> ids=new ArrayList<>(devices.keySet()),shown=all?model.all(ids):model.home(ids),hidden=model.concealed(ids);
        List<JSONObject> cards=new ArrayList<>();for(String id:shown){if(DevicePresentation.HIDDEN.equals(id)){JSONObject group=new JSONObject();try{group.put("id",id).put("title","隐藏设备").put("deviceType","fusion_hidden").put("subtitle",hidden.size()+" 台 · "+(hiddenExpanded?"点击收起":"点击展开"));}catch(JSONException ignored){}cards.add(group);}else if(devices.containsKey(id))cards.add(devices.get(id));}
        List<JSONObject> concealed=new ArrayList<>();if(hiddenExpanded&&shown.contains(DevicePresentation.HIDDEN))for(String id:hidden)if(devices.containsKey(id))concealed.add(devices.get(id));
        String next=(all?"all":"home")+shown.toString()+"/"+concealedIds(concealed);
        section.setText(all?"全部设备":"常用设备");more.setText(all?"收起设备":"更多设备");
        if(!structure.equals(next)){
            structure=next;bindings.clear();grid.removeAllViews();addGrid(cards);
            if(cards.isEmpty()){TextView empty=ui.text(devices.isEmpty()?"还没有设备。请前往设置登录账号，读取设备后即可自由选择首页展示。":"尚未选择常驻模块。点展示设置勾选，或点更多设备查看全部。",14,ui.muted,false);empty.setPadding(ui.dp(16),ui.dp(18),ui.dp(16),ui.dp(18));empty.setBackground(ui.shape(ui.surface,22));grid.addView(empty);ui.space(grid,10);}
            if(!concealed.isEmpty()){ui.space(grid,6);grid.addView(ui.text("隐藏设备",14,ui.muted,true));ui.space(grid,10);addGrid(concealed);}
        }
        for(JSONObject d:cards)updateCard(d);for(JSONObject d:concealed)updateCard(d);
    }
    private String concealedIds(List<JSONObject> list){List<String> ids=new ArrayList<>();for(JSONObject d:list)ids.add(d.optString("id"));return ids.toString();}
    private void updateCard(JSONObject d){CardBinding b=bindings.get(d.optString("id"));if(b!=null){b.device=d;b.name.setText(d.optString("title"));b.state.setText(d.optString("subtitle"));}}
    private void gap(LinearLayout row){row.addView(new View(this),new LinearLayout.LayoutParams(ui.dp(10),1));}
    private void addGrid(List<JSONObject> cards){for(int i=0;i<cards.size();i+=2){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.addView(card(cards.get(i)),new LinearLayout.LayoutParams(0,ui.dp(144),1));gap(row);if(i+1<cards.size())row.addView(card(cards.get(i+1)),new LinearLayout.LayoutParams(0,ui.dp(144),1));else row.addView(new View(this),new LinearLayout.LayoutParams(0,0,1));grid.addView(row);ui.space(grid,10);}}
    private View card(JSONObject d){
        String id=d.optString("id");LinearLayout v=ui.column();v.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));v.setBackground(ui.ripple(ui.surface,22));v.setFocusable(true);v.setClickable(true);
        ImageView icon=new ImageView(this);icon.setImageBitmap(DeviceIcons.make(this,d,ui.accent));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);v.addView(icon,new LinearLayout.LayoutParams(ui.dp(37),ui.dp(37)));ui.space(v,10);
        TextView name=ui.text(d.optString("title"),15,ui.text,true);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);v.addView(name);ui.space(v,5);TextView state=ui.text(d.optString("subtitle"),12,ui.muted,false);state.setMaxLines(2);v.addView(state);CardBinding binding=new CardBinding(d,name,state);bindings.put(id,binding);
        v.setOnClickListener(view->{if(DevicePresentation.HIDDEN.equals(id)){hiddenExpanded=!hiddenExpanded;syncCards();}else startActivity(CloudDevices.panel(this,id));});v.setOnLongClickListener(view->{manage(binding.device);return true;});return v;
    }
    private static final class CardBinding {JSONObject device;final TextView name,state;CardBinding(JSONObject d,TextView name,TextView state){device=d;this.name=name;this.state=state;}}
    private void manage(JSONObject d){
        String id=d.optString("id");DevicePresentation model=preferences.layout(DeviceStore.devices(this));List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();boolean hidden=model.hidden.contains(id);
        if(!hidden){labels.add(model.pinned.contains(id)?"取消首页常驻":"常驻首页");actions.add(()->{preferences.pin(id,!model.pinned.contains(id));DeviceWidget.updateAll(this);syncCards();});}
        if(!DevicePresentation.HIDDEN.equals(id)){labels.add(hidden?"取消隐藏":"隐藏设备");actions.add(()->{preferences.hide(id,!hidden);DeviceWidget.updateAll(this);syncCards();});}
        labels.add("展示与排序");actions.add(()->startActivity(new Intent(this,DisplayActivity.class)));
        new AlertDialog.Builder(this).setTitle(d.optString("title")).setItems(labels.toArray(new String[0]),(dialog,index)->actions.get(index).run()).setNegativeButton("关闭",null).show();
    }
    private void refresh(){
        if(loading)return;loading=true;
        new Thread(()->{final String text=CloudDevices.refresh(this);main.post(()->{loading=false;if(isDestroyed())return;status.setText(text);DeviceStore.remember(this,DeviceStore.devices(this));DeviceWidget.updateAll(this);syncCards();});},"HomeControlRefresh").start();
    }
}
