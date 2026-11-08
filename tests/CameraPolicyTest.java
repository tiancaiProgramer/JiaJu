package dev.homecontrol.app;
import org.json.*;
import java.io.IOException;
public final class CameraPolicyTest {
 static int count;static void check(boolean b){count++;if(!b)throw new AssertionError("camera policy "+count);}
 interface Work{void run()throws Exception;}static void reject(Work w)throws Exception{boolean bad=false;try{w.run();}catch(IOException e){bad=true;}check(bad);}
 public static void main(String[] ignored)throws Exception{
  for(String ip:new String[]{"10.1.2.3","172.16.1.2","172.31.255.1","192.168.1.2"})check(CameraPolicy.localAddress(ip));
  for(String ip:new String[]{"127.0.0.1","169.254.1.2","8.8.8.8","172.32.0.1","10.0.0.256","10.0.0.1:1234","host",""})check(!CameraPolicy.localAddress(ip));
  JSONObject device=new JSONObject().put("model","chuangmi.camera.069a01").put("localip","192.168.1.1").put("did","fixture-only").put("token","fixture-token");
  String hex="0000000000000000000000000000000000000000000000000000000000000000";
  JSONObject ticket=new JSONObject().put("public_key",hex).put("sign",hex).put("vendor",new JSONObject().put("vendor",4));
  JSONObject cfg=CameraPolicy.config(device,ticket);check(cfg.getString("host").equals("192.168.1.1"));check(!cfg.has("did")&&!cfg.has("token")&&!cfg.has("serviceToken"));
  for(String model:new String[]{"chuangmi.camera.039c01","xiaomi.camera.c302"}){JSONObject expanded=new JSONObject(device.toString()).put("model",model);check(CameraPolicy.config(expanded,ticket).getString("model").equals(model));reject(()->CameraPolicy.config(expanded,new JSONObject(ticket.toString()).put("vendor",new JSONObject().put("vendor",1))));reject(()->CameraPolicy.config(expanded,new JSONObject(ticket.toString()).put("public_key","invalid")));}
  for(String model:new String[]{"chuangmi.camera.039a01","chuangmi.camera.029a02","xiaomi.camera.c302n","chuangmi.camera.039c01.extra"})reject(()->CameraPolicy.config(new JSONObject(device.toString()).put("model",model),ticket));
  reject(()->CameraPolicy.config(new JSONObject(device.toString()).put("model","other.camera.model"),ticket));reject(()->CameraPolicy.config(device,null));reject(()->CameraPolicy.config(device,new JSONObject(ticket.toString()).put("sign","invalid")));
  reject(()->CameraPolicy.config(device,new JSONObject(ticket.toString()).put("vendor",new JSONObject().put("vendor",1))));reject(()->CameraPolicy.config(new JSONObject(device.toString()).put("localip","127.0.0.1"),ticket));
  ticket.getJSONObject("vendor").put("vendor_params",new JSONObject().put("p2p_id","FIXTURE-1-TEST").put("init_string","fixture-initializer"));
  cfg=CameraPolicy.config(new JSONObject(device.toString()).put("localip",""),ticket);check(cfg.getString("host").isEmpty()&&cfg.has("p2pId")&&cfg.has("initString"));
  check(CameraNetwork.sameSubnet("192.168.1.20","192.168.1.30",24));check(!CameraNetwork.sameSubnet("192.168.2.20","192.168.1.30",24));check(CameraNetwork.sameSubnet("10.2.4.8","10.2.9.9",16));check(!CameraNetwork.sameSubnet("10.2.4.8","10.3.9.9",16));check(CameraNetwork.sameSubnet("172.20.30.40","172.20.30.40",32));check(!CameraNetwork.sameSubnet("172.20.30.40","172.20.30.41",32));check(!CameraNetwork.sameSubnet("192.168.1.1","192.168.1.1",0));check(!CameraNetwork.sameSubnet("192.168.1.1","192.168.1.999",24));check(!CameraNetwork.sameSubnet("127.0.0.1","127.0.0.1",8));
  System.out.println(count+" camera scope/address/ticket checks passed; no network or media");
 }
}
