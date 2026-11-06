package dev.homecontrol.app;

import org.json.*;
import java.util.*;

/** Provider-scoped display metadata; these filters never decide homepage membership. */
final class DeviceLocation {
    static final String ANY="*", UNKNOWN="?";
    static String provider(JSONObject d){String value=d.optString("provider",d.optString("category"));return "haier".equals(value)||d.optString("id").startsWith("haier:")?"haier":"mijia";}
    static String providerName(String value){return "haier".equals(value)?"海尔智家":"米家";}
    static String home(JSONObject d){return key(d,"homeId","homeName");}
    static String room(JSONObject d){return key(d,"roomId","roomName");}
    private static String key(JSONObject d,String id,String name){String value=text(d,id,"");return !value.isEmpty()?value:!text(d,name,"").isEmpty()?"name:"+text(d,name,""):UNKNOWN;}
    static String homeName(JSONObject d){return text(d,"homeName","未分组");}
    static String roomName(JSONObject d){return text(d,"roomName","未分房间");}
    static String meta(JSONObject d){List<String> parts=new ArrayList<>();parts.add(providerName(provider(d)));if(!d.optString("homeName").isEmpty())parts.add(d.optString("homeName"));if(!d.optString("roomName").isEmpty())parts.add(d.optString("roomName"));return String.join(" · ",parts);}
    static String text(JSONObject o,String key,String fallback){String value=o.optString(key,"").trim();return value.isEmpty()||"null".equals(value)?fallback:value;}
    static boolean matches(JSONObject d,String provider,String home,String room){return provider.equals(provider(d))&&(ANY.equals(home)||home.equals(home(d)))&&(ANY.equals(room)||room.equals(room(d)));}
    static LinkedHashMap<String,String> homes(Collection<JSONObject> devices,String provider){LinkedHashMap<String,String> out=new LinkedHashMap<>();out.put(ANY,"全部家庭");for(JSONObject d:devices)if(provider.equals(provider(d)))out.put(home(d),homeName(d));return out;}
    static LinkedHashMap<String,String> rooms(Collection<JSONObject> devices,String provider,String home){LinkedHashMap<String,String> out=new LinkedHashMap<>();out.put(ANY,"全部房间");for(JSONObject d:devices)if(provider.equals(provider(d))&&(ANY.equals(home)||home.equals(home(d))))out.put(room(d),roomName(d));return out;}
    static void copy(JSONObject from,JSONObject to)throws JSONException{if(from==null)return;
        String home=text(from,"homeId",""),room=text(from,"roomId","");if(!home.isEmpty()&&!home.equals(text(to,"homeId",""))){if(!from.has("homeName"))to.put("homeName","");to.put("roomId","").put("roomName","");}if(!room.isEmpty()&&!room.equals(text(to,"roomId",""))&&!from.has("roomName"))to.put("roomName","");
        for(String key:new String[]{"homeId","homeName","roomId","roomName"})if(from.has(key))to.put(key,from.opt(key));}
    static JSONObject raw(JSONObject d)throws JSONException{JSONObject out=new JSONObject();String[][] fields={{"homeId","home_id","homeId"},{"homeName","home_name","homeName"},{"roomId","room_id","roomId"},{"roomName","room_name","roomName"}};for(String[] f:fields){String value=text(d,f[1],text(d,f[2],""));if(!value.isEmpty())out.put(f[0],value);}return out;}
    static JSONObject homesIndex(JSONObject result)throws JSONException {
        JSONObject out=new JSONObject();if(result==null)return out;
        for(String source:new String[]{"homelist","share_home_list","info"}){JSONArray homes=result.optJSONArray(source);if(homes==null)continue;
            for(int i=0;i<homes.length();i++){JSONObject h=homes.optJSONObject(i);if(h==null)continue;JSONObject location=new JSONObject().put("homeId",text(h,"id",text(h,"home_id",""))).put("homeName",text(h,"name",text(h,"home_name",""))).put("roomId","").put("roomName","");
                putDids(out,h.optJSONArray("dids"),location);JSONArray rooms=h.optJSONArray("roomlist");if(rooms==null)continue;
                for(int j=0;j<rooms.length();j++){JSONObject r=rooms.optJSONObject(j);if(r==null)continue;JSONObject room=new JSONObject(location.toString()).put("roomId",text(r,"id","")).put("roomName",text(r,"name",""));putDids(out,r.optJSONArray("dids"),room);}
            }
        }return out;
    }
    private static void putDids(JSONObject out,JSONArray ids,JSONObject location)throws JSONException{if(ids==null)return;for(int i=0;i<ids.length();i++){String id=ids.optString(i,"");if(!id.isEmpty()&&!"null".equals(id))out.put(id,new JSONObject(location.toString()));}}
    private DeviceLocation(){}
}
