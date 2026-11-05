package dev.homecontrol.app;

import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Independent Mi Home protocol adapter. See META-INF/NOTICE-mijia.txt. */
final class MijiaWire {
    static final List<String> REGIONS=Collections.unmodifiableList(Arrays.asList("cn","de","us","ru","tw","sg","in","i2"));
    static String api(String region){if(!REGIONS.contains(region))throw new IllegalArgumentException("不支持的米家地区");return "https://"+("cn".equals(region)?"":region+".")+"api.io.mi.com/app";}
    static boolean accountUrl(URI uri){if(!secure(uri)||uri.getHost()==null)return false;String host=uri.getHost();return host.equals("account.xiaomi.com")||host.endsWith(".account.xiaomi.com")||host.equals("account.xiaomi.cn")||host.endsWith(".account.xiaomi.cn")||Arrays.asList("sts.api.io.mi.com","api.io.mi.com","verify.sec.xiaomi.com").contains(host);}
    static boolean specUrl(URI uri){return secure(uri)&&Arrays.asList("miot-spec.org","spec.miot-spec.com").contains(uri.getHost());}
    static boolean secure(URI uri){return uri!=null&&"https".equals(uri.getScheme())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);}
    static String nonce(byte[] random,long millis){if(random.length!=8||millis<0)throw new IllegalArgumentException();return b64(ByteBuffer.allocate(12).put(random).putInt((int)(millis/60000)).array());}
    static String signedNonce(String security,String nonce){try{return b64(MessageDigest.getInstance("SHA-256").digest(concat(un64(security),un64(nonce))));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    static String signature(String path,String signed,Map<String,String> fields){try{if(!path.startsWith("/")||path.contains("?")||path.contains("#"))throw new IllegalArgumentException();StringBuilder s=new StringBuilder("POST&").append(path);for(Map.Entry<String,String> e:fields.entrySet())s.append('&').append(e.getKey()).append('=').append(e.getValue());s.append('&').append(signed);return b64(MessageDigest.getInstance("SHA-1").digest(s.toString().getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    static LinkedHashMap<String,String> encrypt(String path,String data,String security,String nonce){String signed=signedNonce(security,nonce);LinkedHashMap<String,String> fields=new LinkedHashMap<>();fields.put("data",data);fields.put("rc4_hash__",signature(path,signed,fields));for(Map.Entry<String,String> e:fields.entrySet())e.setValue(b64(rc4(un64(signed),e.getValue().getBytes(StandardCharsets.UTF_8))));fields.put("signature",signature(path,signed,fields));fields.put("ssecurity",security);fields.put("_nonce",nonce);return fields;}
    static String decrypt(String security,String nonce,String body){return new String(rc4(un64(signedNonce(security,nonce)),un64(body.trim())),StandardCharsets.UTF_8);}
    static byte[] rc4(byte[] key,byte[] input){if(key.length==0)throw new IllegalArgumentException();int[] box=new int[256];for(int i=0;i<256;i++)box[i]=i;int j=0;for(int i=0;i<256;i++){j=(j+box[i]+(key[i%key.length]&255))&255;int t=box[i];box[i]=box[j];box[j]=t;}byte[] out=new byte[input.length];int i=0;j=0;for(int n=0;n<1024+input.length;n++){i=(i+1)&255;j=(j+box[i])&255;int t=box[i];box[i]=box[j];box[j]=t;if(n>=1024)out[n-1024]=(byte)(input[n-1024]^box[(box[i]+box[j])&255]);}return out;}
    static String form(Map<String,String> fields){StringBuilder out=new StringBuilder();try{for(Map.Entry<String,String> e:fields.entrySet()){if(out.length()>0)out.append('&');out.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}}catch(Exception e){throw new IllegalArgumentException(e);}return out.toString();}
    static String b64(byte[] value){return Base64.getEncoder().encodeToString(value);}
    static byte[] un64(String value){return Base64.getDecoder().decode(value);}
    private static byte[] concat(byte[] a,byte[] b){byte[] out=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;}
    private MijiaWire(){}
}
