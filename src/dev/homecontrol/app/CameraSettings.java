package dev.homecontrol.app;
import org.json.*;
import java.util.*;
/** Common image controls only; no generic diagnostics/storage/self-test property dump. */
final class CameraSettings {
 static final String[] KEYS={"p:2:3","p:2:2","p:2:9","p:3:1"};
 static String title(String key){switch(key){case "p:2:3":return "夜视模式";case "p:2:2":return "画面翻转";case "p:2:9":return "时间水印";case "p:3:1":return "状态指示灯";default:return "";}}
 static JSONArray options(JSONObject p){JSONArray out=new JSONArray();try{if("bool".equals(p.optString("format")))return out.put(new JSONObject().put("description","关闭").put("value",false)).put(new JSONObject().put("description","开启").put("value",true));JSONArray choices=p.optJSONArray("choices");if(choices!=null&&choices.length()>0)return choices;JSONArray range=p.optJSONArray("range");if("p:2:2".equals(p.optString("key"))&&range!=null&&range.length()==3&&range.optInt(0)==0&&range.optInt(1)==180&&range.optInt(2)==180)return out.put(new JSONObject().put("description","正常").put("value",0)).put(new JSONObject().put("description","翻转180°").put("value",180));}catch(JSONException ignored){}return out;}
 static JSONArray items(JSONObject device){JSONArray out=new JSONArray();if(!CameraPolicy.supported(device.optString("model")))return out;JSONArray props=device.optJSONArray("capabilities");for(String key:KEYS){JSONObject p=MijiaModel.find(props,key);if(p!=null&&p.optBoolean("writable")&&p.optBoolean("readable"))out.put(p);}return out;}
}
