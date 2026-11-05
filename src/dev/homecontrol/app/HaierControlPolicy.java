package dev.homecontrol.app;
import java.util.*;
final class HaierControlPolicy {
 static boolean allowed(String key,String value,boolean writable,List<String> range,List<String> shown) {
  if(!writable||!range.contains(value))return false;
  if("onOffStatus".equals(key)||"lightingStatus".equals(key))return "true".equals(value)||"false".equals(value);
  return "windSpeedLevel".equals(key)&&("0".equals(value)||shown.contains(value));
 }
 private HaierControlPolicy(){}
}
