package dev.homecontrol.app;

import org.json.*;
import java.io.IOException;
import java.util.Locale;

/** Independent observed wire format. Programs must be fetched for this exact model by the service. */
final class KitchenProgram {
    final String model;final byte[] data;final boolean microwave;
    KitchenProgram(String model,String hex)throws IOException {
        this.model=model;microwave="xiaomi.microwave.cm20l".equals(model);
        if(!microwave&&!"chunmi.cooker.normal2".equals(model))throw new IOException("此型号的扩展烹饪程序尚未验证");
        if(hex==null||!hex.matches("(?i)[0-9a-f]{64,4096}")||(hex.length()&1)!=0)throw new IOException("此菜单未提供有效的设备程序");
        data=new byte[hex.length()/2];for(int i=0;i<data.length;i++)data[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        if(microwave&&(u(0)!=5||u(1)!=1||data.length<36))throw new IOException("微波炉程序型号不匹配");
        if(time()<0||time()>limit()||minimum()>maximum()||maximum()>limit())throw new IOException("设备程序的时间范围无效");
    }
    int u(int i){return data[i]&255;}
    int time(){return microwave?u(8)*60+u(9):u(3)*60+u(4);}
    int minimum(){return microwave?u(12)*60+u(13):u(7)*60+u(8);}
    int maximum(){return microwave?u(10)*60+u(11):u(5)*60+u(6);}
    int limit(){return microwave?6000:1440;}
    boolean adjustableTime(){return maximum()>minimum();}
    long menuId(){return microwave?((long)u(3)<<24)|((long)u(4)<<16)|((long)u(5)<<8)|u(6):u(0)*256+u(1);}
    int fire(){return microwave?u(20):-1;}
    int weight(){return microwave?u(28)*256+u(29):0;}
    int minWeight(){return microwave?u(32)*256+u(33):0;}
    int maxWeight(){return microwave?u(30)*256+u(31):0;}
    boolean adjustableWeight(){return microwave&&u(18)<=2&&maxWeight()>minWeight()&&menuId()!=1&&menuId()!=2&&menuId()!=17&&menuId()!=18;}
    JSONObject describe(String name)throws Exception {JSONArray options=weightOptions();return new JSONObject().put("name",name).put("time",time()).put("minTime",minimum()).put("maxTime",maximum()).put("timeEditable",adjustableTime()&&!adjustableWeight()).put("unit",microwave?"秒":"分钟").put("fire",fire()).put("fireEditable",microwave&&menuId()==1).put("weight",weight()).put("weightUnit",microwave?(u(18)==1?"份":u(18)==2?"ml":"g"):"").put("minWeight",minWeight()).put("maxWeight",maxWeight()).put("weightEditable",adjustableWeight()&&options.length()>0).put("weightOptions",options);}
    String configure(JSONObject input)throws Exception {
        if(input.has("time")){int t=integer(input,"time");if(t!=time()&&(!adjustableTime()||t<minimum()||t>maximum()||t>limit()))throw new IOException("烹饪时间超出此菜单允许范围");int pos=microwave?8:3;data[pos]=(byte)(t/60);data[pos+1]=(byte)(t%60);}
        if(input.has("fire")){int f=integer(input,"fire");if(f!=fire()&&(!microwave||menuId()!=1||f<0||f>4))throw new IOException("此菜单不支持选择该火力");if(microwave)data[20]=(byte)f;}
        if(input.has("weight")){int w=integer(input,"weight");if(w!=weight()&&(!adjustableWeight()||w<minWeight()||w>maxWeight()||(w-minWeight())%Math.max(1,u(34)*256+u(35))!=0))throw new IOException("食材重量超出此菜单允许范围");if(microwave){if(w!=weight()){if(input.has("time"))throw new IOException("此菜单按份量计算时间，请勿同时指定时长");int seconds=weightTime(w);data[8]=(byte)(seconds/60);data[9]=(byte)(seconds%60);}data[28]=(byte)(w>>8);data[29]=(byte)w;}}
        // No delayed start or permanent menu synchronization as a side effect of a start operation.
        if(!microwave){data[9]&=0x7f;data[9]=0;data[10]&=0x80;}
        int crc=crc(data,data.length-2);data[data.length-2]=(byte)(crc>>8);data[data.length-1]=(byte)crc;
        StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private JSONArray weightOptions()throws Exception {JSONArray out=new JSONArray();if(!adjustableWeight())return out;int step=u(34)*256+u(35);if(step<=0||(maxWeight()-minWeight())/step>199)return out;for(int w=minWeight();w<=maxWeight();w+=step){try{out.put(new JSONObject().put("weight",w).put("time",weightTime(w)));}catch(IOException ignored){}}return out;}
    private int weightTime(int weight)throws IOException {
        if(data.length<109)throw new IOException("此菜单的份量时间表尚未验证");int multiplier=u(18)==1?1:20,previousW=-1,previousT=0;
        for(int i=0;i<12;i++){int offset=71+i*3,w=u(offset)*multiplier,t=u(offset+1)*60+u(offset+2);if(w<=0||w<=previousW)continue;if(weight==w&&t>0&&t<=limit())return t;if(previousW>=0&&weight<w&&weight>previousW){int value=previousT+(int)((long)(t-previousT)*(weight-previousW)/(w-previousW));if(t>=previousT&&value>0&&value<=limit())return value;}previousW=w;previousT=t;}
        throw new IOException("此份量未返回有效的烹饪时间");
    }
    static int integer(JSONObject input,String key)throws Exception {Object v=input.get(key);if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue())throw new IOException("参数必须为整数");return ((Number)v).intValue();}
    static int crc(byte[] bytes,int count){int crc=0;for(int n=0;n<count;n++){crc^=(bytes[n]&255)<<8;for(int b=0;b<8;b++)crc=(crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1;}return crc&65535;}
}
