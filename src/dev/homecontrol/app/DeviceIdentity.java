package dev.homecontrol.app;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
final class DeviceIdentity {
    static String haier(String nativeId){try{return "haier:"+HaierWire.hex(MessageDigest.getInstance("SHA-256").digest(nativeId.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static String mijia(String user,String region,String nativeId){try{return "mijia:"+HaierWire.hex(MessageDigest.getInstance("SHA-256").digest((user+"\n"+region+"\n"+nativeId).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private DeviceIdentity(){}
}
