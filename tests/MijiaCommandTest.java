package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;
public final class MijiaCommandTest {
    static int count;static void check(boolean yes){count++;if(!yes)throw new AssertionError("Mijia command check "+count);}
    interface Work{void run()throws Exception;}
    static void rejects(Work work)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}check(rejected);}
    public static void main(String[] args)throws Exception{
        JSONObject cap=new JSONObject("{\"key\":\"p:2:1\",\"siid\":2,\"piid\":1,\"format\":\"bool\",\"writable\":true}");
        MijiaCommand command=MijiaCommand.create("fixture",cap,true,false);check("/miotspec/prop/set".equals(command.path));check(command.data.getJSONArray("params").length()==1);check(Boolean.TRUE.equals(command.data.getJSONArray("params").getJSONObject(0).get("value")));
        JSONObject ack=new JSONObject("{\"result\":[{\"did\":\"fixture\",\"siid\":2,\"piid\":1,\"code\":0}]}");command.accepted(ack);check(true);
        for(String change:new String[]{"did","siid","piid","code"}){JSONObject broken=new JSONObject(ack.toString());broken.getJSONArray("result").getJSONObject(0).put(change,change.equals("did")?"other":-1);rejects(()->command.accepted(broken));}
        rejects(()->command.accepted(new JSONObject()));JSONObject duplicate=new JSONObject(ack.toString());duplicate.getJSONArray("result").put(duplicate.getJSONArray("result").getJSONObject(0));rejects(()->command.accepted(duplicate));
        rejects(()->MijiaCommand.create("fixture",cap,"true",false));JSONObject readOnly=new JSONObject(cap.toString()).put("writable",false);rejects(()->MijiaCommand.create("fixture",readOnly,true,false));
        JSONObject action=new JSONObject("{\"key\":\"a:2:3\",\"siid\":2,\"aiid\":3,\"supported\":true,\"inputs\":[]}");MijiaCommand act=MijiaCommand.create("fixture",action,new JSONArray(),true);check("/miotspec/action".equals(act.path));check(act.data.getJSONObject("params").getJSONArray("in").length()==0);JSONObject actAck=new JSONObject("{\"result\":{\"did\":\"fixture\",\"siid\":2,\"aiid\":3,\"code\":0}}");act.accepted(actAck);check(true);
        actAck.getJSONObject("result").put("aiid",4);rejects(()->act.accepted(actAck));rejects(()->MijiaCommand.create("fixture",action,new JSONArray().put(1),true));
        JSONObject unsupported=new JSONObject(action.toString()).put("supported",false);rejects(()->MijiaCommand.create("fixture",unsupported,new JSONArray(),true));
        JSONObject input=new JSONObject("{\"format\":\"float\",\"range\":[16,30,0.5]}");action.put("inputs",new JSONArray().put(input));check(MijiaCommand.create("fixture",action,new JSONArray().put(20.5),true).data.getJSONObject("params").getJSONArray("in").getDouble(0)==20.5);rejects(()->MijiaCommand.create("fixture",action,new JSONArray().put(20.25),true));rejects(()->MijiaCommand.create("fixture",null,true,false));
        System.out.println(count+" Mi Home schema-bound command/acknowledgement checks passed; no network writes");
    }
}
