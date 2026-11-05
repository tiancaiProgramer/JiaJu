package dev.homecontrol.app;
import java.util.Map;
final class HaierPolicy {
 static String status(Map<String,String> attributes) {
  String power=attributes.get("onOffStatus"),light=attributes.get("lightingStatus"),speed=attributes.get("windSpeedLevel");
  String result="true".equals(power)?"运行中":"false".equals(power)?"已关机":"电源待确认";
  result+=" · "+("true".equals(light)?"照明开":"false".equals(light)?"照明关":"照明待确认");
  if("true".equals(power)&&speed!=null)result+=" · 风速 "+speed;return result;
 }
 static boolean isHood(String title,String model) {return title!=null&&!title.isEmpty()&&model!=null&&(model.startsWith("CXW-")||title.contains("烟机"));}
 private HaierPolicy(){}
}
