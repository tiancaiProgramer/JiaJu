package dev.homecontrol.app;
import java.util.*;
/** Main-thread user intents; coalesce waiting values, never retry a sent write. */
final class HaierPanelQueue {
    static final class Command {final String key,value;Command(String k,String v){key=k;value=v;}}
    private final LinkedHashMap<String,Command> waiting=new LinkedHashMap<>();private Command active;
    void offer(String key,String value){if(active!=null&&key.equals(active.key)&&value.equals(active.value))waiting.remove(key);else waiting.put(key,new Command(key,value));}
    Command next(){if(active!=null||waiting.isEmpty())return null;String key=waiting.keySet().iterator().next();active=waiting.remove(key);return active;}
    String desired(String key,String fallback){Command last=waiting.get(key);return last!=null?last.value:active!=null&&key.equals(active.key)?active.value:fallback;}
    void completed(Command command){if(active==command)active=null;}
    void clearWaiting(){waiting.clear();}
}
