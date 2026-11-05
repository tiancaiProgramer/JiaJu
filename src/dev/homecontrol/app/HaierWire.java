package dev.homecontrol.app;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Community U+ wire format. No accounts or Android dependencies. */
final class HaierWire {
    static String sign(String path,String body,String appId,String appKey,String timestamp)throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest((path+body.replace("\t","").replace("\r","").replace("\n","").replace(" ","")+appId+appKey+timestamp).getBytes(StandardCharsets.UTF_8)));
    }
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    static String asciiJson(String json){StringBuilder b=new StringBuilder();for(char c:json.toCharArray()){if(c>127)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}return b.toString();}
    static boolean gatewayAllowed(URI u){String h=u.getHost();return "wss".equals(u.getScheme())&&h!=null&&(h.endsWith(".haier.net")||h.endsWith(".haier.com"))&&u.getRawUserInfo()==null&&(u.getPort()==-1||(u.getPort()>0&&u.getPort()<=65535))&&u.getRawQuery()==null&&u.getRawFragment()==null;}
    static void masked(OutputStream out,int opcode,byte[] payload)throws IOException {
        if(payload.length>65535)throw new IOException("控制报文过大");byte[] mask=new byte[4];new SecureRandom().nextBytes(mask);
        out.write(128|opcode);if(payload.length<126)out.write(128|payload.length);else{out.write(128|126);out.write(payload.length>>8);out.write(payload.length);}
        out.write(mask);for(int i=0;i<payload.length;i++)out.write(payload[i]^mask[i%4]);out.flush();
    }
    static final class Frame {final int opcode;final boolean fin;final byte[] data;Frame(int o,boolean f,byte[] d){opcode=o;fin=f;data=d;}}
    static Frame frame(InputStream in)throws IOException {
        int a=read(in),b=read(in);if((a&112)!=0||(b&128)!=0)throw new IOException("网关帧无效");long n=b&127;
        if(n==126)n=(read(in)<<8)|read(in);else if(n==127){n=0;for(int i=0;i<8;i++){int v=read(in);if(n>1048576)throw new IOException("网关报文过大");n=(n<<8)|v;}}
        if(n>1048576)throw new IOException("网关报文过大");int op=a&15;boolean fin=(a&128)!=0;
        if((op>=8&&(!fin||n>125))||!(op==0||op==1||op==2||op==8||op==9||op==10))throw new IOException("网关帧无效");
        byte[] data=new byte[(int)n];int p=0;while(p<data.length){int k=in.read(data,p,data.length-p);if(k<0)throw new EOFException();p+=k;}return new Frame(op,fin,data);
    }
    private static int read(InputStream in)throws IOException{int v=in.read();if(v<0)throw new EOFException();return v;}
    private HaierWire(){}
}
