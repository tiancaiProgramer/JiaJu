package dev.homecontrol.app;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Bounded vendor-envelope decoding; only named food collections become recipe buttons. */
final class FeatureData {
    static Object unwrap(Object raw)throws Exception {
        Object value=raw;
        for(int n=0;n<8;n++){
            if(value instanceof String){String s=((String)value).trim();if(!s.startsWith("{")&&!s.startsWith("["))return value;value=new JSONTokener(s).nextValue();continue;}
            if(!(value instanceof JSONObject))return value;
            JSONObject o=(JSONObject)value;
            if(o.has("code")){int code=o.optInt("code",Integer.MIN_VALUE);if(code!=0&&code!=200)throw new IOException("设备功能服务未成功（"+code+"）");}
            if(o.has("result")){value=o.get("result");continue;}
            if(o.has("data")&&o.length()<=5){value=o.get("data");continue;}
            return value;
        }
        throw new IOException("设备功能响应层级异常");
    }
    static JSONArray items(Object raw,String section)throws Exception {
        Object value=unwrap(raw);JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();
        collect(value,section,out,seen,0);return out;
    }
    private static void collect(Object v,String section,JSONArray out,Set<String> seen,int depth)throws Exception {
        if(depth>9||out.length()>=200)return;
        if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)collect(a.opt(i),section,out,seen,depth+1);return;}
        if(!(v instanceof JSONObject))return;JSONObject o=(JSONObject)v;
        String title=o.optString("name",o.optString("recipeName",o.optString("title",""))),id=o.optString("recipeId",o.optString("bussinessId",o.optString("id","")));
        if(!title.isEmpty()&&id.matches("[A-Za-z0-9_.:-]{1,96}")){
            String type=o.optString("menuType",o.optString("recipeType","1"));String key=id+":"+type;
            if(seen.add(key))out.put(new JSONObject(o.toString()).put("_id",id).put("_title",title).put("_menuType",type));return;
        }
        String[] keys="favorites".equals(section)?new String[]{"recipeCollections","colRecipesList","collectRecipes","collections","list","records","rows","content"}:"modes".equals(section)?new String[]{"officialFunctions","defaultRecipes","deviceModeList","recipeExpands","expandModes","extendModeList","extendModes","deviceRecipes","baseRecipes","functions","optionalRecipes","list"}:new String[]{"list","records","rows","content","recipes","recipeList","hotRecipes","recommendRecipes","recipeCollections","colRecipesList","deviceModeList","extendModeList","officialFunctions","floorList","floors","modules","itemList","items"};
        for(String k:keys){Object child=o.opt(k);if(child instanceof JSONObject||child instanceof JSONArray)collect(child,section,out,seen,depth+1);}
    }
    static JSONObject object(Object raw)throws Exception {Object v=unwrap(raw);if(!(v instanceof JSONObject))throw new IOException("设备返回的详情格式暂不支持");return (JSONObject)v;}
    static String detailText(JSONObject o){StringBuilder s=new StringBuilder();String description=plain(o.optString("description"));if(!description.isEmpty())s.append(description).append("\n\n");JSONArray ingredients=o.optJSONArray("ingredients");if(ingredients!=null&&ingredients.length()>0){s.append("食材\n");for(int i=0;i<ingredients.length();i++){JSONObject item=ingredients.optJSONObject(i);if(item!=null)s.append(plain(item.optString("name"))).append("  ").append(plain(item.optString("quantity"))).append(' ').append(unit(item.optString("quantityType"))).append('\n');}s.append('\n');}JSONArray steps=o.optJSONArray("steps");if(steps!=null&&steps.length()>0){s.append("步骤\n");for(int i=0;i<steps.length();i++){JSONObject step=steps.optJSONObject(i);String value=step==null?"":plain(step.optString("content"));if(!value.isEmpty())s.append(i+1).append(". ").append(value).append("\n\n");}}String tips=plain(o.optString("tips"));if(!tips.isEmpty())s.append("提示\n").append(tips);if(s.length()==0)append(o,s,0);return s.length()==0?"暂无步骤说明":s.substring(0,Math.min(16000,s.length())).trim();}
    private static void append(Object v,StringBuilder s,int depth){if(depth>8||s.length()>16000)return;if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)append(a.opt(i),s,depth+1);}else if(v instanceof JSONObject){JSONObject o=(JSONObject)v;for(String k:new String[]{"description","intro","remark","tips","material","materials","ingredients","ingredientList","steps","stepList","recipeSteps","stepDescription","content","text","name","amount","quantity","quantityType","unit"}){Object x=o.opt(k);if(x instanceof String){String t=plain((String)x);if(!t.isEmpty()&&!s.toString().contains(t+"\n"))s.append(t).append('\n');}else if(x instanceof JSONObject||x instanceof JSONArray)append(x,s,depth+1);}}}
    private static String unit(String value){String text=plain(value);return text.matches("[0-9]+")?"":text;}
    static String plain(String text){return text.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").trim();}
    private FeatureData(){}
}
