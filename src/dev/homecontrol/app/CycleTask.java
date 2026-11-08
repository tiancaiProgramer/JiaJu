package dev.homecontrol.app;

import java.io.IOException;

/** cuco.plug.v3 device format: on/off minutes; weekdays; start/end minutes; mode. */
final class CycleTask {
    final int onMinutes,offMinutes,startMinutes,endMinutes,mode;final String days;
    CycleTask(String encoded)throws IOException{String[] values=(encoded==null?"":encoded).split(";",-1);if(values.length!=6)throw new IOException("设备返回的循环任务格式暂不支持");try{onMinutes=Integer.parseInt(values[0]);offMinutes=Integer.parseInt(values[1]);days=values[2];startMinutes=Integer.parseInt(values[3]);endMinutes=Integer.parseInt(values[4]);mode=Integer.parseInt(values[5]);validate(onMinutes,offMinutes,days,startMinutes,endMinutes,mode);}catch(NumberFormatException e){throw new IOException("循环任务时间无效");}}
    static void validate(int on,int off,String days,int start,int end,int mode)throws IOException{if(on<1||on>1440||off<1||off>1440||start<0||start>1439||end<0||end>1439||start==end||!days.matches("(?:[0-7](?:,[0-7])*)?")||mode<0)throw new IOException("循环时长须为 1～1440 分钟，开始与结束时间不能相同");String[] parts=days.isEmpty()?new String[0]:days.split(",");java.util.Set<String> unique=new java.util.HashSet<>();for(String day:parts)if(!unique.add(day))throw new IOException("重复日期无效");}
    String encode(int on,int off,int start,int end)throws IOException{validate(on,off,days,start,end,mode);return on+";"+off+";"+days+";"+start+";"+end+";"+mode;}
}
