package dev.homecontrol.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Server-bound official webpage approval; account passwords and passToken are not saved. */
final class MijiaLogin {
    private final java.net.CookieManager cookies=new java.net.CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final MijiaHttp http=new MijiaHttp();
    private volatile boolean cancelled;
    private String poll;
    private long deadline;
    private static final Map<String,String> HEADERS=Collections.singletonMap("User-Agent","Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36");
    String begin()throws Exception{
        LinkedHashMap<String,String> p=new LinkedHashMap<>();p.put("_qrsize","480");p.put("qs","%3Fsid%3Dxiaomiio%26_json%3Dtrue");p.put("callback","https://sts.api.io.mi.com/sts");p.put("_hasLogo","false");p.put("sid","xiaomiio");p.put("serviceParam","");p.put("_locale","zh_CN");p.put("_dc",Long.toString(System.currentTimeMillis()));
        JSONObject data=json(get("https://account.xiaomi.com/longPolling/loginUrl?"+MijiaWire.form(p),15000));String page=data.optString("loginUrl");poll=data.optString("lp");if(!MijiaWire.accountUrl(new URI(page))||!MijiaWire.accountUrl(new URI(poll)))throw new IOException("米家登录页暂不可用，请重试");deadline=System.currentTimeMillis()+Math.min(600,Math.max(60,data.optLong("timeout",300)))*1000;return page;
    }
    JSONObject await(String region)throws Exception {
        while(!cancelled&&System.currentTimeMillis()<deadline){
            JSONObject reply;try{reply=json(get(poll,12000));}catch(SocketTimeoutException timeout){continue;}catch(IOException e){if(System.currentTimeMillis()+5000>=deadline)throw new IOException("登录页面已过期，请返回后重新登录");throw e;}
            if(reply.optString("ssecurity").isEmpty()){if(reply.optInt("code",0)==70016)continue;throw new IOException("米家登录未完成，请重新打开登录页");}
            String user=reply.optString("userId"),security=reply.optString("ssecurity"),next=reply.optString("location");if(!user.matches("[0-9]{1,24}")||MijiaWire.un64(security).length<8)throw new IOException("米家登录回执无效");
            for(int hop=0;hop<8&&!next.isEmpty();hop++){if(cancelled)throw new InterruptedIOException();MijiaHttp.Reply response=http.request(next,null,HEADERS,cookies,MijiaWire::accountUrl,2097152,12000);if(response.code>=300&&response.code<400){String location=response.location();if(location==null)throw new IOException("米家登录跳转未完成");next=new URI(next).resolve(location).toString();}else{if(response.code!=200)throw new IOException("米家登录确认失败，请重试");break;}}
            String token="";for(HttpCookie cookie:cookies.getCookieStore().get(new URI("https://api.io.mi.com/")))if("serviceToken".equals(cookie.getName())&&!cookie.hasExpired())token=cookie.getValue();
            if(token.isEmpty())for(HttpCookie cookie:cookies.getCookieStore().get(new URI("https://sts.api.io.mi.com/sts")))if("serviceToken".equals(cookie.getName())&&!cookie.hasExpired())token=cookie.getValue();
            if(token.isEmpty())throw new IOException("未收到米家服务会话，请返回重试");
            MijiaWire.api(region);return new JSONObject().put("userId",user).put("ssecurity",security).put("serviceToken",token).put("region",region).put("deviceMap",new JSONObject()).put("createdAt",System.currentTimeMillis());
        }
        throw new IOException(cancelled?"登录已取消":"登录页面已过期，请重新打开");
    }
    private String get(String url,int timeout)throws Exception{MijiaHttp.Reply reply=http.request(url,null,HEADERS,cookies,MijiaWire::accountUrl,2097152,timeout);if(reply.code!=200)throw new IOException("米家登录服务暂不可用（HTTP "+reply.code+"）");return reply.body;}
    static JSONObject json(String value)throws JSONException{return new JSONObject(value.startsWith("&&&START&&&")?value.substring(11):value);}
    void cancel(){cancelled=true;http.cancel();cookies.getCookieStore().removeAll();}
}
