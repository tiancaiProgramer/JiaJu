package dev.homecontrol.app;

import java.net.*;
import java.util.*;

/** Choose the first attempt from current interfaces; cloud failure still permits routed LAN. */
final class CameraNetwork {
    static boolean sameSubnet(String camera,String local,int prefix){
        if(!CameraPolicy.localAddress(camera)||local==null||!local.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")||prefix<1||prefix>32)return false;
        String[] a=camera.split("\\."),b=local.split("\\.");for(int i=0;i<4;i++)if(Integer.parseInt(b[i])>255)return false;
        for(int bit=0;bit<prefix;bit++)if(((Integer.parseInt(a[bit/8])^Integer.parseInt(b[bit/8]))&(1<<(7-bit%8)))!=0)return false;return true;
    }
    static String signature(){try{java.util.List<String> addresses=new java.util.ArrayList<>();Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();if(all==null)return "unknown";while(all.hasMoreElements()){NetworkInterface network=all.nextElement();if(!network.isUp()||network.isLoopback())continue;for(InterfaceAddress address:network.getInterfaceAddresses())addresses.add(network.getName()+":"+address.getAddress().getHostAddress()+"/"+address.getNetworkPrefixLength());}java.util.Collections.sort(addresses);byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(addresses.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:hash)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}catch(Exception unavailable){return "unknown";}}
    static boolean preferLan(String camera){
        if(!CameraPolicy.localAddress(camera))return false;
        try{Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();if(networks==null)return true;while(networks.hasMoreElements()){NetworkInterface network=networks.nextElement();if(!network.isUp()||network.isLoopback())continue;for(InterfaceAddress address:network.getInterfaceAddresses())if(address.getAddress() instanceof Inet4Address&&sameSubnet(camera,address.getAddress().getHostAddress(),address.getNetworkPrefixLength()))return true;}return false;}catch(SocketException denied){return true;}
    }
}
