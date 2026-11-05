package dev.homecontrol.app;
import org.json.*;
/** A temporary model-query failure must not discard the last known controls. */
final class HaierSnapshot {
    static JSONObject retainModel(JSONObject previous,JSONObject incoming,boolean loaded)throws JSONException {
        if(loaded||previous==null||!incoming.optString("id").equals(previous.optString("id"))||!"haier_cloud".equals(previous.optString("source")))return incoming;
        JSONArray caps=previous.optJSONArray("capabilities");if(caps==null||caps.length()==0)return incoming;
        incoming.put("capabilities",new JSONArray(caps.toString())).put("subtitle",previous.optString("subtitle"))
            .put("capabilitiesTime",previous.optLong("capabilitiesTime",previous.optLong("updateTime")))
            .put("statusReadFailed",true);
        return incoming;
    }
    private HaierSnapshot(){}
}
