package dev.homecontrol.app;
import java.util.Objects;
final class CameraReusePolicy {
 // Experimental paths stay off until the interrupted on-device checks complete.
 static final boolean REUSE_ENABLED=false,AUTO_RECOVERY_ENABLED=false;
 static final long IDLE_MS=10000;
 static boolean allowed(String id,int quality,String network,String savedId,int savedQuality,String savedNetwork,boolean ready,boolean foreground,long deadline,long now){return foreground&&ready&&id!=null&&id.equals(savedId)&&quality==savedQuality&&Objects.equals(network,savedNetwork)&&now<deadline;}
 static long recoveryDelay(int attempt,boolean foreground,boolean paused,String message){if(!foreground||paused||attempt<0||attempt>=3||message!=null&&(message.contains("登录")||message.contains("型号")||message.contains("安装包")))return -1;return 1000L<<attempt;}
}
