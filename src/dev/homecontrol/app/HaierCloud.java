package dev.homecontrol.app;

import android.content.Context;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Experimental mainland U+ client, adapted from banto6/haier (Apache-2.0).
 * Uses the community App protocol profile; this is not official platform certification.
 * No dependency on the official app, its storage, its process, or LSPosed.
 */
final class HaierCloud {
    private static final String APP_ID="MB-UZHSH-0001", APP_KEY="5dfca8714eb26e3a776e58a8273c8752";
    private static final String LOGIN="https://zj.haier.net/api-gw/oauthserver/account/v1/login", RENEW="https://zj.haier.net/api-gw/oauthserver/account/v1/refreshToken";
    private static final String DEVICES="https://uws.haier.net/uds/v1/protected/deviceinfos", MODEL="https://uws.haier.net/shadow/v1/devdigitalmodels", GATEWAY="https://uws.haier.net/gmsWS/wsag/assign";
    static final String OAUTH_APP="MB-ISP72111109101-0004",OAUTH_CLIENT="SV-ISP72111109101-0004",OAUTH_CALLBACK="http://homeassistant.local:8123/auth/external/callback";
    private static final String HA="https://ha.haier.net/api-gw/ha/se/";
    private static final List<String> KEYS=Arrays.asList("onOffStatus","lightingStatus","windSpeedLevel","selfCleaningStatus","childLockStatus","meltWashStatus");
    private final Context context;private final HaierVault vault;private JSONObject session;
    private HaierCloud(Context c,HaierVault v)throws Exception{this(c,v,true);}
    private HaierCloud(Context c,HaierVault v,boolean load)throws Exception{context=c;vault=v;session=load?v.read():new JSONObject();}
    static void login(Context c,String phone,String password)throws Exception {
        if(phone==null||!phone.matches("1[0-9]{10}")||password==null||password.isEmpty())throw new IOException("请输入海尔智家手机号和密码");
        try(HaierVault v=new HaierVault(c)){
            HaierCloud cloud=new HaierCloud(c,v,false);
            cloud.session=new JSONObject().put("clientId",phone);
            JSONObject answer=cloud.http(LOGIN,new JSONObject().put("username",phone).put("password",password));
            cloud.setTokens(answer);cloud.session.put("deviceMap",new JSONObject());v.write(cloud.session);
            DeviceStore.putPreference(c,"haier_enabled","1");DeviceStore.putPreference(c,"haier_backend","cloud");
            // Never keep a former account's device directory under the new session.
            DeviceStore.putPreference(c,"haier_devices","[]");DeviceStore.putPreference(c,"haier_sync_time","0");DeviceStore.putPreference(c,"haier_error","");
        }
    }
    static boolean loggedIn(Context c)throws Exception {try(HaierVault v=new HaierVault(c)){return !v.read().optString("refreshToken").isEmpty();}}
    static void oauth(Context c,String code,String clientId)throws Exception {
        try(HaierVault v=new HaierVault(c)){
            HaierCloud cloud=new HaierCloud(c,v,false);cloud.session.put("profile","ha_oauth").put("clientId",clientId);
            cloud.setTokens(cloud.http(HA+"account/token",new JSONObject().put("code",code).put("clientId",clientId).put("redirectUri",OAUTH_CALLBACK).put("grantType","authorization_code")));v.write(cloud.session);
            DeviceStore.putPreference(c,"haier_enabled","1");DeviceStore.putPreference(c,"haier_backend","cloud");DeviceStore.putPreference(c,"haier_devices","[]");DeviceStore.putPreference(c,"haier_sync_time","0");DeviceStore.putPreference(c,"haier_error","");
        }
    }
    private boolean oauth(){return "ha_oauth".equals(session.optString("profile"));}
    static void logout(Context c)throws Exception {
        try(HaierVault v=new HaierVault(c)){v.clear();DeviceStore.putPreference(c,"haier_enabled","0");DeviceStore.putPreference(c,"haier_backend","cloud");DeviceStore.putPreference(c,"haier_devices","[]");DeviceStore.putPreference(c,"haier_sync_time","0");DeviceStore.putPreference(c,"haier_error","");}
        DeviceWidget.updateAll(c);
    }
    static void refresh(Context c)throws Exception {try(HaierVault v=new HaierVault(c)){HaierCloud cloud=new HaierCloud(c,v);cloud.authorize();cloud.publish();}}
    private void authorize()throws Exception {
        if(session.optString("refreshToken").isEmpty())throw new IOException("请在本应用登录海尔账号");
        if(System.currentTimeMillis()+120000>=session.optLong("expiresAt")){
            JSONObject payload=new JSONObject().put("refreshToken",session.getString("refreshToken"));if(oauth())payload.put("clientId",session.getString("clientId")).put("grantType","refresh_token");
            JSONObject answer=http(oauth()?HA+"account/refresh/token":RENEW,payload);setTokens(answer);vault.write(session);
        }
    }
    private void setTokens(JSONObject answer)throws Exception {
        JSONObject data=answer.optJSONObject("data"),tokens=oauth()?data:data==null?null:data.optJSONObject("tokenInfo");String accessKey=oauth()?"accessToken":"accountToken";
        if(tokens==null||tokens.optString(accessKey).isEmpty()||tokens.optString("refreshToken").isEmpty()||tokens.optLong("expiresIn")<=0)throw new IOException("登录未完成；如海尔要求验证码，请稍后重试或使用支持的授权方式");
        session.put("token",tokens.getString(accessKey)).put("refreshToken",tokens.getString("refreshToken")).put("expiresAt",System.currentTimeMillis()+tokens.getLong("expiresIn")*1000L);
    }
    private JSONObject http(String api,JSONObject payload)throws Exception {
        URL url=new URL(api);if(!"https".equals(url.getProtocol())||!(url.getHost().equals("zj.haier.net")||url.getHost().equals("uws.haier.net")||url.getHost().equals("ha.haier.net")))throw new IOException("接口地址无效");
        String body=payload==null?"":HaierWire.asciiJson(payload.toString()),timestamp=Long.toString(System.currentTimeMillis());
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(payload==null?"GET":"POST");
        try {
            connection.setRequestProperty("accessToken",session.optString("token"));connection.setRequestProperty("appId",APP_ID);connection.setRequestProperty("appKey",APP_KEY);connection.setRequestProperty("clientId",session.optString("clientId"));
            connection.setRequestProperty("timestamp",timestamp);connection.setRequestProperty("sequenceId",new SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).format(new Date())+String.format(Locale.ROOT,"%06d",new java.security.SecureRandom().nextInt(900000)+100000));
            connection.setRequestProperty("sign",HaierWire.sign(url.getPath(),body,APP_ID,APP_KEY,timestamp));connection.setRequestProperty("timezone","+8");connection.setRequestProperty("language","zh-CN");connection.setRequestProperty("Accept","application/json");
            if(oauth()){connection.setRequestProperty("appId",OAUTH_APP);connection.setRequestProperty("appKey","");connection.setRequestProperty("sign","");connection.setRequestProperty("appVersion","1.0.0");connection.setRequestProperty("Accept-Language","zh");connection.setRequestProperty("User-Agent","Apache-HttpClient/4.5.14 (Java/1.8.0_442)");}
            if(payload!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int status=connection.getResponseCode();if(status<200||status>=300)throw new IOException(status==401||status==403?"海尔认证失效，请重新登录":"海尔服务暂不可用（HTTP "+status+"）");
            JSONObject answer;try(InputStream stream=connection.getInputStream()){answer=new JSONObject(new String(limited(stream,2097152),StandardCharsets.UTF_8));}
            String code=answer.optString("retCode");if(!"00000".equals(code))throw new IOException("海尔接口未成功（"+(code.matches("[A-Za-z0-9_-]{1,16}")?code:"响应无效")+"），请检查账号登录或稍后重试");
            return answer;
        }catch(org.json.JSONException e){throw new IOException("海尔响应格式不兼容，请稍后重试");}
        finally{connection.disconnect();}
    }
    private JSONArray directory()throws Exception {
        JSONObject answer=http(oauth()?HA+"dev/list":DEVICES,null);if(oauth())answer=answer.optJSONObject("data");JSONArray a=answer==null?null:answer.optJSONArray(oauth()?"deviceInfos":"deviceinfos");if(a==null)throw new IOException("海尔设备列表响应不兼容");
        if(oauth())for(int i=0;i<a.length();i++){JSONObject d=a.getJSONObject(i);if(!d.has("deviceId"))d.put("deviceId",d.optString("device_id"));if(!d.has("deviceName"))d.put("deviceName",d.optString("devName",d.optString("device_name","海尔设备")));if(!d.has("online"))d.put("online",d.optBoolean("isOnline"));}
        return a;
    }
    private JSONObject model(String nativeId)throws Exception {
        if(oauth()){Object data=http(HA+"dev/digitalmodels",new JSONObject().put("deviceId",nativeId)).opt("data");if(data instanceof JSONArray)return new JSONObject().put("attributes",data);if(data instanceof JSONObject){JSONObject d=(JSONObject)data;if(d.optJSONArray("attributes")!=null)return d;JSONObject nested=d.optJSONObject("data");if(nested!=null)return nested;}throw new IOException("设备未返回兼容的属性模型");}
        JSONObject answer=http(MODEL,new JSONObject().put("deviceInfoList",new JSONArray().put(new JSONObject().put("deviceId",nativeId))));
        JSONObject detail=answer.optJSONObject("detailInfo");Object data=detail==null?null:detail.opt(nativeId);
        if(data instanceof JSONObject)return (JSONObject)data;if(data instanceof String)return new JSONObject((String)data);throw new IOException("设备尚未返回属性定义");
    }
    private void publish()throws Exception {
        JSONArray raw=directory(),result=new JSONArray();JSONObject map=new JSONObject();long time=System.currentTimeMillis();
        Map<String,JSONObject> remembered=new HashMap<>();JSONArray old=new JSONArray(DeviceStore.preference(context,"haier_devices","[]"));for(int i=0;i<old.length();i++){JSONObject d=old.optJSONObject(i);if(d!=null)remembered.put(d.optString("id"),d);}
        if(raw.length()>128)throw new IOException("海尔设备超过本版支持的 128 台，请先缩小设备范围");
        for(int i=0;i<raw.length();i++){
            JSONObject d=raw.getJSONObject(i);String nativeId=d.optString("deviceId");if(nativeId.isEmpty())continue;
            String id=publicId(nativeId),title=d.optString("deviceName","海尔设备"),product=d.optString("productNameT");JSONObject definition=null;
            boolean hood=HaierPolicy.isHood(title,product);
            if(hood&&System.currentTimeMillis()-time<20000)try{definition=model(nativeId);}catch(Exception ignored){}
            JSONArray attributes=definition==null?new JSONArray():definition.optJSONArray("attributes");if(attributes==null)attributes=new JSONArray();
            Map<String,String> values=values(attributes);
            JSONArray capabilities=new JSONArray();for(int j=0;j<attributes.length();j++){JSONObject a=attributes.getJSONObject(j);if(KEYS.contains(a.optString("name")))capabilities.put(new JSONObject().put("key",a.optString("name")).put("value",a.opt("value")).put("writable",a.optBoolean("writable")).put("range",a.optJSONObject("valueRange")));}
            boolean online=d.optBoolean("online");String subtitle=(online?"":"离线 · ")+(hood?HaierPolicy.status(values):"海尔设备 · 暂未适配控制");if(hood&&definition==null)subtitle="状态读取失败，请刷新";
            JSONObject item=new JSONObject().put("id",id).put("title",title).put("deviceType",hood?"haier_hood":"haier_device").put("category","haier").put("subtitle",subtitle).put("state",online?1:0).put("battery","").put("icon","").put("updateTime",time).put("capabilitiesTime",time).put("capabilities",capabilities).put("source","haier_cloud");
            result.put(HaierSnapshot.retainModel(remembered.get(id),item,definition!=null));
            map.put(id,new JSONObject().put("deviceId",nativeId).put("title",title).put("model",product));
        }
        session.put("deviceMap",map);vault.write(session);DeviceStore.putPreference(context,"haier_devices",result.toString());DeviceStore.putPreference(context,"haier_sync_time",Long.toString(time));DeviceStore.putPreference(context,"haier_error","");
    }
    static String control(Context c,String id,String key,String value,boolean execute)throws Exception {
        try(HaierVault v=new HaierVault(c)){
            HaierCloud cloud=new HaierCloud(c,v);cloud.authorize();JSONObject map=cloud.session.optJSONObject("deviceMap"),record=map==null?null:map.optJSONObject(id);
            if(record==null)throw new IOException("请先读取海尔设备列表");String nativeId=record.getString("deviceId");
            boolean online=false;JSONArray directory=cloud.directory();for(int i=0;i<directory.length();i++)if(nativeId.equals(directory.getJSONObject(i).optString("deviceId")))online=directory.getJSONObject(i).optBoolean("online");
            if(!online)throw new IOException("设备离线，暂时无法操作");
            JSONObject definition=cloud.model(nativeId);JSONArray attrs=definition.optJSONArray("attributes");if(attrs==null)throw new IOException("设备未返回控制能力");
            if(!HaierPolicy.isHood(record.optString("title"),record.optString("model")))throw new IOException("当前设备暂未适配控制");
            JSONObject attribute=null;Map<String,String> states=values(attrs);List<String> range=new ArrayList<>();
            for(int i=0;i<attrs.length();i++){JSONObject a=attrs.getJSONObject(i);if(key!=null&&key.equals(a.optString("name")))attribute=a;}
            if(attribute!=null){JSONObject r=attribute.optJSONObject("valueRange");JSONArray list=r==null?null:r.optJSONArray("dataList");if(list!=null)for(int i=0;i<list.length();i++)range.add(list.getJSONObject(i).optString("data"));}
            if(attribute==null||!HaierControlPolicy.allowed(key,value,attribute.optBoolean("writable"),range,Arrays.asList("1","3","4")))throw new IOException("设备当前未提供此控制项");
            for(String guard:Arrays.asList("selfCleaningStatus","childLockStatus","meltWashStatus"))if("true".equals(states.get(guard)))throw new IOException("设备处于清洗或童锁状态，请先处理");
            JSONArray alarms=definition.optJSONArray("alarms");if(alarms!=null)for(int i=0;i<alarms.length();i++){JSONObject alarm=alarms.optJSONObject(i);if(alarm!=null&&"true".equals(alarm.optString("value")))throw new IOException("设备存在告警，请先确认设备状态");}
            if(!execute)return "云端控制能力检查通过，未下发实体指令";
            if(value.equals(states.get(key))){try{cloud.publish();}catch(Exception ignored){}return "设备已处于该状态";}
            String gateway=cloud.oauth()?"wss://ha-gw.haier.net:59502":cloud.http(GATEWAY,new JSONObject().put("clientId",cloud.session.getString("clientId")).put("token",cloud.session.getString("token"))).getString("agAddr");
            if(gateway.startsWith("http://"))gateway="wss://"+gateway.substring(7);else if(gateway.startsWith("https://"))gateway="wss://"+gateway.substring(8);
            URI endpoint=new URI(gateway);String token=cloud.session.getString("token"),sn=UUID.randomUUID().toString().replace("-","");boolean confirmed=false;
            try(HaierSocket socket=new HaierSocket(endpoint,token,cloud.oauth()?OAUTH_APP:null)){
                socket.send(new JSONObject().put("agClientId",token).put("topic","BoundDevs").put("content",new JSONObject().put("devs",new JSONArray().put(nativeId))).toString());
                JSONObject cmd=new JSONObject().put("sn",sn).put("index",0).put("delaySeconds",0).put("subSn",sn+":0").put("deviceId",nativeId).put("cmdArgs",new JSONObject().put(key,value));
                if(cloud.oauth()){cmd.remove("sn");cmd.remove("delaySeconds");cmd.remove("subSn");}
                socket.send(new JSONObject().put("agClientId",token).put("topic","BatchCmdReq").put("content",new JSONObject().put("trace",UUID.randomUUID().toString().replace("-","")).put("sn",sn).put("data",new JSONArray().put(cmd))).toString());
                long deadline=System.currentTimeMillis()+7000;
                try{while(System.currentTimeMillis()<deadline){JSONObject incoming=new JSONObject(socket.receive(deadline));if(matches(incoming,nativeId,key,value)){confirmed=true;break;}}}catch(Exception unconfirmed){/* Never resend the write. */}
            }catch(Exception transport){throw new IOException("控制结果未确认，请刷新状态；为避免重复操作，不会自动重发");}
            if(!confirmed){try{confirmed=value.equals(values(cloud.model(nativeId).getJSONArray("attributes")).get(key));}catch(Exception ignored){}}
            try{cloud.publish();}catch(Exception ignored){}
            return confirmed?"设备状态已确认":"指令已发送，设备尚未确认，请刷新状态";
        }
    }
    private static boolean matches(JSONObject message,String nativeId,String key,String value)throws Exception {
        JSONObject content=message.optJSONObject("content");if(!"GenMsgDown".equals(message.optString("topic"))||content==null||!("DigitalModel".equals(content.optString("businType"))||"DigitalModelHA".equals(content.optString("businType"))))return false;
        JSONObject data=new JSONObject(new String(Base64.getDecoder().decode(content.getString("data")),StandardCharsets.UTF_8));if(!nativeId.equals(data.optString("dev")))return false;
        JSONObject plain=data.optJSONObject("args");if(plain!=null)return value.equals(values(plain.getJSONArray("attributes")).get(key));
        byte[] packed=Base64.getDecoder().decode(data.getString("args"));if(packed.length>1048576)throw new IOException("状态报文过大");
        try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(packed))){JSONObject model=new JSONObject(new String(limited(gzip,1048576),StandardCharsets.UTF_8));return value.equals(values(model.getJSONArray("attributes")).get(key));}
    }
    private static Map<String,String> values(JSONArray attrs)throws JSONException {Map<String,String> values=new HashMap<>();for(int i=0;i<attrs.length();i++){JSONObject a=attrs.getJSONObject(i);if(a.has("value"))values.put(a.optString("name"),a.optString("value"));}return values;}
    private static String publicId(String raw){return DeviceIdentity.haier(raw);}
    private static byte[] limited(InputStream in,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;){if(out.size()+n>limit)throw new IOException("海尔响应过大");out.write(buffer,0,n);}return out.toByteArray();}
    private HaierCloud(){throw new AssertionError();}
}
