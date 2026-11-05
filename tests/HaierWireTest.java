package dev.homecontrol.app;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class HaierWireTest {
    private static int count;
    private static void check(boolean yes){count++;if(!yes)throw new AssertionError("Haier wire check "+count);}
    private static void rejected(byte[] bytes)throws Exception{boolean rejected=false;try{HaierWire.frame(new ByteArrayInputStream(bytes));}catch(IOException e){rejected=true;}check(rejected);}
    public static void main(String[] args)throws Exception {
        check("ecc28da8e40ccbb5a4e2c443b714d8477b317c51b897357ff315b7e66623f6b7".equals(HaierWire.sign("/p","{\"x\":\"a b\"}","id","key","123")));
        check("{\"x\":\"\\u4e2d\\u6587\"}".equals(HaierWire.asciiJson("{\"x\":\"中文\"}")));
        check(!HaierWire.sign("/p","a","id","key","123").equals(HaierWire.sign("/p","a","id","key","124")));
        check(HaierWire.gatewayAllowed(new URI("wss://gateway.haier.net")));
        check(HaierWire.gatewayAllowed(new URI("wss://ha-gw.haier.net:59502")));
        for(String u:new String[]{"ws://gateway.haier.net","wss://haier.net.evil.test","wss://evil.test","wss://a.haier.net@evil.test","wss://a.haier.net?redirect=x","wss://a.haier.net#fragment"})check(!HaierWire.gatewayAllowed(new URI(u)));
        HaierWire.Frame f=HaierWire.frame(new ByteArrayInputStream(new byte[]{(byte)129,2,111,107}));check(f.fin&&f.opcode==1&&"ok".equals(new String(f.data,StandardCharsets.UTF_8)));
        f=HaierWire.frame(new ByteArrayInputStream(new byte[]{1,1,97}));check(!f.fin&&f.opcode==1&&f.data[0]==97);
        f=HaierWire.frame(new ByteArrayInputStream(new byte[]{(byte)137,0}));check(f.opcode==9&&f.fin);
        rejected(new byte[]{(byte)193,0});rejected(new byte[]{(byte)129,(byte)128});rejected(new byte[]{(byte)129,3,1});rejected(new byte[]{9,0});rejected(new byte[]{(byte)131,0});
        rejected(new byte[]{(byte)129,127,0,0,0,0,0,32,0,0});
        for(int size:new int[]{0,5,125,126,1000,65535}){
            byte[] input=new byte[size];Arrays.fill(input,(byte)65);ByteArrayOutputStream out=new ByteArrayOutputStream();HaierWire.masked(out,1,input);byte[] wire=out.toByteArray();int header=size<126?2:4;check((wire[0]&255)==129&&(wire[1]&128)!=0&&wire.length==header+4+size);for(int i=0;i<size;i++)if((byte)(wire[header+4+i]^wire[header+i%4])!=input[i])throw new AssertionError("mask mismatch");
        }
        List<String> range=Arrays.asList("0","1","3","4","5"),shown=Arrays.asList("1","3","4");
        check(HaierControlPolicy.allowed("windSpeedLevel","0",true,range,shown));check(HaierControlPolicy.allowed("windSpeedLevel","3",true,range,shown));check(!HaierControlPolicy.allowed("windSpeedLevel","5",true,range,shown));check(!HaierControlPolicy.allowed("windSpeedLevel","2",true,range,shown));check(!HaierControlPolicy.allowed("windSpeedLevel","3",false,range,shown));
        check(HaierControlPolicy.allowed("lightingStatus","true",true,Arrays.asList("true","false"),shown));check(!HaierControlPolicy.allowed("lightingStatus","1",true,range,shown));check(!HaierControlPolicy.allowed("unknown","1",true,range,shown));
        String callback="http://homeassistant.local:8123/auth/external/callback";
        check("test-code".equals(HaierAuthPolicy.code(new URI(callback+"?code=test-code&state=nonce"),"nonce")));
        for(String suffix:new String[]{"?code=test-code&state=wrong","?code=test-code","?code=&state=nonce","?code=test-code&state=nonce&state=nonce","?code=a&code=b&state=nonce","?code=test-code&state=nonce#error","?code=test-code&state=nonce&error=access_denied"})check(HaierAuthPolicy.code(new URI(callback+suffix),"nonce")==null);
        for(String url:new String[]{"http://homeassistant.local.evil:8123/auth/external/callback","http://homeassistant.local:8124/auth/external/callback","http://user@homeassistant.local:8123/auth/external/callback","http://homeassistant.local:8123/auth%2fexternal/callback"})check(!HaierAuthPolicy.callback(new URI(url)));
        check(HaierAuthPolicy.loginAllowed(new URI("https://account.haier.com/oauth/authorize")));
        check(HaierAuthPolicy.loginAllowed(new URI("https://api-cn-sp000362-auth.haierbros.com/path")));
        for(String url:new String[]{"http://account.haier.com","https://account.haier.com.evil","https://haier.com@evil.test","https://account.haier.com:444"})check(!HaierAuthPolicy.loginAllowed(new URI(url)));
        System.out.println(count+" independent Haier wire/control checks passed");
    }
}
