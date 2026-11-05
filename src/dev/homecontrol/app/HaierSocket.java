package dev.homecontrol.app;

import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Short-lived verified TLS WebSocket. A write is sent once and never retried. */
final class HaierSocket implements AutoCloseable {
    private final SSLSocket socket;private final InputStream in;private final OutputStream out;
    HaierSocket(URI endpoint,String token)throws Exception {this(endpoint,token,null);}
    HaierSocket(URI endpoint,String token,String appId)throws Exception {
        if(!HaierWire.gatewayAllowed(endpoint))throw new IOException("海尔网关地址未通过校验");
        Socket tcp=new Socket();SSLSocket ssl=null;
        try {
            int port=endpoint.getPort()==-1?443:endpoint.getPort();tcp.connect(new InetSocketAddress(endpoint.getHost(),port),8000);tcp.setSoTimeout(8000);
            ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,endpoint.getHost(),port,true);
            SSLParameters params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(params);ssl.startHandshake();
            socket=ssl;in=socket.getInputStream();out=socket.getOutputStream();
            byte[] nonce=new byte[16];new SecureRandom().nextBytes(nonce);String key=Base64.getEncoder().encodeToString(nonce);
            String path=endpoint.getRawPath();if(path==null||path.isEmpty())path="";path=path.replaceAll("/+$","")+"/userag";
            path+="?token="+URLEncoder.encode(token,"UTF-8")+"&agClientId="+URLEncoder.encode(token,"UTF-8");
            if(appId!=null)path+="&appId="+URLEncoder.encode(appId,"UTF-8");
            out.write(("GET "+path+" HTTP/1.1\r\nHost: "+endpoint.getHost()+(port==443?"":":"+port)+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
            ByteArrayOutputStream headers=new ByteArrayOutputStream();int state=0;
            while(state<4){int b=in.read();if(b<0||headers.size()>16384)throw new IOException("网关握手失败");headers.write(b);state=(state==0&&b==13)?1:(state==1&&b==10)?2:(state==2&&b==13)?3:(state==3&&b==10)?4:0;}
            String[] lines=headers.toString("US-ASCII").split("\r\n");if(!lines[0].startsWith("HTTP/1.1 101 "))throw new IOException("网关连接未授权");
            Map<String,String> fields=new HashMap<>();for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)fields.put(lines[i].substring(0,colon).toLowerCase(Locale.ROOT),lines[i].substring(colon+1).trim());}
            String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            if(!accept.equals(fields.get("sec-websocket-accept"))||!"websocket".equalsIgnoreCase(fields.get("upgrade"))||!Arrays.asList(fields.getOrDefault("connection","").toLowerCase(Locale.ROOT).split("\\s*,\\s*")).contains("upgrade"))throw new IOException("网关握手校验失败");
        }catch(Exception e){if(ssl!=null)ssl.close();else tcp.close();throw e;}
    }
    void send(String message)throws IOException{HaierWire.masked(out,1,message.getBytes(StandardCharsets.UTF_8));}
    String receive(long deadline)throws IOException {
        ByteArrayOutputStream assembled=new ByteArrayOutputStream();boolean fragmented=false;
        while(System.currentTimeMillis()<deadline){socket.setSoTimeout((int)Math.max(1,Math.min(8000,deadline-System.currentTimeMillis())));HaierWire.Frame f=HaierWire.frame(in);
            if(f.opcode==8)throw new IOException("网关连接已关闭");if(f.opcode==9){HaierWire.masked(out,10,f.data);continue;}if(f.opcode==10)continue;
            if(f.opcode==2||(!fragmented&&f.opcode!=1)||(fragmented&&f.opcode!=0))throw new IOException("网关消息格式不支持");
            assembled.write(f.data);if(assembled.size()>1048576)throw new IOException("网关报文过大");if(f.fin)return assembled.toString("UTF-8");fragmented=true;
        }throw new SocketTimeoutException("网关确认超时");
    }
    @Override public void close()throws IOException{socket.close();}
}
