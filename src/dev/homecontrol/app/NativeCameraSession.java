package dev.homecontrol.app;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account secrets stay in the Java vault; only one ephemeral camera ticket crosses a private pipe. */
final class NativeCameraSession implements AutoCloseable {
    interface Listener {void status(String value);void frame(int codec,long timestamp,byte[] annexB)throws Exception;void error(String value);default void diagnostic(String code){} default void metrics(JSONObject values){} }
    private final Context context;private final String id;private final int quality;private final Listener listener;private final AtomicBoolean closed=new AtomicBoolean();private volatile Process process;private volatile OutputStream commands;private volatile boolean ready,pausedAck;private volatile long mediaPackets=-1,commandReplyCount;private final java.util.concurrent.atomic.AtomicLong metricsVersion=new java.util.concurrent.atomic.AtomicLong();private final Object commandLock=new Object();
    NativeCameraSession(Context context,String id,int quality,Listener listener){this.context=context.getApplicationContext();this.id=id;this.quality=quality;this.listener=listener;}
    void start(){new Thread(this::run,"JiaJuCameraRead").start();}
    private void run(){try{
        File binary=new File(context.getApplicationInfo().nativeLibraryDir,"libjiaju_camera.so");if(!binary.isFile())throw new IOException("当前安装包未包含适用的视频组件");Process child;try{child=new ProcessBuilder(binary.getAbsolutePath()).start();}catch(IOException startup){throw new IOException("视频组件启动失败，请重新安装当前版本");}process=child;if(closed.get()){child.destroy();return;}
        new Thread(()->{try(InputStream in=child.getErrorStream()){byte[] buffer=new byte[1024];while(in.read(buffer)>=0){/* Native diagnostics are deliberately discarded. */}}catch(IOException ignored){}},"JiaJuCameraDiscard").start();
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(child.getInputStream()));OutputStream out=child.getOutputStream()){commands=out;
            while(!closed.get()){
                int type=in.readUnsignedByte(),size=in.readInt();long timestamp=in.readLong();if(size<0||size>4*1024*1024)throw new IOException("视频帧长度异常");byte[] payload=new byte[size];in.readFully(payload);
                if(type==1){JSONObject event=new JSONObject(new String(payload,java.nio.charset.StandardCharsets.UTF_8));String name=event.optString("event");
                    if("key".equals(name)){listener.status("正在取得视频授权…");JSONObject config=MijiaCloud.cameraTicket(context,id,event.getString("public"));config.put("quality",quality).put("tryLocal",CameraNetwork.preferLan(config.optString("host")));if(closed.get())return;synchronized(commandLock){out.write((config.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();}listener.status("正在连接摄像头…");}
                    else if("metrics".equals(name)){mediaPackets=event.optLong("mediaPackets",-1);commandReplyCount=event.optLong("commandReplies",0);metricsVersion.incrementAndGet();listener.metrics(event);}else if("cloud_connect".equals(name))listener.status("正在连接跨网络视频通道…");else if("local_fallback".equals(name))listener.status("正在尝试本地视频通道…");else if("connected".equals(name)){ready=true;listener.status("已连接，等待实时画面…");}else if("paused".equals(name)){pausedAck=true;}else if("resumed".equals(name)){pausedAck=false;listener.status("已连接，等待实时画面…");}else if("error".equals(name)){if(!closed.get())listener.diagnostic(event.optString("code"));throw new IOException(message(event.optString("code")));}
                }else if(type==4||type==5)listener.frame(type,timestamp,payload);else throw new IOException("视频通道数据不兼容");
            }
        }
    }catch(Exception failure){if(!closed.get())listener.error(failure instanceof IOException&&failure.getMessage()!=null?failure.getMessage():"视频连接未完成，请重试");}finally{close();}}
    private static String message(String code){if("local_connect_failed".equals(code)||"media_connect_failed".equals(code)||"connect_timeout".equals(code))return "暂时无法建立视频连接，请检查网络后重新连接";if(code.startsWith("auth_"))return "摄像头未接受视频授权，请重新连接";if(code.startsWith("stream_read_failed"))return "视频连接已中断，请重新连接";if("unsupported_camera".equals(code))return "此型号或视频通道仍在适配";return "视频通道连接失败，请重试";}
    boolean isReady(){Process child=process;return ready&&!closed.get()&&child!=null&&child.isAlive();}
    private boolean playback(String op){synchronized(commandLock){if(!isReady()||commands==null)return false;try{JSONObject command=new JSONObject().put("op",op);commands.write((command.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));commands.flush();return true;}catch(Exception failure){close();return false;}}}
    boolean pausePlayback(){pausedAck=false;return playback("pause");}boolean resumePlayback(){pausedAck=false;return playback("resume");}boolean isPaused(){return isReady()&&pausedAck;}boolean requestMetrics(){return playback("stats");}long mediaPackets(){return mediaPackets;}long metricsVersion(){return metricsVersion.get();}long commandReplyCount(){return commandReplyCount;}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;Process child=process;if(child!=null){try{child.getOutputStream().close();}catch(IOException ignored){}new Thread(()->{try{if(!child.waitFor(250,java.util.concurrent.TimeUnit.MILLISECONDS))child.destroy();}catch(InterruptedException interrupted){child.destroy();Thread.currentThread().interrupt();}},"JiaJuCameraStop").start();}}
}
