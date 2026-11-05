package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;

/** Fail closed on stale appliance state; sleeping with a closed microwave door is idle. */
final class KitchenState {
    static void ready(String model,JSONArray props)throws IOException {
        JSONObject status=MijiaModel.find(props,"p:2:1");if(status==null||status.optBoolean("stale")||!(status.opt("value") instanceof Number))throw new IOException("未确认设备状态，请刷新后重试");int value=status.optInt("value",-1);boolean microwave="xiaomi.microwave.cm20l".equals(model);
        if(microwave){if(value!=1&&value!=10&&value!=12)throw new IOException("请确认设备空闲、炉门关闭后再操作");JSONObject fault=MijiaModel.find(props,"p:2:2");if(fault==null||fault.optBoolean("stale")||!(fault.opt("value") instanceof Number)||fault.optInt("value",-1)!=0)throw new IOException("微波炉故障状态尚未确认");JSONObject lock=MijiaModel.find(props,"p:4:1");if(lock!=null&&Boolean.TRUE.equals(lock.opt("value")))throw new IOException("设备童锁已开启，请先解除童锁");}
        else if(!DeviceProfiles.cooker(model)||value!=1)throw new IOException("设备正在工作，请先在设备上确认状态");
    }
    static String label(String model,int value){boolean micro="xiaomi.microwave.cm20l".equals(model);if(micro){switch(value){case 1:return "待机";case 2:return "烹饪中";case 3:case 13:case 14:case 15:return "炉门已打开";case 4:return "预约中";case 7:return "设备故障";case 9:return "已暂停";case 10:return "已完成";case 11:return "已锁定";case 12:return "休眠待机";case 16:return "升级中";default:return "待更新";}}switch(value){case 1:return "待机";case 2:return "烹饪中";case 3:return "保温中";case 4:return "设备忙";default:return "待更新";}}
    private KitchenState(){}
}
