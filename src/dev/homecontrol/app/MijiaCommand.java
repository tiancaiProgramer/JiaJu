package dev.homecontrol.app;

import org.json.*;
import java.io.IOException;

/** Validate against the current device schema and match the complete cloud acknowledgement. */
final class MijiaCommand {
    final String path,did;final int siid,id;final boolean action;final JSONObject data;
    private MijiaCommand(String p,String did,int s,int id,boolean action,JSONObject data){path=p;this.did=did;siid=s;this.id=id;this.action=action;this.data=data;}
    static MijiaCommand create(String did,JSONObject definition,Object value,boolean action)throws Exception{
        if(did==null||did.isEmpty()||definition==null||definition.optInt("siid")<=0)throw new IOException("设备未提供此控制项");
        int siid=definition.getInt("siid"),iid=definition.optInt(action?"aiid":"piid");if(iid<=0)throw new IOException("设备未提供此控制项");JSONObject request;
        if(action){if(!definition.optBoolean("supported")||!(value instanceof JSONArray))throw new IOException("该操作参数暂不支持");JSONArray inputs=definition.getJSONArray("inputs"),provided=(JSONArray)value;if(inputs.length()!=provided.length())throw new IOException("操作参数不完整");for(int i=0;i<inputs.length();i++)if(!MijiaModel.valid(inputs.getJSONObject(i),provided.get(i)))throw new IOException("操作参数超出设备支持范围");request=new JSONObject().put("params",new JSONObject().put("did",did).put("siid",siid).put("aiid",iid).put("in",provided));
        }else{if(!definition.optBoolean("writable")||!MijiaModel.valid(definition,value))throw new IOException("属性不可写或数值超出设备支持范围");request=new JSONObject().put("params",new JSONArray().put(new JSONObject().put("did",did).put("siid",siid).put("piid",iid).put("value",value)));}
        return new MijiaCommand(action?"/miotspec/action":"/miotspec/prop/set",did,siid,iid,action,request);
    }
    void accepted(JSONObject response)throws Exception{JSONObject result;if(action)result=response.optJSONObject("result");else{JSONArray list=response.optJSONArray("result");result=list!=null&&list.length()==1?list.optJSONObject(0):null;}
        if(result==null||!did.equals(result.optString("did"))||result.optInt("siid",-1)!=siid||result.optInt(action?"aiid":"piid",-1)!=id)throw new IOException("操作回执不完整，请刷新状态；不会自动重复发送");int code=result.optInt("code",Integer.MIN_VALUE);if(code!=0)throw new IOException("设备未接受操作（"+code+"）");
    }
}
