package dev.homecontrol.app;
import org.json.*;
import java.util.*;
/** Common image controls only; no generic diagnostics/storage/self-test property dump. */
final class CameraSettings {
 static final String[] NAMES={"night-shot","image-rollover","time-watermark","on"};
 static String[] keys(String model){if("xiaomi.camera.c302".equals(model))return new String[]{"p:2:3","p:2:4","p:2:5","p:4:1"};if("chuangmi.camera.069a01".equals(model)||"chuangmi.camera.039c01".equals(model))return new String[]{"p:2:3","p:2:2","p:2:9","p:3:1"};return new String[0];}
 static String title(JSONObject p){switch(p.optString("name")){case "night-shot":return "夜视模式";case "image-rollover":return "画面翻转";case "time-watermark":return "时间水印";case "on":return "状态指示灯";default:return "";}}
 static JSONArray options(JSONObject p){JSONArray out=new JSONArray();try{if("bool".equals(p.optString("format")))return out.put(new JSONObject().put("description","关闭").put("value",false)).put(new JSONObject().put("description","开启").put("value",true));JSONArray choices=p.optJSONArray("choices");if(choices!=null&&choices.length()>0)return choices;JSONArray range=p.optJSONArray("range");if("image-rollover".equals(p.optString("name"))&&range!=null&&range.length()==3&&range.optInt(0)==0){int max=range.optInt(1),step=range.optInt(2);if(max==180&&step==180||max==270&&step==90){for(int angle=0;angle<=max;angle+=step)out.put(new JSONObject().put("description",angle==0?"正常":angle==180?"翻转180°":"旋转"+angle+"°").put("value",angle));return out;}}}catch(JSONException ignored){}return out;}
 static JSONArray items(JSONObject device){JSONArray out=new JSONArray();String[] keys=keys(device.optString("model"));JSONArray props=device.optJSONArray("capabilities");for(int i=0;i<keys.length;i++){JSONObject p=MijiaModel.find(props,keys[i]);if(p!=null&&NAMES[i].equals(p.optString("name"))&&p.optBoolean("writable")&&p.optBoolean("readable"))out.put(p);}return out;}
}
