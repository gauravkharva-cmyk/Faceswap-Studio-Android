package com.example.stufferwebaccess;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://stuffer.ai/";
    private static final int PICK = 812;
    private WebView web;
    private ValueCallback<Uri[]> files;
    private ProgressBar progress;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.rgb(31,42,60));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        for (String name : new String[]{"Back","Refresh","Browser"}) {
            Button b = new Button(this);
            b.setText(name);
            b.setTextSize(12);
            b.setAllCaps(false);
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1f));
            if (name.equals("Back")) b.setOnClickListener(v -> {
                if (web.canGoBack()) web.goBack(); else web.loadUrl(HOME);
            });
            if (name.equals("Refresh")) b.setOnClickListener(v -> web.reload());
            if (name.equals("Browser")) b.setOnClickListener(v ->
                external(Uri.parse(web.getUrl()==null?HOME:web.getUrl())));
        }
        body.addView(bar, new LinearLayout.LayoutParams(-1,-2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(-1,dp(3)));
        web = new WebView(this);
        body.addView(web,new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(body);

        WebSettings config = web.getSettings();
        config.setJavaScriptEnabled(true);
        config.setDomStorageEnabled(true);
        config.setDatabaseEnabled(true);
        config.setAllowContentAccess(true);
        config.setAllowFileAccess(false);
        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        config.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri u=req.getUrl();
                String scheme=u.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    String host=u.getHost();
                    if (host != null && (host.equalsIgnoreCase("stuffer.ai") ||
                        host.toLowerCase(java.util.Locale.ROOT).endsWith(".stuffer.ai"))) return false;
                }
                external(u);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) {
                progress.setProgress(value);
                progress.setVisibility(value<100?View.VISIBLE:View.GONE);
            }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams parameters) {
                if (files!=null) files.onReceiveValue(null);
                files=callback;
                try {
                    Intent intent=parameters.createIntent();
                    startActivityForResult(intent,PICK);
                    return true;
                } catch (Exception ex) {
                    files=null;
                    Toast.makeText(MainActivity.this,"File picker unavailable",Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        web.setDownloadListener((url,ua,disposition,mime,size)->{
            if (url==null || !url.startsWith("https://")) {
                Toast.makeText(this,"Use Browser button to download",Toast.LENGTH_LONG).show();
                return;
            }
            try {
                String file=URLUtil.guessFileName(url,disposition,mime);
                DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
                req.setTitle(file);
                req.setMimeType(mime);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if(ua!=null) req.addRequestHeader("User-Agent",ua);
                String cookie=CookieManager.getInstance().getCookie(url);
                if(cookie!=null) req.addRequestHeader("Cookie",cookie);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file);
                DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
                if(dm!=null) dm.enqueue(req);
                Toast.makeText(this,"Saving to Downloads",Toast.LENGTH_SHORT).show();
            } catch(Exception ex) {
                Toast.makeText(this,"Download failed; try Browser",Toast.LENGTH_LONG).show();
            }
        });
        if(saved==null) web.loadUrl(HOME); else web.restoreState(saved);
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private void external(Uri u){
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}
        catch(Exception ex){Toast.makeText(this,"Cannot open link",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==PICK && files!=null){
            files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            files=null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(web!=null) web.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(files!=null){files.onReceiveValue(null);files=null;}
        if(web!=null){((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}
        super.onDestroy();
    }
}
