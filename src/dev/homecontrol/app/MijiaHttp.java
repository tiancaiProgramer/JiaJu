package dev.homecontrol.app;

import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No automatic redirects/retries or logging: writes are sent once only. */
final class MijiaHttp {
    interface Trust {boolean allowed(URI uri);}
    static final class Reply {final int code;final String body;final Map<String,List<String>> headers;Reply(int c,String b,Map<String,List<String>> h){code=c;body=b;headers=h;}String location(){for(Map.Entry<String,List<String>> e:headers.entrySet())if("location".equalsIgnoreCase(e.getKey())&&!e.getValue().isEmpty())return e.getValue().get(0);return null;}}
    private volatile HttpsURLConnection active;
    private volatile boolean cancelled;
    Reply request(String url,String body,Map<String,String> headers,java.net.CookieManager jar,Trust trust,int limit,int timeout)throws Exception {
        URI uri=new URI(url);if(!trust.allowed(uri))throw new IOException("米家接口地址校验失败");if(cancelled)throw new InterruptedIOException("已取消连接");
        HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();active=c;
        try{c.setConnectTimeout(8000);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(false);c.setRequestMethod(body==null?"GET":"POST");c.setRequestProperty("Accept-Encoding","identity");for(Map.Entry<String,String> e:headers.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
            if(jar!=null)for(Map.Entry<String,List<String>> e:jar.get(uri,Collections.emptyMap()).entrySet())c.setRequestProperty(e.getKey(),String.join("; ",e.getValue()));
            if(body!=null){c.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();Map<String,List<String>> response=c.getHeaderFields();if(jar!=null)jar.put(uri,response);String text="";
            if(code>=200&&code<300){try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(cancelled)throw new InterruptedIOException();if(out.size()+n>limit)throw new IOException("米家响应过大");out.write(b,0,n);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);}}
            return new Reply(code,text,response);
        }finally{active=null;c.disconnect();}
    }
    void cancel(){cancelled=true;HttpsURLConnection c=active;if(c!=null)c.disconnect();}
}
