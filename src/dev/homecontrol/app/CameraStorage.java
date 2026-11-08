package dev.homecontrol.app;
import org.json.*;
import java.util.*;

/** Storage IDs differ from image settings, especially on c302. */
final class CameraStorage {
    static JSONArray items(JSONObject device){JSONArray out=new JSONArray();if(!CameraPolicy.supported(device.optString("model")))return out;JSONArray props=device.optJSONArray("capabilities");if(props==null)return out;int siid="xiaomi.camera.c302".equals(device.optString("model"))?3:4;for(int i=0;i<props.length();i++){JSONObject p=props.optJSONObject(i);if(p==null||!p.optBoolean("readable"))continue;String name=p.optString("name");if(p.optInt("siid")==siid&&Arrays.asList("status","storage-total-space","storage-free-space","storage-used-space","remaining-recording-duration").contains(name)||"recording-mode".equals(name)&&p.optInt("siid")==2)out.put(p);}return out;}
    static String title(JSONObject p){switch(p.optString("name")){case "status":return "存储卡状态";case "storage-total-space":return "总容量";case "storage-free-space":return "可用容量";case "storage-used-space":return "已用容量";case "remaining-recording-duration":return "剩余录制时长";default:return "录制模式";}}
    static String value(JSONObject p){String prefix=p.optBoolean("stale")?"上次：":"";if(!p.has("value"))return "暂未读取";String name=p.optString("name");if(name.startsWith("storage-")&&p.opt("value") instanceof Number){double kb=((Number)p.opt("value")).doubleValue();return prefix+String.format(java.util.Locale.ROOT,kb>=1048576?"%.1f GB":"%.1f MB",kb/(kb>=1048576?1048576:1024));}return prefix+MijiaModel.display(p);}
}
