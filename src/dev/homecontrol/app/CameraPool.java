package dev.homecontrol.app;
import android.content.Context;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicReference;
/** One short-lived, in-memory connection. An idle connection has no video consumer. */
final class CameraPool {
 private static final Handler main=new Handler(Looper.getMainLooper());private static Lease current;private static boolean foreground;
 static final class Lease {
  final String id,network;final int quality;final Forwarder forwarder=new Forwarder();final NativeCameraSession session;long deadline;boolean reused;
  Lease(Context context,String id,int quality,String network){this.id=id;this.quality=quality;this.network=network;session=new NativeCameraSession(context,id,quality,forwarder);}
 }
 private static final class Forwarder implements NativeCameraSession.Listener {
  final AtomicReference<NativeCameraSession.Listener> target=new AtomicReference<>();volatile boolean failed;
  public void status(String value){NativeCameraSession.Listener l=target.get();if(l!=null)l.status(value);}
  public void frame(int codec,long timestamp,byte[] data)throws Exception{NativeCameraSession.Listener l=target.get();if(l!=null)l.frame(codec,timestamp,data);}
  public void audio(int codec,int rate,int channels,byte[] data)throws Exception{NativeCameraSession.Listener l=target.get();if(l!=null)l.audio(codec,rate,channels,data);}
  public void control(String name,String code){NativeCameraSession.Listener l=target.get();if(l!=null)l.control(name,code);}
  public void error(String value){failed=true;NativeCameraSession.Listener l=target.get();if(l!=null)l.error(value);}
  public void diagnostic(String code){NativeCameraSession.Listener l=target.get();if(l!=null)l.diagnostic(code);}
  public void metrics(JSONObject values){NativeCameraSession.Listener l=target.get();if(l!=null)l.metrics(values);}
 }
 static synchronized void foreground(boolean value){foreground=value;if(!value)closeAll();}
 static synchronized Lease acquire(Context context,String id,int quality,NativeCameraSession.Listener listener){
  String network=CameraNetwork.signature();Lease saved=current;long now=SystemClock.elapsedRealtime();
  if(CameraReusePolicy.REUSE_ENABLED&&saved!=null&&CameraReusePolicy.allowed(id,quality,network,saved.id,saved.quality,saved.network,!saved.forwarder.failed&&saved.session.isReady(),foreground,saved.deadline,now)&&saved.forwarder.target.get()==null){saved.forwarder.target.set(listener);saved.reused=true;if(saved.session.resumePlayback()){listener.status("正在恢复实时画面…");return saved;}saved.forwarder.target.set(null);}
  closeAll();Lease next=new Lease(context.getApplicationContext(),id,quality,network);next.forwarder.target.set(listener);current=next;next.session.start();return next;
 }
 static synchronized void release(Lease lease){if(lease==null)return;lease.forwarder.target.set(null);if(lease!=current){lease.session.close();return;}if(!CameraReusePolicy.REUSE_ENABLED||!foreground||lease.forwarder.failed||!lease.session.pausePlayback()){closeAll();return;}lease.deadline=SystemClock.elapsedRealtime()+CameraReusePolicy.IDLE_MS;main.postDelayed(()->{synchronized(CameraPool.class){if(current==lease&&lease.forwarder.target.get()==null&&SystemClock.elapsedRealtime()>=lease.deadline)closeAll();}},CameraReusePolicy.IDLE_MS);}
 static synchronized void closeAll(){Lease old=current;current=null;if(old!=null){old.forwarder.target.set(null);old.session.close();}}
 static synchronized boolean idle(){return current!=null&&current.forwarder.target.get()==null&&current.session.isReady();}
}
