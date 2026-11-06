package dev.homecontrol.app;

import java.util.*;

public final class PresentationTest {
    private static int checks;
    static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    static List<String> list(String... ids) {return Arrays.asList(ids);}
    public static void main(String[] args)throws Exception {
        List<String> ids=list("phone","camera","rice","speaker","tv","oven","watch");
        DevicePresentation model=DevicePresentation.migrate(ids,Collections.emptySet(),2);
        check(model.home(ids).equals(list("phone","camera")),"upgrade preserves the previous visible homepage");
        for(String id:list("rice","speaker","tv","oven"))model.pin(id,true);
        check(model.home(ids).size()==6,"six selected devices are all resident immediately");
        for(String id:list("camera","rice","oven"))model.pin(id,false);
        check(model.home(ids).equals(list("phone","speaker","tv")),"arbitrary three-device homepage has no fixed count cap");
        check(model.all(ids).containsAll(ids)&&model.all(ids).contains("phone"),"all devices includes homepage devices and unpinned devices");
        model.hide("tv",true);
        check(!model.home(ids).contains("tv")&&!model.all(ids).contains("tv")&&model.concealed(ids).equals(list("tv")),"hide removes direct tiles and collects them in the hidden section");
        model.pin(DevicePresentation.HIDDEN,true);
        check(model.home(ids).equals(list("phone","speaker",DevicePresentation.HIDDEN)),"hidden section can be pinned alongside devices");
        model.pin("tv",true);
        check(!model.home(ids).contains("tv"),"hidden device cannot also appear directly on homepage");
        model.move(DevicePresentation.HOME,DevicePresentation.HIDDEN,"phone");
        check(model.home(ids).equals(list(DevicePresentation.HIDDEN,"phone","speaker"))&&model.all(ids).get(0).equals("phone"),"homepage order is independent of full-list order");
        model.move(DevicePresentation.ALL,"watch","phone");
        check(model.all(ids).get(0).equals("watch")&&model.home(ids).get(0).equals(DevicePresentation.HIDDEN),"full-list order is independent of homepage order");
        model.hide("rice",true);model.move(DevicePresentation.CONCEALED,"rice","tv");
        check(model.concealed(ids).equals(list("rice","tv")),"hidden devices have their own order");
        model.hide("tv",false);
        check(model.all(ids).contains("tv")&&!model.home(ids).contains("tv"),"unhide restores full-list availability without silently pinning");
        List<String> after=new ArrayList<>(ids);after.add("new-lock");List<String> homeBefore=model.home(ids);model.discover(after);
        check(model.all(after).get(model.all(after).size()-1).equals("new-lock")&&model.home(after).equals(homeBefore),"new devices append to full list without changing homepage selection");
        List<String> disconnected=list("camera","rice","tv","oven","watch","new-lock");
        check(model.home(disconnected).equals(homeBefore)&&model.home(after).equals(homeBefore),"missing pinned devices remain visible with their saved order");
        DevicePresentation migrated=DevicePresentation.migrate(ids,new HashSet<>(list("camera")),2);
        check(migrated.home(ids).equals(list("phone","rice"))&&migrated.hidden.isEmpty()&&migrated.all(ids).contains("camera"),"old unchecked devices migrate as unpinned, not explicitly hidden");
        DevicePresentation empty=new DevicePresentation();empty.discover(list("phone","phone","",null));empty.pin("phone",true);empty.pin("phone",true);
        check(empty.home(list("phone","phone")).equals(list("phone"))&&empty.all(list("phone")).size()==1,"duplicates and empty hidden modules do not create duplicate or empty tiles");
        empty.pin("phone",false);
        check(empty.home(list("phone")).isEmpty()&&empty.all(list("phone")).contains("phone"),"empty homepage is intentional and full list remains available");
        model.hide(DevicePresentation.HIDDEN,true);
        check(!model.hidden.contains(DevicePresentation.HIDDEN)&&!model.move("invalid","phone","tv"),"special module and invalid ordering requests are constrained");
        DevicePresentation persistent=new DevicePresentation();persistent.discover(list("tv","lamp"));persistent.pin("tv",true);
        check(persistent.home(Collections.emptyList()).equals(list("tv")),"pinned television remains a homepage module after all discovery records expire");
        persistent.pin("tv",false);
        check(persistent.home(Collections.emptyList()).isEmpty(),"user can deliberately remove an unavailable pinned module");
        persistent.pin("tv",true);persistent.hide("tv",true);
        check(persistent.home(Collections.emptyList()).isEmpty(),"hiding an unavailable module still removes it from homepage");
        DevicePresentation visibility=new DevicePresentation();visibility.discover(list("lamp","tv","camera"));visibility.place("lamp","home");visibility.place("tv","hidden");visibility.place(DevicePresentation.HIDDEN,"home");
        check(visibility.home(list("lamp","tv")).equals(list("lamp",DevicePresentation.HIDDEN)),"nonempty hidden section can be pinned");
        visibility.place("tv","excluded");check(!visibility.home(list("lamp","tv")).contains(DevicePresentation.HIDDEN)&&!visibility.all(list("lamp","tv")).contains(DevicePresentation.HIDDEN),"empty hidden module disappears in both views while its preference remains saved");
        check(visibility.excluded(list("lamp","tv")).equals(list("tv"))&&!visibility.all(list("lamp","tv")).contains("tv")&&visibility.concealed(list("lamp","tv")).isEmpty(),"excluded devices are editor-only");
        visibility.place("tv","hidden");check(visibility.home(list("lamp","tv")).contains(DevicePresentation.HIDDEN),"hidden module reappears without losing pin choice");
        visibility.place(DevicePresentation.HIDDEN,"excluded");check(!visibility.home(list("lamp","tv")).contains(DevicePresentation.HIDDEN)&&!visibility.all(list("lamp","tv")).contains(DevicePresentation.HIDDEN)&&visibility.concealed(list("lamp","tv")).equals(list("tv")),"module can be suppressed without losing hidden device management");
        visibility.place("tv","home");check(visibility.home(list("lamp","tv")).equals(list("lamp","tv"))&&visibility.hidden.isEmpty()&&visibility.excluded.isEmpty(),"restore to home atomically clears other placements");
        visibility.place("lamp","excluded");check(!visibility.home(Collections.emptyList()).contains("lamp")&&"excluded".equals(visibility.placement("lamp")),"excluded remains excluded when offline");
        visibility.place("lamp","all");check(!visibility.home(list("lamp","tv")).contains("lamp")&&visibility.all(list("lamp","tv")).contains("lamp"),"restore to More does not auto-pin");
        org.json.JSONObject catalog=new org.json.JSONObject("{\"homelist\":[{\"id\":1,\"name\":\"厦门\",\"dids\":[\"unassigned\"],\"roomlist\":[{\"id\":10,\"name\":\"厨房\",\"dids\":[\"cooker\"]}]}],\"share_home_list\":[{\"id\":2,\"name\":\"厦门\",\"roomlist\":[{\"id\":20,\"name\":\"厨房\",\"dids\":[\"shared\"]}]}]}");
        org.json.JSONObject index=DeviceLocation.homesIndex(catalog),cooker=index.getJSONObject("cooker"),shared=index.getJSONObject("shared");
        check(cooker.getString("homeName").equals("厦门")&&cooker.getString("roomName").equals("厨房"),"family and room membership read from directory");
        check(!DeviceLocation.home(cooker).equals(DeviceLocation.home(shared)),"same-name homes retain distinct IDs");
        check(DeviceLocation.room(index.getJSONObject("unassigned")).equals(DeviceLocation.UNKNOWN),"unassigned room remains explicit rather than assigned to another room");
        cooker.put("category","mijia");shared.put("category","haier");
        check(DeviceLocation.matches(cooker,"mijia","1","10")&&!DeviceLocation.matches(shared,"mijia","2","20"),"filters are provider-scoped");
        check(DeviceLocation.homes(Arrays.asList(cooker,shared),"mijia").size()==2&&DeviceLocation.rooms(Arrays.asList(cooker,shared),"haier","2").get("20").equals("厨房"),"each provider keeps its independent native classification");
        check(DeviceLocation.meta(cooker).equals("米家 · 厦门 · 厨房"),"card metadata includes source and location");
        org.json.JSONObject moved=new org.json.JSONObject(cooker.toString());DeviceLocation.copy(new org.json.JSONObject().put("homeId","3"),moved);check(moved.optString("homeName").isEmpty()&&moved.optString("roomName").isEmpty(),"moving to an unnamed home never keeps the previous location label");
        System.out.println(checks+" device-presentation behavior checks passed");
    }
}
