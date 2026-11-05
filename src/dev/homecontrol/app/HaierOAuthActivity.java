package dev.homecontrol.app;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.security.SecureRandom;
import java.util.*;

/** Official hosted SMS/captcha UI, with state-bound callback intercepted locally.
 * No Javascript bridge, form scraping, SMS access, or official-app dependency.
 */
public final class HaierOAuthActivity extends Activity {
    private WebView web;private TextView status;private String state,clientId;private boolean exchanging,destroyed;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AppUi.systemBars(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);AppUi ui=new AppUi(this);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);state=Base64.getUrlEncoder().withoutPadding().encodeToString(random);String unique=UUID.randomUUID().toString().replace("-","");clientId="HA"+unique.substring(0,10).toUpperCase(Locale.ROOT)+".cn."+unique.substring(10,26);
        LinearLayout page=ui.column();page.setBackgroundColor(ui.background);page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        TextView back=ui.text("‹ 返回海尔接入",16,ui.accent,true);back.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(14));back.setOnClickListener(v->finish());page.addView(back);
        status=ui.text("海尔官方短信授权页 · 试验接入使用公开的 Home Assistant 授权配置，页面可能显示该名称。",13,ui.muted,false);status.setPadding(ui.dp(18),0,ui.dp(18),ui.dp(12));page.addView(status);
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return navigate(request.getUrl(),request.isForMainFrame());}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()&&!exchanging)status.setText("官方授权页加载失败，请返回后重试；未发送设备指令。");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();status.setText("官方授权页证书校验失败，连接已取消。");}
        });
        page.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        Uri authorize=Uri.parse("https://account.haier.com/oauth/authorize").buildUpon().appendQueryParameter("redirect_uri",HaierCloud.OAUTH_CALLBACK).appendQueryParameter("client_id",HaierCloud.OAUTH_CLIENT).appendQueryParameter("response_type","code").appendQueryParameter("state",state).appendQueryParameter("login_priority","phone").build();web.loadUrl(authorize.toString());
    }
    private boolean navigate(Uri uri,boolean mainFrame){
        if(!mainFrame)return false;
        java.net.URI parsed;try{parsed=new java.net.URI(uri.toString());}catch(Exception e){return true;}
        if(HaierAuthPolicy.callback(parsed)){
            if(exchanging)return true;String received;try{received=HaierAuthPolicy.code(parsed,state);}catch(Exception e){received=null;}final String code=received;
            if(code==null){status.setText("授权回执未通过校验，请返回后重新登录。");return true;}
            exchanging=true;web.stopLoading();web.setVisibility(View.GONE);status.setText("正在完成海尔授权，不会操作设备…");
            new Thread(()->{try{HaierCloud.oauth(this,code,clientId);runOnUiThread(()->{if(!destroyed){setResult(RESULT_OK);finish();}});}catch(Exception e){runOnUiThread(()->{if(!destroyed)status.setText("授权交换未完成，请返回后重试。"+(e instanceof java.io.IOException?e.getMessage():""));});}},"HaierOAuth").start();return true;
        }
        boolean official=HaierAuthPolicy.loginAllowed(parsed);
        if(!official){status.setText("已阻止离开海尔授权页的跳转。");return true;}return false;
    }
    @Override protected void onDestroy(){destroyed=true;if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
