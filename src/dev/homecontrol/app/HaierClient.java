package dev.homecontrol.app;
import android.content.Context;
/** App and widget entry points use the independent cloud client exclusively. */
final class HaierClient {
    static boolean isDevice(String id){return id!=null&&id.startsWith("haier:");}
    static void refresh(Context c)throws Exception{HaierCloud.refresh(c);}
    static String summary(Context c){try{int count=new org.json.JSONArray(DeviceStore.preference(c,"haier_devices","[]")).length();return count>0?"已同步 "+count+" 台海尔设备":"登录已完成，但此接口未返回设备；可检查账号或尝试另一登录方式";}catch(Exception e){return "设备列表尚未读取";}}
    private HaierClient(){}
}
