package dev.homecontrol.app;
import org.json.*;
/** Run against Android's JSON implementation; uses local synthetic fixtures only. */
public final class HaierSnapshotCheck {
    static int count;static void check(boolean yes){count++;if(!yes)throw new AssertionError("Snapshot check "+count);}
    public static void main(String[] args)throws Exception {
        JSONObject previous=new JSONObject("{\"id\":\"haier:test\",\"source\":\"haier_cloud\",\"subtitle\":\"已关机\",\"updateTime\":123,\"capabilities\":[{\"key\":\"lightingStatus\",\"value\":\"false\",\"writable\":true}]}");
        JSONObject incoming=new JSONObject("{\"id\":\"haier:test\",\"source\":\"haier_cloud\",\"subtitle\":\"读取失败\",\"capabilities\":[]}");
        JSONObject kept=HaierSnapshot.retainModel(previous,new JSONObject(incoming.toString()),false);
        check(kept.getJSONArray("capabilities").length()==1);check("已关机".equals(kept.getString("subtitle")));check(kept.getLong("capabilitiesTime")==123);check(kept.getBoolean("statusReadFailed"));
        kept.getJSONArray("capabilities").getJSONObject(0).put("value","true");check("false".equals(previous.getJSONArray("capabilities").getJSONObject(0).getString("value")));
        check(HaierSnapshot.retainModel(previous,new JSONObject(incoming.toString()),true).getJSONArray("capabilities").length()==0);
        incoming.put("id","haier:other");check(HaierSnapshot.retainModel(previous,new JSONObject(incoming.toString()),false).getJSONArray("capabilities").length()==0);
        incoming.put("id","haier:test");previous.put("source","other");check(HaierSnapshot.retainModel(previous,incoming,false).getJSONArray("capabilities").length()==0);
        System.out.println(count+" Android snapshot retention checks passed; synthetic fixtures only");
    }
}
