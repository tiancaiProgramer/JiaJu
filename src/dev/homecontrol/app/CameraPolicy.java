package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;
/** Exact-model, current-vault-only camera configuration. No account session reaches native code. */
final class CameraPolicy {
    static boolean supported(String model){return "chuangmi.camera.069a01".equals(model);}
    static boolean localAddress(String ip){if(ip==null||!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return false;String[] parts=ip.split("\\.");int[] b=new int[4];for(int i=0;i<4;i++){b[i]=Integer.parseInt(parts[i]);if(b[i]>255)return false;}return b[0]==10||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==192&&b[1]==168;}
    static JSONObject config(JSONObject device,JSONObject ticket)throws Exception{if(!supported(device.optString("model")))throw new IOException("摄像头型号或地址不支持");JSONObject vendor=ticket==null?null:ticket.optJSONObject("vendor");if(vendor==null||vendor.optInt("vendor")!=4)throw new IOException("该视频通道类型仍在适配");String key=ticket.optString("public_key"),sign=ticket.optString("sign");if(!key.matches("[0-9a-fA-F]{64}")||!sign.matches("[0-9a-fA-F]{64}"))throw new IOException("视频授权响应不完整");JSONObject params=vendor.optJSONObject("vendor_params");String p2p=params==null?"":params.optString("p2p_id"),init=params==null?"":params.optString("init_string");if(!localAddress(device.optString("localip"))&&(p2p.isEmpty()||init.isEmpty()))throw new IOException("摄像头未返回可用的视频连接地址");return new JSONObject().put("host",localAddress(device.optString("localip"))?device.optString("localip"):"").put("p2pId",p2p).put("initString",init).put("model",device.getString("model")).put("vendor",4).put("devicePublic",key).put("sign",sign).put("quality",1);}
}
