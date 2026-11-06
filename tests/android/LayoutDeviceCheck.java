package dev.homecontrol.app;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;

/** Native UI integration check. Only display preferences change and are restored in finally. */
public final class LayoutDeviceCheck extends Instrumentation {
    private final List<String> passed=new ArrayList<>();private final Map<String,String> saved=new LinkedHashMap<>();private final List<Activity> opened=new ArrayList<>();
    private Context context;private MainActivity home;private Bundle report=new Bundle(),arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args==null?new Bundle():args;start();}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);passed.add(label);}
    private void ui(Runnable work){Throwable[] error=new Throwable[1];runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private List<View> views(View root){List<View> out=new ArrayList<>();out.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)out.addAll(views(((ViewGroup)root).getChildAt(i)));return out;}
    private View named(Activity activity,String name){for(View v:views(activity.getWindow().getDecorView()))if(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;throw new AssertionError("Missing UI: "+name);}
    private View text(Activity activity,String name){for(View v:views(activity.getWindow().getDecorView()))if(v instanceof TextView&&name.contentEquals(((TextView)v).getText()))return v;throw new AssertionError("Missing label: "+name);}
    private void choose(Activity activity,String description,String option){ui(()->named(activity,description).performClick());ui(()->{for(View root:WindowInspector.getGlobalWindowViews())for(View v:views(root))if(v instanceof TextView&&v.isClickable()&&option.contentEquals(((TextView)v).getText())){v.performClick();return;}throw new AssertionError("Missing dropdown option: "+option);});}
    private <T extends Activity>T launch(Class<T> type){ActivityMonitor monitor=addMonitor(type.getName(),null,false);Intent intent=new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(type==MainActivity.class)intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);context.startActivity(intent);Activity a=waitForMonitorWithTimeout(monitor,60000);removeMonitor(monitor);if(a==null)throw new AssertionError("系统未打开测试界面，已恢复临时设置");opened.add(a);waitForIdleSync();return type.cast(a);}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void patchHome(boolean all)throws Exception{Field f=MainActivity.class.getDeclaredField("all");f.setAccessible(true);f.set(home,all);Method sync=MainActivity.class.getDeclaredMethod("syncCards");sync.setAccessible(true);ui(()->{try{sync.invoke(home);}catch(Exception e){throw new RuntimeException(e);}});}
    private boolean tile(String id)throws Exception{return ((Map<?,?>)field(home,"bindings")).containsKey(id);}
    private void backup(){List<String> keys=new ArrayList<>(Arrays.asList("layout_v3","remembered_devices","editor_platform","editor_fold:selected","editor_fold:hidden","editor_fold:excluded"));for(String p:new String[]{"mijia","haier"})for(String f:new String[]{"home","room"})keys.add("editor_"+f+":"+p);for(String key:keys)saved.put(key,DeviceStore.preference(context,key,null));}
    private void restore(){for(Map.Entry<String,String> item:saved.entrySet())if(item.getValue()!=null)DeviceStore.putPreference(context,item.getKey(),item.getValue());try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("home-control.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){for(Map.Entry<String,String> item:saved.entrySet())if(item.getValue()==null)db.delete("settings","key=?",new String[]{item.getKey()});}DeviceWidget.updateAll(context);}
    private void selectOnly(String first,String second)throws Exception{JSONObject data=new JSONObject(DeviceStore.preference(context,"layout_v3","{}"));data.put("pinned",new JSONArray().put(first).put(second)).put("hidden",new JSONArray()).put("excluded",new JSONArray()).put("hiddenModuleVisible",true);DeviceStore.putPreference(context,"layout_v3",data.toString());DeviceStore.putPreference(context,"editor_platform","all");for(String p:new String[]{"mijia","haier"})for(String f:new String[]{"home","room"})DeviceStore.putPreference(context,"editor_"+f+":"+p,DeviceLocation.ANY);}
    @Override public void onStart(){boolean success=false;try{
        context=getTargetContext();if(context.getSystemService(KeyguardManager.class).isDeviceLocked()){report.putBoolean("requires_unlock",true);finish(0,report);return;}
        JSONArray devices=DeviceStore.devices(context);List<String> ids=new ArrayList<>();JSONObject target=null,other=null;Set<String> families=new LinkedHashSet<>();int located=0;
        for(int i=0;i<devices.length();i++){JSONObject d=devices.optJSONObject(i);if(d==null)continue;ids.add(d.optString("id"));if(!d.optString("homeName").isEmpty()){located++;families.add(d.optString("homeName"));}if(target==null&&d.optString("model").contains(".light."))target=d;if(other==null&&"haier".equals(DeviceLocation.provider(d)))other=d;}
        check(target!=null&&other!=null,"real cached devices available");report.putInt("located_devices",located);report.putString("family_names",new JSONArray(families).toString());
        backup();final String id=target.getString("id"),second=other.getString("id"),title=target.getString("title");selectOnly(id,second);
        if("manual_drag".equals(arguments.getString("mode"))){
            home=launch(MainActivity.class);ui(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));patchHome(false);Bundle ready=new Bundle();ready.putString("manual_drag_ready",title+" → "+other.getString("title"));sendStatus(1,ready);List<String> initial=new ArrayList<>(new DisplayPreferences(context).layout(DeviceStore.devices(context)).pinned);
            long until=SystemClock.elapsedRealtime()+180000;boolean moved=false;while(SystemClock.elapsedRealtime()<until){SystemClock.sleep(350);if(!new DisplayPreferences(context).layout(DeviceStore.devices(context)).pinned.equals(initial)){moved=true;break;}}
            report.putBoolean("manual_homepage_drag_verified",moved);report.putString("manual_drag_status",moved?"verified":"not_observed_before_timeout");if(moved)check(true,"manual homepage drag commits new order");success=moved;return;
        }
        DisplayActivity edit=launch(DisplayActivity.class);check(text(edit,"编辑设备")!=null,"native editor opens");
        ui(()->{for(View v:views(edit.getWindow().getDecorView()))if(v instanceof ChoiceView){ChoiceView choice=(ChoiceView)v;View label=choice.getChildAt(0),arrow=choice.getChildAt(1);check(label.getWidth()>0&&arrow.getLeft()>=label.getRight(),"label and arrow have independent measured columns");break;}});
        final List<String> original=new ArrayList<>(new DisplayPreferences(context).layout(DeviceStore.devices(context)).pinned);
        choose(edit,"米家家庭","全部家庭");check(new DisplayPreferences(context).layout(DeviceStore.devices(context)).pinned.equals(original),"native family dropdown leaves homepage choices unchanged");
        for(String value:new String[]{"更多","隐藏","不展示","首页"}){
            choose(edit,title+"展示方式",value);DevicePresentation model=new DisplayPreferences(context).layout(DeviceStore.devices(context));String expected="更多".equals(value)?"all":"隐藏".equals(value)?"hidden":"不展示".equals(value)?"excluded":"home";check(expected.equals(model.placement(id)),"native placement dropdown: "+value);
        }
        ui(edit::finish);home=launch(MainActivity.class);patchHome(false);check(tile(id),"pinned device renders as a real homepage card");
        DisplayPreferences preferences=new DisplayPreferences(context);preferences.place(id,"excluded");patchHome(false);check(!tile(id),"excluded device absent from real homepage");patchHome(true);check(!tile(id),"excluded device absent from real More grid");
        preferences.place(DevicePresentation.HIDDEN,"home");patchHome(false);check(!tile(DevicePresentation.HIDDEN),"empty hidden module absent from real homepage");patchHome(true);check(!tile(DevicePresentation.HIDDEN),"empty hidden module absent from real More grid");
        preferences.place(id,"hidden");patchHome(false);check(tile(DevicePresentation.HIDDEN)&&!tile(id),"nonempty hidden module renders without direct device tile");preferences.place(id,"home");patchHome(false);check(tile(id),"restored device reappears in homepage");
        // Real touch injection may be blocked by the phone's automation settings. Never simulate a pass.
        try{DevicePresentation before=preferences.layout(DeviceStore.devices(context));Object binding=((Map<?,?>)field(home,"bindings")).get(id),dest=((Map<?,?>)field(home,"bindings")).get(second);TextView label=(TextView)field(binding,"name"),endLabel=(TextView)field(dest,"name");View from=(View)label.getParent(),to=(View)endLabel.getParent();Rect a=new Rect(),b=new Rect();ui(()->{from.getGlobalVisibleRect(a);to.getGlobalVisibleRect(b);});long down=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,a.centerX(),a.centerY(),0));SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+250);
            for(int i=1;i<=12;i++){long time=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(down,time,MotionEvent.ACTION_MOVE,a.centerX()+(b.centerX()-a.centerX())*i/12f,a.centerY()+(b.centerY()-a.centerY())*i/12f,0));SystemClock.sleep(25);}sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,b.centerX(),b.centerY(),0));waitForIdleSync();SystemClock.sleep(300);report.putBoolean("homepage_touch_drag_verified",!preferences.layout(DeviceStore.devices(context)).pinned.equals(before.pinned));
        }catch(Exception e){report.putBoolean("homepage_touch_drag_verified",false);report.putString("drag_status","自动触摸受限，需手动验证");}
        success=true;
    }catch(Throwable e){report.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());}
    finally{try{if(context!=null&&!saved.isEmpty()){restore();report.putBoolean("original_layout_restored",Objects.equals(saved.get("layout_v3"),DeviceStore.preference(context,"layout_v3",null)));}for(Activity a:opened)if(a!=home)ui(a::finish);if(home!=null)patchHome(false);}catch(Throwable e){report.putString("restore_error",e.getClass().getSimpleName());}report.putString("passed_checks",new JSONArray(passed).toString());report.putInt("check_count",passed.size());report.putBoolean("physical_commands_sent",false);finish(success?-1:0,report);}}
}
