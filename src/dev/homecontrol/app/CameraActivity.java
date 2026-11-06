package dev.homecontrol.app;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A single video surface is kept attached when entering/leaving immersive mode. */
public final class CameraActivity extends AppPage implements SurfaceHolder.Callback {
    private String id;
    private JSONObject device=new JSONObject();
    private LinearLayout layout,heading,information,toolbar;
    private FrameLayout video,root;
    private SurfaceView surface;
    private TextView status,qualityLabel,connection;
    private View playButton,fullButton,exitFull;
    private boolean foreground,paused,fullscreen;
    private volatile int generation;private int quality=1;
    private volatile int renderedFrames;
    private NativeCameraSession session;private CameraPool.Lease lease;private int recoveryAttempt;private volatile long stableSince;
    private CameraDecoder decoder;
    private final Handler main=new Handler(Looper.getMainLooper());
 private final Runnable recover=()->{if(foreground&&!paused){CameraPool.closeAll();connect();}};
    private final Runnable hideControls=()->{if(fullscreen&&renderedFrames>0){toolbar.setVisibility(View.GONE);exitFull.setVisibility(View.GONE);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);id=getIntent().getStringExtra("id");
        if(id==null||!id.startsWith("mijia:")){finish();return;}
        JSONObject cached=MijiaCloud.cached(this,id);if(cached!=null)device=cached;
        root=new FrameLayout(this);root.setBackgroundColor(ui.background);layout=ui.column();root.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(fullscreen?WindowInsets.Type.displayCutout():WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        heading=ui.row();heading.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(8));heading.addView(iconButton("返回",AppUi.BACK,this::back));
        LinearLayout labels=ui.column();TextView title=ui.text(device.optString("title","监控"),20,ui.text,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);labels.addView(ui.text(DeviceLocation.meta(device),11,ui.muted,false));
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);labelParams.leftMargin=ui.dp(12);heading.addView(labels,labelParams);heading.addView(iconButton("监控设置",AppUi.SETTINGS,this::settings));layout.addView(heading);
        video=new FrameLayout(this);video.setBackgroundColor(Color.BLACK);surface=new SurfaceView(this);surface.getHolder().addCallback(this);video.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        status=ui.text("准备连接实时画面…",14,Color.WHITE,false);status.setGravity(Gravity.CENTER);status.setPadding(ui.dp(28),ui.dp(16),ui.dp(28),ui.dp(52));video.addView(status,new FrameLayout.LayoutParams(-1,-1));
        toolbar=ui.row();toolbar.setBackgroundColor(0xb3101513);toolbar.setPadding(ui.dp(8),0,ui.dp(8),0);
        playButton=tool("暂停观看",AppUi.PAUSE,()->{paused=!paused;if(paused){stop();status.setText("已暂停观看");status.setVisibility(View.VISIBLE);}else connect();patchTools();showControls();});toolbar.addView(playButton,new LinearLayout.LayoutParams(0,ui.dp(54),1));
        toolbar.addView(tool("重新连接",AppUi.REFRESH,()->{paused=false;recoveryAttempt=0;CameraPool.closeAll();connect();patchTools();showControls();}),new LinearLayout.LayoutParams(0,ui.dp(54),1));
        qualityLabel=ui.text("流畅 ⌄",13,Color.WHITE,true);qualityLabel.setGravity(Gravity.CENTER);qualityLabel.setContentDescription("监控清晰度");qualityLabel.setBackground(ui.ripple(0x00101513,12));qualityLabel.setOnClickListener(v->qualityMenu());toolbar.addView(qualityLabel,new LinearLayout.LayoutParams(0,ui.dp(54),1));
        fullButton=tool("全屏观看",AppUi.FULLSCREEN,()->setFullscreen(!fullscreen));toolbar.addView(fullButton,new LinearLayout.LayoutParams(0,ui.dp(54),1));video.addView(toolbar,new FrameLayout.LayoutParams(-1,ui.dp(54),Gravity.BOTTOM));
        exitFull=tool("退出全屏",AppUi.BACK,()->setFullscreen(false));exitFull.setVisibility(View.GONE);FrameLayout.LayoutParams exitParams=new FrameLayout.LayoutParams(ui.dp(76),ui.dp(54),Gravity.TOP|Gravity.LEFT);video.addView(exitFull,exitParams);
        video.setOnClickListener(v->{if(fullscreen&&toolbar.getVisibility()==View.VISIBLE){main.removeCallbacks(hideControls);toolbar.setVisibility(View.GONE);exitFull.setVisibility(View.GONE);}else showControls();});
        layout.addView(video,new LinearLayout.LayoutParams(-1,ui.dp(245)));information=ui.column();information.setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(16));connection=ui.text("正在连接…",12,ui.muted,false);information.addView(connection);layout.addView(information);
        setContentView(root);root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)resize();});resize();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    private View tool(String title,int icon,Runnable action){LinearLayout box=ui.column();box.setGravity(Gravity.CENTER);box.setFocusable(true);box.setClickable(true);box.setContentDescription(title);box.setBackground(ui.ripple(0x00101513,10));box.addView(ui.icon(icon,Color.WHITE,19));TextView label=ui.text(title,10,Color.WHITE,false);label.setGravity(Gravity.CENTER);box.addView(label);box.setOnClickListener(v->action.run());return box;}
    private void replaceTool(View view,String label,int icon){LinearLayout box=(LinearLayout)view;box.removeViewAt(0);View glyph=ui.icon(icon,Color.WHITE,19);box.addView(glyph,0);((TextView)box.getChildAt(1)).setText(label);box.setContentDescription(label);}
    private void patchTools(){replaceTool(playButton,paused?"继续观看":"暂停观看",paused?AppUi.PLAY:AppUi.PAUSE);replaceTool(fullButton,fullscreen?"退出全屏":"全屏观看",fullscreen?AppUi.COLLAPSE_SCREEN:AppUi.FULLSCREEN);}
    private void qualityMenu(){
        main.removeCallbacks(hideControls);LinearLayout rows=ui.column();rows.setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6));PopupWindow popup=new PopupWindow(rows,ui.dp(150),ui.dp(116),true);popup.setBackgroundDrawable(ui.shape(ui.surface,16));popup.setElevation(ui.dp(8));popup.setOutsideTouchable(true);
        for(int q:new int[]{1,2}){TextView option=ui.text(q==1?"流畅":"高清",14,ui.text,false);option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(ui.dp(18),0,ui.dp(12),0);option.setBackground(ui.ripple(q==quality?ui.pale:ui.surface,12));option.setOnClickListener(v->{popup.dismiss();if(q!=quality){quality=q;qualityLabel.setText((q==1?"流畅":"高清")+" ⌄");qualityLabel.setStateDescription(q==1?"流畅":"高清");if(!paused)connect();}showControls();});rows.addView(option,new LinearLayout.LayoutParams(-1,ui.dp(52)));}popup.setOnDismissListener(this::showControls);popup.showAsDropDown(qualityLabel,0,ui.dp(4),Gravity.END);
    }
    private void showControls(){if(toolbar==null)return;toolbar.setVisibility(View.VISIBLE);exitFull.setVisibility(fullscreen?View.VISIBLE:View.GONE);main.removeCallbacks(hideControls);if(fullscreen&&renderedFrames>0)main.postDelayed(hideControls,3500);}
    private void setFullscreen(boolean value){if(fullscreen==value)return;fullscreen=value;heading.setVisibility(value?View.GONE:View.VISIBLE);information.setVisibility(value?View.GONE:View.VISIBLE);root.setBackgroundColor(value?Color.BLACK:ui.background);patchTools();WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null){bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(value)bars.hide(WindowInsets.Type.systemBars());else bars.show(WindowInsets.Type.systemBars());}setRequestedOrientation(value?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);root.requestApplyInsets();resize();showControls();}
    private void resize(){if(video==null)return;int width=root.getWidth();if(width<=0)return;LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)video.getLayoutParams();int desired=fullscreen?0:Math.round(width*9/16f)+ui.dp(54);if(p.height!=desired||p.weight!=(fullscreen?1:0)){p.height=desired;p.weight=fullscreen?1:0;video.setLayoutParams(p);}video.post(()->{int w=video.getWidth(),h=Math.max(1,video.getHeight()-ui.dp(fullscreen?0:54));int sw=w,sh=Math.round(w*9/16f);if(sh>h){sh=h;sw=Math.round(h*16/9f);}FrameLayout.LayoutParams sp=(FrameLayout.LayoutParams)surface.getLayoutParams();int gravity=fullscreen?Gravity.CENTER:Gravity.TOP|Gravity.CENTER_HORIZONTAL;if(sp.width!=sw||sp.height!=sh||sp.gravity!=gravity){sp.width=sw;sp.height=sh;sp.gravity=gravity;surface.setLayoutParams(sp);}});}
    private void back(){if(fullscreen)setFullscreen(false);else finish();}
    @Override public void onBackPressed(){back();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);resize();}
    @Override protected void onResume(){super.onResume();foreground=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(!paused)connect();}
    @Override protected void onPause(){foreground=false;main.removeCallbacks(recover);stop();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder holder){if(session==null&&!paused)connect();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){stop();}
    private void connect(){if(!foreground||paused||surface==null||!surface.getHolder().getSurface().isValid())return;main.removeCallbacks(recover);stop();final int token=++generation;renderedFrames=0;status.setVisibility(View.VISIBLE);showControls();
        if(!CameraPolicy.supported(device.optString("model"))){status.setText("此摄像头型号的视频连接仍在适配");return;}status.setText("正在连接实时画面…");connection.setText("正在连接…");decoder=new CameraDecoder(surface.getHolder().getSurface(),()->{if(token!=generation)return;int count=++renderedFrames;if(count==1){stableSince=SystemClock.elapsedRealtime();}if(count==1)main.post(()->{if(token==generation){status.setVisibility(View.GONE);connection.setText("实时画面 · "+(quality==1?"流畅":"高清"));showControls();}});});final CameraDecoder sink=decoder;
        lease=CameraPool.acquire(this,id,quality,new NativeCameraSession.Listener(){public void status(String text){main.post(()->{if(token==generation&&renderedFrames==0){status.setText(text);connection.setText(text);}});}public void frame(int codec,long timestamp,byte[] data)throws Exception{if(token==generation)sink.accept(codec,timestamp,data);}public void error(String message){main.post(()->{if(token!=generation)return;status.setText(message);status.setVisibility(View.VISIBLE);connection.setText(message);showControls();long now=SystemClock.elapsedRealtime();if(stableSince>0&&now-stableSince>=30000)recoveryAttempt=0;stableSince=0;long wait=CameraReusePolicy.AUTO_RECOVERY_ENABLED?CameraReusePolicy.recoveryDelay(recoveryAttempt,foreground,paused,message):-1;if(wait>=0){recoveryAttempt++;status.setText("连接中断，正在重连（"+recoveryAttempt+"/3）…");main.postDelayed(recover,wait);}});}});session=lease.session;
    }
    private void stop(){generation++;if(lease!=null){CameraPool.release(lease);lease=null;}session=null;if(decoder!=null){decoder.close();decoder=null;}}
    private void settings(){showControls();new CameraSettingsDialog(this,ui,id,device).show();}
    int renderedFrameCount(){return renderedFrames;}
}
