package dev.homecontrol.app;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
/** Private standalone cloud-device store. No MiLink resolver or module dependency. */
public final class DeviceStore {
    private static Cache helper;
    private static synchronized SQLiteDatabase db(Context c){if(helper==null)helper=new Cache(c.getApplicationContext());return helper.getWritableDatabase();}
    static String preference(Context c,String key,String fallback){try(Cursor cursor=db(c).query("settings",new String[]{"value"},"key=?",new String[]{key},null,null,null)){return cursor.moveToFirst()?cursor.getString(0):fallback;}}
    static void putPreference(Context c,String key,String value){ContentValues row=new ContentValues();row.put("key",key);row.put("value",value);db(c).insertWithOnConflict("settings",null,row,SQLiteDatabase.CONFLICT_REPLACE);}
    public static JSONArray devices(Context c){try{
        JSONArray list=new JSONArray();Set<String> seen=new HashSet<>();for(String provider:new String[]{"haier","mijia"}){JSONArray source=new JSONArray(preference(c,provider+"_devices","[]"));long time=Long.parseLong(preference(c,provider+"_sync_time","0"));for(int i=0;i<source.length();i++){JSONObject d=source.getJSONObject(i);if(!seen.add(d.optString("id")))continue;if(System.currentTimeMillis()-time>60000)d.put("stale",true);list.put(d);}}
        JSONObject remembered=new JSONObject(preference(c,"remembered_devices","{}")),layout=new JSONObject(preference(c,"layout_v3","{}"));for(String key:new String[]{"pinned","hidden"}){JSONArray ids=layout.optJSONArray(key);if(ids==null)continue;for(int i=0;i<ids.length();i++){String id=ids.optString(i);if(seen.add(id)&&!DevicePresentation.HIDDEN.equals(id)){JSONObject old=remembered.optJSONObject(id);if(old!=null)list.put(new JSONObject(old.toString()).put("stale",true).put("state",0).put("subtitle",id.startsWith("mijia:")&&!"ready".equals(preference(c,"mijia_auth_state","logged_out"))?"请在设置中登录米家":"暂时无法操作 · 点击刷新"));}}}return list;
    }catch(Exception e){return new JSONArray();}}
    static void remember(Context c,JSONArray source){transaction(c,()->{try{JSONObject saved=new JSONObject(preference(c,"remembered_devices","{}"));for(int i=0;i<source.length();i++){JSONObject d=source.getJSONObject(i),copy=new JSONObject();for(String key:new String[]{"id","title","deviceType","category","subtitle","icon","battery","updateTime","source","model"})copy.put(key,d.opt(key));saved.put(d.optString("id"),copy);}putPreference(c,"remembered_devices",saved.toString());return null;}catch(JSONException e){throw new IllegalStateException(e);}});}
    static String operation(Context c,String id){return preference(c,"operation:"+id,"");}
    static void operation(Context c,String id,String message){putPreference(c,"operation:"+id,message);DeviceWidget.updateAll(c);}
    static <T>T transaction(Context c,java.util.function.Supplier<T> work){SQLiteDatabase d=db(c);d.beginTransaction();try{T result=work.get();d.setTransactionSuccessful();return result;}finally{d.endTransaction();}}
    private static final class Cache extends SQLiteOpenHelper {
        Cache(Context c){super(c,"home-control.db",null,1);}
        @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
        @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported cache version");}
    }
}
