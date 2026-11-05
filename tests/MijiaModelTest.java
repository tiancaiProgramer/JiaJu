package dev.homecontrol.app;
import org.json.*;
import java.nio.file.*;
public final class MijiaModelTest {
    static int count;static void check(boolean v){count++;if(!v)throw new AssertionError("Mijia model check "+count);}
    public static void main(String[] args)throws Exception{
        JSONObject spec=new JSONObject(new String(Files.readAllBytes(Paths.get(args.length==0?"tests/fixtures/miot-light.json":args[0])),java.nio.charset.StandardCharsets.UTF_8)),parsed=MijiaModel.parse(spec);JSONArray caps=parsed.getJSONArray("capabilities");JSONObject power=MijiaModel.find(caps,"p:2:1"),brightness=MijiaModel.find(caps,"p:2:2"),mode=MijiaModel.find(caps,"p:2:3");check(power!=null&&power.getBoolean("writable"));check("电源".equals(power.getString("title")));check(MijiaModel.find(caps,"p:1:1")==null);check(!power.has("value"));check(MijiaModel.valid(power,true));check(!MijiaModel.valid(power,"true"));check(MijiaModel.valid(brightness,1));check(!MijiaModel.valid(brightness,0));check(!MijiaModel.valid(brightness,100.1));check(MijiaModel.valid(mode,1));check(!MijiaModel.valid(mode,999));
        JSONObject toggle=MijiaModel.find(parsed.getJSONArray("actions"),"a:2:1");check(toggle!=null&&toggle.getBoolean("supported")&&toggle.getJSONArray("inputs").length()==0);
        JSONArray reply=new JSONArray().put(new JSONObject().put("did","fixture").put("siid",2).put("piid",1).put("code",0).put("value",true));MijiaModel.results(caps,reply,"other",100);check(!power.has("value"));MijiaModel.results(caps,reply,"fixture",101);check(Boolean.TRUE.equals(power.get("value"))&&power.getLong("valueTime")==101&&!power.getBoolean("stale"));check("开".equals(MijiaModel.display(power)));
        JSONArray next=MijiaModel.parse(spec).getJSONArray("capabilities");MijiaModel.retain(next,caps);JSONObject retained=MijiaModel.find(next,"p:2:1");check(Boolean.TRUE.equals(retained.get("value"))&&retained.getBoolean("stale"));JSONArray error=new JSONArray().put(new JSONObject().put("did","fixture").put("siid",2).put("piid",1).put("code",-704042011).put("value",false));MijiaModel.results(next,error,"fixture",200);check(Boolean.TRUE.equals(retained.get("value"))&&retained.getLong("valueTime")==101);check(retained.getBoolean("stale"));check(!MijiaModel.find(next,"p:2:2").has("value"));
        power.put("value",false);check(Boolean.TRUE.equals(retained.get("value")));
        spec.put("_translations",new JSONObject().put("zh_cn",new JSONObject().put("service:002","客厅灯控制").put("service:002:property:001","灯光开关")));check("灯光开关".equals(MijiaModel.find(MijiaModel.parse(spec).getJSONArray("capabilities"),"p:2:1").getString("title")));
        JSONObject service=spec.getJSONArray("services").getJSONObject(1);service.getJSONArray("actions").put(new JSONObject().put("iid",99).put("type","urn:miot-spec-v2:action:unsafe-unknown:1").put("in",new JSONArray().put(999)));JSONObject missing=MijiaModel.find(MijiaModel.parse(spec).getJSONArray("actions"),"a:2:99");check(!missing.getBoolean("supported"));
        System.out.println(count+" Mi Home real-schema/state-retention checks passed");
    }
}
