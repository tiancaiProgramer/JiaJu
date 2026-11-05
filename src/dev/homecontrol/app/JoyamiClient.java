package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Independent legacy cooker recipe transport. Request format observed in the installed plugin. */
final class JoyamiClient {
    private final String user,region,model;
    JoyamiClient(String user,String region,String model){this.user=user;this.region=region;this.model=model;}
    JSONObject request(String path,JSONObject params,boolean post)throws Exception{
        if(!path.matches("/(mijia-pot-service|microwaveoven)/[A-Za-z0-9_./-]{1,240}")||path.contains(".."))throw new IOException("食谱接口地址无效");
        String host="de".equals(region)?"fra-gateway.joyami.com":"us".equals(region)?"us-gateway.joyami.com":"ru".equals(region)?"ru-gateway.joyami.com":"sg".equals(region)?"sg-gateway.joyami.com":"gateway.joyami.com";
        byte[] bytes=new byte[12];new SecureRandom().nextBytes(bytes);String nonce=HaierWire.hex(bytes),time=Long.toString(System.currentTimeMillis()),method=post?"POST":"GET";
        String signature=Base64.getUrlEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(("com.chunmi.mijia.microwave&"+time+"&"+nonce+"&"+method+"&"+path).getBytes(StandardCharsets.UTF_8)));
        JSONObject data=new JSONObject(params.toString()).put("language","zh_CN");Map<String,String> headers=new LinkedHashMap<>();headers.put("signature",signature);headers.put("nonce",nonce);headers.put("timestamp",time);headers.put("vid",user);headers.put("version",model+": 1020021");headers.put("Accept","application/json");headers.put("Content-Type","application/json");String url="https://"+host+path;
        if(!post){Map<String,String> fields=new LinkedHashMap<>();for(Iterator<String> i=data.keys();i.hasNext();){String k=i.next();fields.put(k,String.valueOf(data.get(k)));}url+="?"+MijiaWire.form(fields);}
        MijiaHttp.Reply reply=new MijiaHttp().request(url,post?data.toString():null,headers,null,u->MijiaWire.secure(u)&&host.equals(u.getHost()),8388608,15000);if(reply.code!=200)throw new IOException("食谱服务暂不可用（HTTP "+reply.code+"）");return new JSONObject(reply.body);
    }
}
