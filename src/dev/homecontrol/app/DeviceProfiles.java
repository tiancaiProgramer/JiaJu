package dev.homecontrol.app;
import org.json.*;
import java.util.*;
/** Exact model dispatch: similarly named cooking modes may use different wire values. */
final class DeviceProfiles {
    static String kind(JSONObject d){String m=d.optString("model");if(m.contains(".light."))return "light";if(Arrays.asList("chunmi.cooker.normal2","chunmi.cooker.js5").contains(m))return "cooker";if("xiaomi.microwave.cm20l".equals(m))return "microwave";if(m.contains(".lock."))return "lock";return "generic";}
    static JSONArray modes(String model)throws JSONException{JSONArray out=new JSONArray();String[] names;int[] ids;
        if("chunmi.cooker.normal2".equals(model)){names=new String[]{"精煮","快煮","煮粥","保温","自选"};ids=new int[]{1,2,3,4,5};}
        else if("chunmi.cooker.js5".equals(model)){names=new String[]{"精煮","快煮","煮粥","保温","自选"};ids=new int[]{2,1,3,4,5};}
        else if("xiaomi.microwave.cm20l".equals(model)){names=new String[]{"火力加热","快热","食谱","除菌","自选","解冻"};ids=new int[]{1,6,7,8,10,9};}else return out;
        for(int i=0;i<names.length;i++)out.put(new JSONObject().put("title",names[i]).put("value",ids[i]));return out;
    }
    static boolean cooker(String model){return Arrays.asList("chunmi.cooker.normal2","chunmi.cooker.js5").contains(model);}
    static String modeTitle(String model,int value){try{JSONArray a=modes(model);for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getInt("value")==value)return a.getJSONObject(i).getString("title");}catch(Exception ignored){}return "设备模式";}
    private DeviceProfiles(){}
}
