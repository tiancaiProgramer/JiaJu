package dev.homecontrol.app;

import android.app.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.URI;

/** User signs in and completes verification on Xiaomi's official HTTPS page. */
public final class MijiaLoginActivity extends Activity {
    private final MijiaLogin login=new MijiaLogin();private WebView web;private TextView status;private volatile boolean destroyed;private String region;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);AppUi.systemBars(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);region=getIntent().getStringExtra("region");if(!MijiaWire.REGIONS.contains(region)){finish();return;}AppUi ui=new AppUi(this);
        LinearLayout page=ui.column();page.setBackgroundColor(ui.background);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});LinearLayout toolbar=ui.row();TextView back=ui.text("‹ 返回米家账号",16,ui.accent,true);back.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(14));back.setOnClickListener(v->finish());toolbar.addView(back,new LinearLayout.LayoutParams(0,-2,1));TextView retry=ui.text("重新打开登录页",13,ui.accent,true);retry.setPadding(ui.dp(12),ui.dp(14),ui.dp(18),ui.dp(14));retry.setOnClickListener(v->recreate());toolbar.addView(retry);page.addView(toolbar);
        status=ui.text("正在打开小米官方登录页…",13,ui.muted,false);status.setPadding(ui.dp(18),0,ui.dp(18),ui.dp(12));page.addView(status);
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setSaveFormData(false);web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);web.setSaveEnabled(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return false;boolean allowed=false;try{allowed=MijiaWire.accountUrl(new URI(request.getUrl().toString()));}catch(Exception ignored){}if(!allowed)status.setText("请在官方网页完成登录，可选择密码或验证码方式。");return !allowed;}@Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();status.setText("登录页安全连接失败，请返回重试。");}@Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("登录页未能加载，请检查网络并返回重试。");}});
        page.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        new Thread(()->{try{String pageUrl=login.begin();runOnUiThread(()->{if(!destroyed){status.setText("请在小米官方网页登录并确认米家授权，完成后会自动读取设备。");web.loadUrl(pageUrl);}});JSONObject session=login.await(region);if(destroyed)return;MijiaCloud.login(this,session);runOnUiThread(()->{if(!destroyed){setResult(RESULT_OK);finish();}});}catch(Exception e){runOnUiThread(()->{if(!destroyed)status.setText(CloudDevices.error(e,"米家登录未完成，请返回重试"));});}},"MijiaWebLogin").start();
    }
    @Override protected void onDestroy(){destroyed=true;login.cancel();if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
