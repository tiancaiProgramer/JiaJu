package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;
import java.util.*;
/** Private device-scoped feature operations; credentials and native IDs never enter Intents. */
final class MijiaFeatures {
    private final JSONObject current;private final MiHomeApi api;private final JSONObject record;private final String did,model,user,region;
    MijiaFeatures(MiHomeApi api,JSONObject record,String region,String user,JSONObject current)throws Exception{this.current=current;this.api=api;this.record=record;did=record.getString("did");model=record.getString("model");this.region=region;this.user=user;}
    static boolean mutates(String op){return Arrays.asList("kitchen.start","recipes.favorite","light.delay.set","light.delay.remove","light.timers.create","light.timers.toggle","light.timers.delete","camera.nas.set").contains(op);}
    static boolean requiresOnline(String op){return op.startsWith("light.delay.")||op.equals("kitchen.start")||op.startsWith("camera.nas.");}
    Object run(String operation,JSONObject input)throws Exception{
        if(operation.startsWith("camera.nas.")){String method=CameraNas.method(model,operation);Object params=CameraNas.params(operation,input);if(!did.matches("[A-Za-z0-9_.:-]{1,96}"))throw new IOException("设备标识无效");return api.request("/home/rpc/"+did,new JSONObject().put("method",method).put("params",params));}
        if(operation.startsWith("recipes."))return recipes(operation,input);
        if(operation.equals("kitchen.prepare")){JSONObject item=selected(input);return decodeProgram(item).describe(item.optString("name","烹饪菜单"));}
        if(operation.equals("kitchen.start"))return start(input);
        if(operation.equals("light.delay.get")){light();return rpc("cron_get",new JSONArray().put(0));}
        if(operation.equals("light.delay.set")){light();int minutes=KitchenProgram.integer(input,"minutes");if(minutes<1||minutes>1440)throw new IOException("延迟时间须为 1 至 1440 分钟");return rpc("cron_add",new JSONArray().put(0).put(minutes));}
        if(operation.equals("light.delay.remove")){light();return rpc("cron_del",new JSONArray().put(0));}
        if(operation.startsWith("light.timers.")){light();JSONObject scope=new JSONObject().put("identify",did).put("st_id",8);if(operation.equals("light.timers.list"))return api.request("/scene/list",scope);
            if(operation.equals("light.timers.create"))return api.request("/scene/edit",LightSchedules.create(did,input));
            if(operation.equals("light.timers.toggle")||operation.equals("light.timers.delete")){String usid=input.optString("us_id");JSONObject task=LightSchedules.owned(LightSchedules.list(api.request("/scene/list",scope)),usid,did);if(operation.endsWith("delete"))return api.request("/scene/delete",new JSONObject().put("identify",did).put("us_id",usid));if(!(input.opt("enabled") instanceof Boolean))throw new IOException("定时开关参数无效");JSONObject setting=new JSONObject(task.getJSONObject("setting").toString()).put("enable_timer",input.getBoolean("enabled")?"1":"0");return api.request("/scene/edit",scope.put("us_id",usid).put("name",task.getString("name")).put("setting",setting).put("authed",new JSONArray().put(did)));}
        }
        throw new IOException("此设备功能尚未接通");
    }
    private JSONObject selected(JSONObject input)throws Exception {
        String source=input.optString("source","detail");String id=recipeId(input);
        if("detail".equals(source))return FeatureData.object(recipes("recipes.detail",input));
        if(!"home".equals(source)&&!"optional".equals(source))throw new IOException("菜单来源无效");
        JSONArray items=FeatureData.items(recipes("optional".equals(source)?"recipes.optional":"recipes.home",input),"modes");
        for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(id.equals(item.optString("_id")))return item;}
        throw new IOException("菜单已更新，请重新选择");
    }
    private KitchenProgram program(JSONObject input)throws Exception {return decodeProgram(selected(input));}
    private KitchenProgram decodeProgram(JSONObject item)throws Exception {
        String code=item.optString("cookcode",item.optString("cookScript"));
        JSONArray models=item.optJSONArray("cookModels");if(models!=null){code="";for(int i=0;i<models.length();i++){JSONObject m=models.optJSONObject(i);if(m!=null&&model.equals(m.optString("model"))){code=m.optString("cookScript");break;}}}
        return new KitchenProgram(model,code);
    }
    private JSONObject start(JSONObject input)throws Exception {
        KitchenProgram program=program(input);String code=program.configure(input);
        if("xiaomi.microwave.cm20l".equals(model)){
            // Schema-bound acknowledgement, identical to normal MIoT control.
            JSONObject definition=current==null?null:MijiaModel.find(current.optJSONArray("actions"),"a:2:1");
            MijiaCommand command=MijiaCommand.create(did,definition,new JSONArray().put(program.fire()+1).put(code),true);command.accepted(api.request(command.path,command.data));
        }else if("chunmi.cooker.normal2".equals(model)){
            JSONObject answer=rpc("set_start",new JSONArray().put(code));Object result=answer.opt("result");
            if(!(result instanceof JSONArray)||((JSONArray)result).length()!=1||!"ok".equalsIgnoreCase(((JSONArray)result).optString(0)))throw new IOException("操作回执尚未确认，请刷新状态；不会重复发送");
        }else throw new IOException("此型号的扩展程序尚未验证");
        return new JSONObject().put("message","启动指令已受理，请留意设备状态");
    }
    private void light()throws IOException{if(!"yeelink.light.bslamp2".equals(model))throw new IOException("此灯具型号尚未适配设备定时");}
    private JSONObject rpc(String method,JSONArray params)throws Exception{
        if(!did.matches("[A-Za-z0-9_.:-]{1,96}"))throw new IOException("设备标识无效");return api.request("/home/rpc/"+did,new JSONObject().put("method",method).put("params",params));
    }
    private JSONObject recipes(String operation,JSONObject in)throws Exception{
        boolean microwave="xiaomi.microwave.cm20l".equals(model);if(!microwave&&!DeviceProfiles.cooker(model))throw new IOException("此型号暂未适配食谱服务");JSONObject q=new JSONObject().put("model",model).put("language","zh_CN");String path;boolean write=false;
        if(operation.equals("recipes.recommended")){q.put("pageNo",Math.max(1,Math.min(100,in.optInt("page",1)))).put("pageSize",10);path=microwave?"/cm-microwaveoven/v2/index/hotRecipes":"/microwaveoven/index/hotRecipes";}
        else if(operation.equals("recipes.search")){String query=in.optString("query").trim();if(query.length()>80)throw new IOException("搜索词过长");q.put("keyword",query).put("pageNo",Math.max(1,Math.min(100,in.optInt("page",1)))).put("pageSize",10);path=microwave?"/cm-microwaveoven/v2/index/search":"/microwaveoven/index/search";}
        else if(operation.equals("recipes.discover")){q.put(microwave?"deviceNo":"deviceId",did);if(microwave)q.put("mac",record.optString("mac"));else q.put("useId",user);path=microwave?"/cm-microwaveoven/v2/index/homepage":"/mijia-pot-service/portal/newIndex";}
        else if(operation.equals("recipes.home")){q.put("deviceId",did).put("useId",user);path=microwave?"/cm-microwaveoven/v2/api/device/"+segment(model)+"/"+segment(did)+"/index":"/mijia-pot-service/portal/newIndex";}
        else if(operation.equals("recipes.detail")){String id=recipeId(in);q.put("recipeId",id).put("deviceId",did).put("menuType",in.optString("menuType","1"));path=microwave?"/cm-microwaveoven/v2/index/getRecipeDetailById":"/microwaveoven/index/getRecipeDetailById";}
        else if(operation.equals("recipes.favorites")){q.put("deviceId",did).put("userId",user).put("recipeId",in.optString("lastRecipeId","0"));path=microwave?"/cm-microwaveoven/v2/api/device/"+segment(model)+"/"+segment(did)+"/index":"/microwaveoven/index/collection/mine/list";}
        else if(operation.equals("recipes.optional")){q.put(microwave?"deviceNo":"deviceId",did);if(microwave)q.put("relatedOfficialFunctionId",segment(in.optString("containerId")));path=microwave?"/cm-microwaveoven/v2/api/device/optional/listContainer":"/mijia-pot-service/api/device/optional/"+segment(model)+"/"+segment(did)+"/list";}
        else if(operation.equals("recipes.favorite")){String id=recipeId(in);boolean add=in.optBoolean("add");q.put("recipeId",id);write=true;if(microwave){q.put("menuType",in.optString("menuType","1")).put("bussinessId",id);path="/cm-microwaveoven/v2/api/recipe/collection/"+segment(model)+"/"+segment(did)+"/"+(add?"add":"cancel");}else path="/microwaveoven/index/collection/"+(add?"add":"cancel")+"/"+segment(did)+"/"+segment(model)+"/"+segment(user);}
        else throw new IOException("不支持的食谱操作");
        return microwave?api.request("/eco"+path,q):new JoyamiClient(user,region,model).request(path,q,write);
    }
    private static String segment(String id)throws IOException{if(!id.matches("[A-Za-z0-9_.:-]{1,96}"))throw new IOException("食谱参数无效");return id;}
    private static String recipeId(JSONObject in)throws IOException{return segment(in.optString("recipeId"));}
}
