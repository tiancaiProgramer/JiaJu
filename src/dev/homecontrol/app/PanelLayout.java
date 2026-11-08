package dev.homecontrol.app;

import org.json.*;
import java.util.*;
import java.math.BigDecimal;

/** Presentation only. Every control still uses the current schema and original wire value. */
final class PanelLayout {
    static boolean cycleData(String model,JSONObject p){return "cuco.plug.v3".equals(model)&&"p:5:2".equals(p.optString("key"));}
    static String fieldTitle(String model,JSONObject p){if(cycleData(model,p))return "循环时间";String unit=p.optString("unit"),suffix="seconds".equals(unit)?"（秒）":"minutes".equals(unit)?"（分钟）":"celsius".equals(unit)?"（℃）":"";return p.optString("title")+suffix;}
    static String summary(String model,JSONObject p){if(cycleData(model,p)){try{CycleTask task=new CycleTask(p.optString("value"));return (p.optBoolean("stale")?"上次：":"")+"开 "+task.onMinutes+" 分钟 / 关 "+task.offMinutes+" 分钟";}catch(Exception unsupported){return p.has("value")?"任务时间待适配":"状态待更新";}}return (p.optBoolean("stale")&&p.has("value")?"上次：":"")+MijiaModel.display(p);}
    static final Set<String> METRICS=new HashSet<>(Arrays.asList("temperature","electric-current","current","voltage","electric-power","power-consumption","energy-consumption"));
    static boolean power(JSONObject d){String model=d.optString("model");return model.contains(".plug.")||model.contains(".outlet.");}
    static boolean climate(JSONObject d){String model=d.optString("model");return model.contains(".acpartner.")||model.contains(".aircondition.")||model.contains(".airconditioner.");}
    static boolean compact(JSONObject d){return power(d)||climate(d);}
    static boolean metric(JSONObject p){return p.optBoolean("readable")&&!p.optBoolean("writable")&&METRICS.contains(p.optString("name"));}
    static List<JSONObject> main(JSONObject d,JSONArray props){List<JSONObject> list=new ArrayList<>();if(props==null)return list;
        String[] order=climate(d)?new String[]{"on","mode","target-temperature","horizontal-angle","vertical-angle","horizontal-swing","vertical-swing","fan-level","fan-speed","speed-level"}:new String[]{"on"};
        for(String name:order)for(int i=0;i<props.length();i++){JSONObject p=props.optJSONObject(i);if(p==null||!name.equals(p.optString("name"))||!p.optBoolean("writable"))continue;
            if("on".equals(name)){if(climate(d)&&!p.optString("service").contains("空调")&&!p.optString("service").contains("air-condition"))continue;if(power(d)&&!(p.optString("service").contains("指示灯")||p.optString("service").contains("indicator")||p.optString("service").equals("开关")||p.optString("service").equals("插座")||p.optString("service").equals("switch")||p.optString("service").equals("outlet")))continue;}
            list.add(p);
        }return list;
    }
    static String controlTitle(JSONObject p){if("on".equals(p.optString("name"))&&(p.optString("service").contains("指示灯")||p.optString("service").contains("indicator")))return "指示灯";if("on".equals(p.optString("name")))return "开关";return p.optString("title");}
    static String metrics(JSONObject d,JSONArray props){List<String> out=new ArrayList<>();if(props!=null)for(int i=0;i<props.length();i++){JSONObject p=props.optJSONObject(i);if(p==null||!metric(p))continue;String value="—";if(p.has("value")){value=formatMetric(d.optString("model"),p);if(p.optBoolean("stale"))value="上次 "+value;}String name=p.optString("name"),title="temperature".equals(name)?"温度":"electric-current".equals(name)||"current".equals(name)?"电流":"voltage".equals(name)?"电压":"electric-power".equals(name)?"功率":"耗电";String raw=p.optString("title");if(raw.contains("16A"))title+="16A";else if(raw.contains("10A"))title+="10A";out.add(title+" "+value);}return out.isEmpty()?"暂无用电数据":String.join("  ·  ",out);}
    static String formatMetric(String model,JSONObject p){Object raw=p.opt("value");if(!(raw instanceof Number))return MijiaModel.display(p);double v=((Number)raw).doubleValue();String key=p.optString("key"),name=p.optString("name"),unit=p.optString("unit");
        if("chuangmi.plug.212a01".equals(model)&&"p:5:1".equals(key)){v=v*6/1000000;unit="kWh";}
        else if("cuco.plug.v3".equals(model)&&"p:11:1".equals(key)){v*=.01;unit="kWh";}
        else if("chuangmi.plug.212a01".equals(model)&&"p:5:6".equals(key)){v*=.01;unit="W";}
        else if("celsius".equals(unit))unit="℃";else if("watt".equals(unit))unit="W";else if("kwh".equalsIgnoreCase(unit))unit="kWh";else if("none".equals(unit))unit="";
        if(!Double.isFinite(v))return "—";return BigDecimal.valueOf(v).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+(unit.isEmpty()?"":" "+unit);
    }
    static final class Group {final String id,title;final List<JSONObject> properties=new ArrayList<>();Group(String id,String title){this.id=id;this.title=title;}}
    static List<Group> groups(JSONObject device,JSONArray props,Set<String> used){LinkedHashMap<String,Group> map=new LinkedHashMap<>();if(props!=null)for(int i=0;i<props.length();i++){JSONObject p=props.optJSONObject(i);if(p==null||used.contains(p.optString("key"))||metric(p))continue;String service=p.optString("service","其它功能"),id=Integer.toString(p.optInt("siid"));
            String title=service.contains("循环任务")?"循环任务":service.contains("充电保护参数扩展")?"充电保护参数":service.contains("功率限制参数扩展")?"功率保护参数":service;
            if("chuangmi.plug.212a01".equals(device.optString("model"))&&p.optInt("siid")==4&&!Arrays.asList("on-duration","off-duration","task-switch").contains(p.optString("name"))){id+="-countdown";title="按键倒计时";}
            Group g=map.get(id);if(g==null){g=new Group(id,title);map.put(id,g);}g.properties.add(p);
        }return new ArrayList<>(map.values());}
    static List<JSONObject> writeOrder(List<JSONObject> fields){List<JSONObject> out=new ArrayList<>();for(JSONObject p:fields)if(!"bool".equals(p.optString("format")))out.add(p);for(JSONObject p:fields)if("bool".equals(p.optString("format")))out.add(p);return out;}
}
