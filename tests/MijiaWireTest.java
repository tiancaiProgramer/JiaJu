package dev.homecontrol.app;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
public final class MijiaWireTest {
    static int count;static void check(boolean v){count++;if(!v)throw new AssertionError("Mijia protocol check "+count);}
    static void rejects(String format,String value){boolean yes=false;try{MijiaValue.parse(format,value);}catch(IllegalArgumentException expected){yes=true;}check(yes);}
    public static void main(String[] args)throws Exception{
        String security="MDEyMzQ1Njc4OWFiY2RlZg==",nonce=MijiaWire.nonce(new byte[]{0,1,2,3,4,5,6,7},1700000000000L),signed=MijiaWire.signedNonce(security,nonce);check("AAECAwQFBgcBsFUV".equals(nonce));check("jJSDtjhNmSIEWHCVcTbRRb9Y2uivglyOL9fqb3Izzbs=".equals(signed));
        LinkedHashMap<String,String> data=new LinkedHashMap<>();data.put("data","{\"test\":1}");check("ZOgPNz6k9XhrFgdM0YBzwhy75BE=".equals(MijiaWire.signature("/home/device_list",signed,data)));
        byte[] key=MijiaWire.un64(signed),input="测试 wire payload + / =".getBytes(StandardCharsets.UTF_8),combined=new byte[1024+input.length];System.arraycopy(input,0,combined,1024,input.length);Cipher oracle=Cipher.getInstance("ARCFOUR");oracle.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"ARCFOUR"));byte[] encrypted=oracle.doFinal(combined);check(Arrays.equals(Arrays.copyOfRange(encrypted,1024,encrypted.length),MijiaWire.rc4(key,input)));
        Map<String,String> fields=MijiaWire.encrypt("/home/device_list",data.get("data"),security,nonce);check(fields.keySet().equals(new LinkedHashSet<>(Arrays.asList("data","rc4_hash__","signature","ssecurity","_nonce"))));check(data.get("data").equals(MijiaWire.decrypt(security,nonce,fields.get("data"))));check(!data.get("data").equals(fields.get("data")));check(fields.get("_nonce").equals(nonce));
        for(String u:new String[]{"https://account.xiaomi.com/pass/serviceLogin","https://ak.account.xiaomi.com/x","https://ak.lp.account.xiaomi.com/x","https://sts.api.io.mi.com/sts"})check(MijiaWire.accountUrl(new URI(u)));
        for(String u:new String[]{"http://account.xiaomi.com/x","https://account.xiaomi.com.evil.test/x","https://evilaccount.xiaomi.com/x","https://account.xiaomi.com@evil.test/x","https://account.xiaomi.com:444/x","file:///etc/x","https://evil.test/account.xiaomi.com"})check(!MijiaWire.accountUrl(new URI(u)));
        check(MijiaWire.api("cn").equals("https://api.io.mi.com/app"));check(MijiaWire.api("de").equals("https://de.api.io.mi.com/app"));boolean invalid=false;try{MijiaWire.api("evil.test");}catch(IllegalArgumentException e){invalid=true;}check(invalid);
        check(MijiaValue.equivalent(1,1.0));check(!MijiaValue.equivalent(true,"true"));check(MijiaValue.allowed("bool",true,Collections.emptyList(),Collections.emptyList()));check(!MijiaValue.allowed("bool","true",Collections.emptyList(),Collections.emptyList()));
        check(MijiaValue.allowed("float",20.5,Collections.emptyList(),Arrays.asList(16,30,.5)));check(!MijiaValue.allowed("float",20.25,Collections.emptyList(),Arrays.asList(16,30,.5)));check(!MijiaValue.allowed("float",31,Collections.emptyList(),Arrays.asList(16,30,.5)));check(!MijiaValue.allowed("uint8",256,Collections.emptyList(),Collections.emptyList()));
        check(MijiaValue.allowed("uint8",3,Arrays.asList(1,3,4),Collections.emptyList()));check(!MijiaValue.allowed("uint8",2,Arrays.asList(1,3,4),Collections.emptyList()));check(!MijiaValue.allowed("float",Double.NaN,Collections.emptyList(),Collections.emptyList()));
        rejects("uint8","-1");rejects("int16","3.5");rejects("float","NaN");rejects("uint64","18446744073709551615");rejects("int64","9223372036854775808");
        check(((Number)MijiaValue.parse("uint32","4294967295")).longValue()==4294967295L);check(MijiaWire.form(Collections.singletonMap("a","中 &+/")).equals("a=%E4%B8%AD+%26%2B%2F"));
        check(!DeviceIdentity.mijia("1","cn","device").equals(DeviceIdentity.mijia("2","cn","device")));check(!DeviceIdentity.mijia("1","cn","device").equals(DeviceIdentity.mijia("1","de","device")));check(DeviceIdentity.mijia("1","cn","device").equals(DeviceIdentity.mijia("1","cn","device")));
        System.out.println(count+" Mi Home protocol/command-boundary checks passed");
    }
}
