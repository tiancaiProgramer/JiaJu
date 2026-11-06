package dev.homecontrol.app;

import android.content.*;
import java.util.*;

/** Provider errors are isolated; one account cannot hide or block the other's cached devices. */
final class CloudDevices {
    static String refresh(Context c){List<String> messages=new ArrayList<>();try{if(HaierCloud.loggedIn(c)){HaierCloud.refresh(c);messages.add(HaierClient.summary(c));}}catch(Exception e){messages.add(error(e,"海尔暂不可用，已保留上次状态"));}try{if(MijiaCloud.loggedIn(c)){MijiaCloud.refresh(c);messages.add(MijiaCloud.summary(c));}}catch(Exception e){String text=error(e,"米家暂不可用，已保留上次状态");DeviceStore.putPreference(c,"mijia_error",text);messages.add(text);}return String.join(" · ",messages);}
    static String error(Exception e,String fallback){if(e instanceof java.net.SocketTimeoutException)return "连接超时，请稍后重试";if(e instanceof java.net.UnknownHostException)return "无法连接服务，请检查网络";if(e instanceof javax.net.ssl.SSLException)return "安全连接未建立，请检查网络";if(e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().length()<150&&!e.getMessage().contains("https:")&&!e.getMessage().contains("http:")&&!e.getMessage().contains("{"))return e.getMessage();return fallback;}
    static Intent panel(Context c,String id){Class<?> target=HaierPanelActivity.class;if(id!=null&&id.startsWith("mijia:")){org.json.JSONObject cached=MijiaCloud.cached(c,id);target=cached!=null&&cached.optString("model").contains(".camera.")?CameraActivity.class:MijiaPanelActivity.class;}return new Intent(c,target).putExtra("id",id);}
    private CloudDevices(){}
}
