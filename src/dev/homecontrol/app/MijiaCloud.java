package dev.homecontrol.app;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;

import java.util.*;

/** Standalone Mi Home cloud client. Account identities and native DIDs stay in the vault. */
final class MijiaCloud {
    private final Context context;private final JSONObject session;private final MijiaVault vault;
    static final class Authentication extends IOException {Authentication(){super("米家登录已失效，请在设置中重新登录");}}
    private MijiaCloud(Context c,MijiaVault v)throws Exception{context=c.getApplicationContext();vault=v;session=v.read();validate(session);}
    static void validate(JSONObject s)throws Exception{String token=s.optString("serviceToken");if(!s.optString("userId").matches("[0-9]{1,24}")||token.isEmpty()||token.contains("\r")||token.contains("\n")||token.contains(";")||MijiaWire.un64(s.optString("ssecurity")).length<8)throw new Authentication();MijiaWire.api(s.optString("region"));}
    static synchronized boolean loggedIn(Context c)throws Exception{try(MijiaVault v=new MijiaVault(c)){JSONObject s=v.read();if(s.length()==0)return false;validate(s);return true;}}
    static synchronized void login(Context c,JSONObject session)throws Exception{validate(session);try(MijiaVault v=new MijiaVault(c)){JSONObject old=v.read();if(!old.optString("userId").equals(session.optString("userId"))||!old.optString("region").equals(session.optString("region")))DeviceStore.putPreference(c,"mijia_devices","[]");v.write(session);}DeviceStore.putPreference(c,"mijia_region",session.getString("region"));DeviceStore.putPreference(c,"mijia_auth_state","ready");DeviceStore.putPreference(c,"mijia_error","");}
    static synchronized void logout(Context c)throws Exception{try(MijiaVault v=new MijiaVault(c)){v.clear();}DeviceStore.putPreference(c,"mijia_devices","[]");DeviceStore.putPreference(c,"mijia_auth_state","logged_out");DeviceStore.putPreference(c,"mijia_error","");DeviceWidget.updateAll(c);}
    static synchronized void refresh(Context c)throws Exception{try(MijiaVault v=new MijiaVault(c)){new MijiaCloud(c,v).publish();}catch(Authentication e){DeviceStore.putPreference(c,"mijia_auth_state","expired");throw e;}}
    static String summary(Context c){try{if("0".equals(DeviceStore.preference(c,"mijia_sync_time","0")))return "账号已登录，尚未完成设备同步";int count=new JSONArray(DeviceStore.preference(c,"mijia_devices","[]")).length();return count==0?"当前地区未返回设备，请检查账号地区与共享权限":"已同步 "+count+" 台米家设备";}catch(Exception e){return "米家设备尚未同步";}}
    private JSONObject api(String path,JSONObject data)throws Exception{return new MiHomeApi(session).request(path,data);}
    private JSONArray directory()throws Exception{
        JSONObject answer=api("/home/device_list",new JSONObject().put("getVirtualModel",true).put("getHuamiDevices",1).put("get_split_device",true).put("support_smart_home",true));JSONObject result=answer.optJSONObject("result");JSONArray list=result==null?null:result.optJSONArray("list");if(list==null)throw new IOException("米家设备列表响应不兼容");
        if(list.length()>1000)throw new IOException("设备数量超过本版支持范围，未覆盖现有列表");return list;
    }
    private void publish()throws Exception{
        JSONArray raw=directory(),items=new JSONArray();JSONObject map=new JSONObject(),old=byId(new JSONArray(DeviceStore.preference(context,"mijia_devices","[]")));long now=System.currentTimeMillis();
        for(int i=0;i<raw.length();i++){JSONObject d=raw.optJSONObject(i);if(d==null)continue;String did=d.optString("did");if(did.isEmpty())continue;String id=DeviceIdentity.mijia(session.getString("userId"),session.getString("region"),did),model=d.optString("model"),name=d.optString("name","米家设备");JSONObject previous=old.optJSONObject(id);boolean online=d.optBoolean("isOnline",false);
            JSONObject item=new JSONObject().put("id",id).put("title",name).put("model",model).put("category","mijia").put("deviceType","mijia_device").put("source","mijia_cloud").put("state",online?1:0).put("subtitle",online?"在线 · 点击操作":"离线 · 暂时无法操作").put("updateTime",now);
            if(previous!=null)for(String key:new String[]{"capabilities","actions","capabilitiesTime","specType"})if(previous.has(key))item.put(key,previous.opt(key));items.put(item);
            map.put(id,new JSONObject().put("did",did).put("title",name).put("model",model).put("mac",d.optString("mac")).put("specType",d.optString("spec_type",d.optString("miot_type"))));
        }
        session.put("deviceMap",map);vault.write(session);DeviceStore.transaction(context,()->{DeviceStore.putPreference(context,"mijia_devices",items.toString());DeviceStore.putPreference(context,"mijia_sync_time",Long.toString(now));DeviceStore.putPreference(context,"mijia_auth_state","ready");DeviceStore.putPreference(context,"mijia_error","");return null;});
    }
    static synchronized JSONObject readDevice(Context c,String id)throws Exception{
        try(MijiaVault v=new MijiaVault(c)){MijiaCloud cloud=new MijiaCloud(c,v);cloud.publish();return cloud.read(id,true);}catch(Authentication e){DeviceStore.putPreference(c,"mijia_auth_state","expired");throw e;}
    }
    private JSONObject record(String id)throws Exception{JSONObject map=session.optJSONObject("deviceMap"),record=map==null?null:map.optJSONObject(id);if(record==null||!id.equals(DeviceIdentity.mijia(session.getString("userId"),session.getString("region"),record.optString("did"))))throw new IOException("此账号或地区未返回该设备，请在设置中检查账号");return record;}
    static JSONObject cached(Context c,String id){try{JSONArray items=new JSONArray(DeviceStore.preference(c,"mijia_devices","[]"));for(int i=0;i<items.length();i++){JSONObject d=items.optJSONObject(i);if(d!=null&&id.equals(d.optString("id")))return d;}}catch(Exception ignored){}return null;}
    private JSONObject read(String id,boolean fetchValues)throws Exception{
        JSONObject record=record(id),item=cached(context,id);if(item==null)throw new IOException("设备暂时不可用，请刷新设备列表");
        JSONObject schema=MijiaSpec.load(context,record.getString("model"),record.optString("specType")),parsed=MijiaModel.parse(schema);JSONArray props=parsed.getJSONArray("capabilities");MijiaModel.retain(props,item.optJSONArray("capabilities"));item.put("capabilities",props).put("actions",parsed.getJSONArray("actions")).put("specType",parsed.getString("specType"));saveItem(item);
        if(fetchValues&&item.optInt("state")==1){List<JSONObject> readable=new ArrayList<>();for(int i=0;i<props.length();i++)if(props.getJSONObject(i).optBoolean("readable"))readable.add(props.getJSONObject(i));boolean failed=false;
            for(int offset=0;offset<readable.size();offset+=40){JSONArray params=new JSONArray();for(int i=offset;i<Math.min(offset+40,readable.size());i++){JSONObject p=readable.get(i);params.put(new JSONObject().put("did",record.getString("did")).put("siid",p.getInt("siid")).put("piid",p.getInt("piid")));}
                try{JSONObject reply=api("/miotspec/prop/get",new JSONObject().put("params",params).put("datasource",1));JSONArray values=reply.optJSONArray("result");if(values==null)failed=true;else MijiaModel.results(props,values,record.getString("did"),System.currentTimeMillis());}catch(Authentication e){throw e;}catch(Exception e){failed=true;}
            }
            long confirmed=item.optLong("capabilitiesTime");for(JSONObject p:readable){if(!p.has("value")||p.optBoolean("stale"))failed=true;confirmed=Math.max(confirmed,p.optLong("valueTime"));}item.put("statusReadFailed",failed).put("capabilitiesTime",confirmed);
        }
        item.put("subtitle",status(item));saveItem(item);return item;
    }
    private String status(JSONObject item){if(item.optInt("state")==0)return "离线 · 暂时无法操作";JSONArray caps=item.optJSONArray("capabilities");if(caps!=null)for(int i=0;i<caps.length();i++){JSONObject p=caps.optJSONObject(i);if(p!=null&&"on".equals(p.optString("name"))&&p.opt("value") instanceof Boolean)return (p.optBoolean("stale")?"上次：":"")+((Boolean)p.opt("value")?"已开启":"已关闭");}return item.optBoolean("statusReadFailed")?"状态暂未更新 · 已保留控件":"在线 · 米家";}
    private void saveItem(JSONObject item){DeviceStore.transaction(context,()->{try{JSONArray items=new JSONArray(DeviceStore.preference(context,"mijia_devices","[]"));for(int i=0;i<items.length();i++)if(item.optString("id").equals(items.getJSONObject(i).optString("id"))){items.put(i,item);break;}DeviceStore.putPreference(context,"mijia_devices",items.toString());return null;}catch(JSONException e){throw new IllegalStateException(e);}});}
    static synchronized String control(Context c,String id,String key,String encoded)throws Exception{
        try(MijiaVault v=new MijiaVault(c)){
            MijiaCloud cloud=new MijiaCloud(c,v);cloud.publish();JSONObject device=cloud.cached(c,id),record=cloud.record(id);if(device==null||device.optInt("state")!=1)throw new IOException("设备离线，暂时无法操作");
            JSONObject current=cloud.read(id,false);Object value;try{value=new JSONArray("["+encoded+"]").get(0);}catch(Exception e){throw new IOException("操作参数无效");}
            boolean action=key.startsWith("a:");JSONObject capability=MijiaModel.find(current.optJSONArray(action?"actions":"capabilities"),key);MijiaCommand command=MijiaCommand.create(record.getString("did"),capability,value,action);
            JSONObject response;try{response=cloud.api(command.path,command.data);}catch(Authentication e){throw e;}catch(Exception e){throw new IOException("操作结果未确认，请刷新状态；不会自动重复发送");}
            command.accepted(response);return "操作已受理，正在同步设备状态";
        }catch(Authentication e){DeviceStore.putPreference(c,"mijia_auth_state","expired");throw e;}
    }
    static synchronized Object feature(Context c,String id,String operation,JSONObject input)throws Exception{
        try(MijiaVault v=new MijiaVault(c)){MijiaCloud cloud=new MijiaCloud(c,v);JSONObject record=cloud.record(id);if(MijiaFeatures.mutates(operation)){android.app.KeyguardManager guard=c.getSystemService(android.app.KeyguardManager.class);if(guard==null||guard.isDeviceLocked())throw new IOException("请先解锁手机");}if(MijiaFeatures.requiresOnline(operation)){cloud.publish();record=cloud.record(id);JSONObject item=cached(c,id);if(item==null||item.optInt("state")!=1)throw new IOException("设备离线，暂时无法操作");}
            JSONObject fresh=null;if(operation.equals("kitchen.start")){fresh=cloud.read(id,true);KitchenState.ready(record.optString("model"),fresh.optJSONArray("capabilities"));}
            return new MijiaFeatures(new MiHomeApi(cloud.session),record,cloud.session.optString("region"),cloud.session.optString("userId"),fresh).run(operation,input==null?new JSONObject():input);
        }catch(Authentication e){DeviceStore.putPreference(c,"mijia_auth_state","expired");throw e;}
    }
    private static JSONObject byId(JSONArray items)throws JSONException{JSONObject map=new JSONObject();for(int i=0;i<items.length();i++){JSONObject d=items.optJSONObject(i);if(d!=null)map.put(d.optString("id"),d);}return map;}
}
