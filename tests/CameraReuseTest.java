package dev.homecontrol.app;
public final class CameraReuseTest {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("camera reuse "+checks);}
 public static void main(String[] args){
  check(CameraReusePolicy.allowed("own-camera",1,"net","own-camera",1,"net",true,true,100,50));
  check(!CameraReusePolicy.allowed("other-account-camera",1,"net","own-camera",1,"net",true,true,100,50));
  check(!CameraReusePolicy.allowed("own-camera",2,"net","own-camera",1,"net",true,true,100,50));
  check(!CameraReusePolicy.allowed("own-camera",1,"new-net","own-camera",1,"net",true,true,100,50));
  check(!CameraReusePolicy.allowed("own-camera",1,"net","own-camera",1,"net",false,true,100,50));
  check(!CameraReusePolicy.allowed("own-camera",1,"net","own-camera",1,"net",true,false,100,50));
  check(!CameraReusePolicy.allowed("own-camera",1,"net","own-camera",1,"net",true,true,100,100));
  check(CameraReusePolicy.IDLE_MS==10000);check(!CameraReusePolicy.REUSE_ENABLED&&!CameraReusePolicy.AUTO_RECOVERY_ENABLED);
  check(CameraReusePolicy.recoveryDelay(0,true,false,"视频中断")==1000);check(CameraReusePolicy.recoveryDelay(1,true,false,"视频中断")==2000);check(CameraReusePolicy.recoveryDelay(2,true,false,"视频中断")==4000);
  check(CameraReusePolicy.recoveryDelay(3,true,false,"视频中断")==-1);check(CameraReusePolicy.recoveryDelay(0,false,false,"视频中断")==-1);check(CameraReusePolicy.recoveryDelay(0,true,true,"视频中断")==-1);check(CameraReusePolicy.recoveryDelay(0,true,false,"米家登录已失效")==-1);check(CameraReusePolicy.recoveryDelay(0,true,false,"型号不支持")==-1);
  System.out.println(checks+" camera reuse/foreground/account/retry-boundary checks passed; no media or writes");
 }
}
