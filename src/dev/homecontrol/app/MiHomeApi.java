package dev.homecontrol.app;

import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.*;

/** Authenticated transport shared by the directory, timers, RPC and manufacturer proxy. */
final class MiHomeApi {
    private final JSONObject session;
    MiHomeApi(JSONObject session)throws Exception{MijiaCloud.validate(session);this.session=session;}
    JSONObject request(String path,JSONObject data)throws Exception{
        if(!allowed(path))throw new IOException("不支持的米家请求");
        String base=MijiaWire.api(session.getString("region"));byte[] random=new byte[8];new SecureRandom().nextBytes(random);String nonce=MijiaWire.nonce(random,System.currentTimeMillis()),security=session.getString("ssecurity");
        Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent","Android-16-HomeControl APP/xiaomi.smarthome APPV/62830");headers.put("Content-Type","application/x-www-form-urlencoded");headers.put("x-xiaomi-protocal-flag-cli","PROTOCAL-HTTP2");headers.put("MIOT-ENCRYPT-ALGORITHM","ENCRYPT-RC4");headers.put("Cookie","userId="+session.getString("userId")+"; serviceToken="+session.getString("serviceToken")+"; yetAnotherServiceToken="+session.getString("serviceToken")+"; locale=zh_CN; timezone=GMT%2B08%3A00; is_daylight=0; dst_offset=0; channel=MI_APP_STORE");
        String host=new URI(base).getHost();MijiaHttp.Reply reply=new MijiaHttp().request(base+path,MijiaWire.form(MijiaWire.encrypt(path,data.toString(),security,nonce)),headers,null,u->MijiaWire.secure(u)&&host.equals(u.getHost()),16777216,20000);
        if(reply.code==401||reply.code==403)throw new MijiaCloud.Authentication();if(reply.code!=200)throw new IOException("米家服务暂不可用（HTTP "+reply.code+"）");JSONObject answer;try{answer=new JSONObject(reply.body.trim().startsWith("{")?reply.body:MijiaWire.decrypt(security,nonce,reply.body));}catch(Exception e){throw new IOException("米家响应未能解密，请重新登录或稍后重试");}
        int code=answer.optInt("code",Integer.MIN_VALUE);if(code==3||code==-3||code==401||code==-10001)throw new MijiaCloud.Authentication();if(code!=0)throw new IOException("米家请求未成功（"+code+"）");return answer;
    }
    static boolean allowed(String path){
        if(Arrays.asList("/home/device_list","/v2/homeroom/gethome","/v2/home/home_device_list","/miotspec/prop/get","/miotspec/prop/set","/miotspec/action","/scene/list","/scene/get","/scene/edit","/scene/delete").contains(path))return true;
        if(path.matches("/home/rpc/[A-Za-z0-9_.:-]{1,96}"))return true;
        return path.matches("/eco/(cm-microwaveoven/v2|mijia-pot-service|microwaveoven)/[A-Za-z0-9_./-]{1,220}")&&!path.contains("..");
    }
}
