package dev.homecontrol.app;
import java.net.*;
import java.util.*;
/** Pure policy for the hosted login navigation and its one-use local callback. */
final class HaierAuthPolicy {
    static boolean callback(URI u){return "http".equals(u.getScheme())&&"homeassistant.local".equals(u.getHost())&&u.getPort()==8123&&"/auth/external/callback".equals(u.getRawPath())&&u.getRawUserInfo()==null&&u.getRawFragment()==null;}
    static String code(URI u,String expected)throws Exception {
        if(!callback(u)||expected==null||expected.isEmpty()||u.getRawQuery()==null)return null;
        Map<String,String> query=new HashMap<>();for(String pair:u.getRawQuery().split("&")){String[] parts=pair.split("=",2);String key=URLDecoder.decode(parts[0],"UTF-8");if(query.containsKey(key))return null;query.put(key,parts.length==2?URLDecoder.decode(parts[1],"UTF-8"):"");}
        String code=query.get("code");return expected.equals(query.get("state"))&&code!=null&&!code.isEmpty()&&code.length()<=8192&&!query.containsKey("error")?code:null;
    }
    static boolean loginAllowed(URI u){String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&(h.equals("haier.com")||h.endsWith(".haier.com")||h.endsWith(".haier.net")||h.endsWith(".haierbros.com"));}
    private HaierAuthPolicy(){}
}
