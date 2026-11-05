package dev.homecontrol.app;
import java.util.*;
public final class HaierTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Haier policy check "+checks);}
 public static void main(String[] args){
  Map<String,String> a=new HashMap<>();check(HaierPolicy.status(a).equals("电源待确认 · 照明待确认"));
  a.put("onOffStatus","false");a.put("lightingStatus","false");a.put("windSpeedLevel","0");check(HaierPolicy.status(a).equals("已关机 · 照明关"));
  a.put("onOffStatus","true");a.put("lightingStatus","true");a.put("windSpeedLevel","3");check(HaierPolicy.status(a).equals("运行中 · 照明开 · 风速 3"));
  a.remove("windSpeedLevel");check(!HaierPolicy.status(a).contains("null"));
  check(HaierPolicy.isHood("吸油烟机","CXW-358-IQ75Z1"));check(!HaierPolicy.isHood("遥控器","virtual"));check(!HaierPolicy.isHood("","CXW-358-IQ75Z1"));
  System.out.println(checks+" Haier read-only policy checks passed");
 }
}
