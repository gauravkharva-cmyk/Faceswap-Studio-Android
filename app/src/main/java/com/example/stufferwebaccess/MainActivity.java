package com.example.stufferwebaccess;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.ClipData;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.PixelCopy;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONTokener;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebViewRenderProcess;
import android.webkit.WebViewRenderProcessClient;
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
    private FrameLayout pageFrame;
    private boolean rendererWarning = false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(31,42,60));
        LinearLayout firstRow = new LinearLayout(this);
        LinearLayout secondRow = new LinearLayout(this);
        toolbar.addView(firstRow, new LinearLayout.LayoutParams(-1, dp(44)));
        toolbar.addView(secondRow, new LinearLayout.LayoutParams(-1, dp(44)));
        addAction(firstRow, "Back", () -> {
            if (web != null && web.canGoBack()) web.goBack();
            else if (web != null) web.loadUrl(HOME);
        });
        addAction(firstRow, "Reference Photo", () -> showReferencePhotoHelp());
        addAction(firstRow, "Save Image", () -> saveLargestOrScreenshot());
        addAction(secondRow, "Stop Page", () -> {
            if (web != null) web.stopLoading();
            Toast.makeText(this, "Page loading stopped. Server jobs may continue.", Toast.LENGTH_LONG).show();
        });
        addAction(secondRow, "Recover", () -> confirmRecover());
        addAction(secondRow, "Browser", () ->
            external(Uri.parse(web == null || web.getUrl() == null ? HOME : web.getUrl())));
        body.addView(toolbar, new LinearLayout.LayoutParams(-1,-2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(-1,dp(3)));
        pageFrame = new FrameLayout(this);
        body.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(body);
        createWebView();
        if (saved == null) web.loadUrl(HOME); else web.restoreState(saved);
    }


    private void addAction(LinearLayout row, String label, Runnable callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
        b.setOnClickListener(v -> callback.run());
    }
    private void confirmRecover() {
        new AlertDialog.Builder(this).setTitle("Recover frozen page?")
            .setMessage("Recover reopens the Stuffer website and may lose unsaved prompts " +
                "and uploaded selections. It cannot cancel a server job or bypass overload.")
            .setPositiveButton("Recover", (d, w) -> recoverWebView())
            .setNeutralButton("Chrome", (d, w) -> external(Uri.parse(HOME)))
            .setNegativeButton("Cancel", null).show();
    }
    private void releaseWebView(WebView old) {
        if (old == null) return;
        try {
            ViewGroup container = (ViewGroup) old.getParent();
            if (container != null) container.removeView(old);
            old.destroy();
        } catch (Exception ignored) {}
    }
    private void recoverWebView() {
        if (files != null) { files.onReceiveValue(null); files = null; }
        WebView old = web;
        web = null;
        releaseWebView(old);
        rendererWarning = false;
        createWebView();
        web.loadUrl(HOME);
        Toast.makeText(this, "Stuffer reopened. Check Chrome if overload continues.", Toast.LENGTH_LONG).show();
    }
    private void createWebView() {
        web = new WebView(this);
        pageFrame.addView(web, new FrameLayout.LayoutParams(-1,-1));
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
            @Override public boolean onRenderProcessGone(WebView dead, RenderProcessGoneDetail detail) {
                if (files != null) { files.onReceiveValue(null); files = null; }
                releaseWebView(dead);
                if (web == dead) web = null;
                rendererWarning = false;
                createWebView();
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Webpage renderer stopped")
                    .setMessage("Stuffer's webpage stopped or ran out of resources. " +
                        "You can reload the homepage or open Chrome. " +
                        "This cannot resolve Stuffer server overload.")
                    .setPositiveButton("Reload homepage", (d, w) -> web.loadUrl(HOME))
                    .setNeutralButton("Chrome", (d, w) -> external(Uri.parse(HOME)))
                    .setNegativeButton("Close", null).show();
                return true;
            }
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
                if (files != null) files.onReceiveValue(null);
                files = callback;
                try {
                    String[] requested = parameters.getAcceptTypes();
                    ArrayList<String> types = new ArrayList<>();
                    if (requested != null) {
                        for (String item : requested) {
                            if (item == null) continue;
                            for (String value : item.split(",")) {
                                value = value.trim();
                                if (!value.isEmpty() && !value.equals("*/*")) types.add(value);
                            }
                        }
                    }
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    String mimeType = "image/*";
                    if (types.size() == 1) mimeType = types.get(0);
                    else if (types.size() > 1) {
                        mimeType = "*/*";
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                    }
                    intent.setType(mimeType);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        parameters.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(intent, PICK);
                    return true;
                } catch (Exception ex) {
                    files = null;
                    Toast.makeText(MainActivity.this,
                        "Android file picker unavailable; open this page in Chrome.",
                        Toast.LENGTH_LONG).show();
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
        web.setOnLongClickListener(view -> {
            WebView.HitTestResult result = web.getHitTestResult();
            if (result == null) return false;
            int kind = result.getType();
            if (kind != WebView.HitTestResult.IMAGE_TYPE &&
                kind != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false;
            String src = result.getExtra();
            new AlertDialog.Builder(this)
                .setTitle("Save generated image")
                .setItems(new String[]{"Save original if available", "Save visible screen"}, (dialog, which) -> {
                    if (which == 0 && src != null && src.startsWith("https://")) {
                        downloadOriginal(src);
                    } else {
                        saveScreenshot();
                    }
                }).show();
            return true;
        });
        web.setWebViewRenderProcessClient(getMainExecutor(),
            new WebViewRenderProcessClient() {
                @Override public void onRenderProcessUnresponsive(WebView affected, WebViewRenderProcess process) {
                    if (affected != web || rendererWarning || isFinishing()) return;
                    rendererWarning = true;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Webpage not responding")
                        .setMessage("The embedded browser is busy or frozen. " +
                            "Recover restarts only the webpage; it cannot repair overloaded AI servers.")
                        .setPositiveButton("Recover", (d, w) -> recoverWebView())
                        .setNeutralButton("Chrome", (d, w) -> external(Uri.parse(HOME)))
                        .setNegativeButton("Keep waiting", null).show();
                }
                @Override public void onRenderProcessResponsive(WebView affected, WebViewRenderProcess process) {
                    if (affected == web) rendererWarning = false;
                }
            });
    }

    // Reference pictures belong in the generator's model-specific inputs.
    // Deliberately do not click the website's PUBLIC Upload Files feature.
    private void showReferencePhotoHelp() {
        new AlertDialog.Builder(this)
            .setTitle("Upload photo for image editing")
            .setMessage("Important: The popup 'Upload AI Images & Videos' is PUBLIC, " +
                "not a private reference upload. Close it using X.\n\n" +
                "1. On the generator page, open model selection.\n" +
                "2. Choose Qwen Edit or an image-guided model.\n" +
                "3. Open Show more options / settings.\n" +
                "4. Tap Qwen Edit Image Input > Primary Image (Required), " +
                "or the Controlnet++ image-input control.\n" +
                "5. Choose your image using the Android Files or Photos picker.\n\n" +
                "This app handles website file-upload requests; it cannot " +
                "substitute a photo directly into another website field.")
            .setPositiveButton("Highlight reference input", (dialog, which) -> highlightReferenceInput())
            .setNeutralButton("Open in Chrome", (dialog, which) ->
                external(Uri.parse(web.getUrl()==null?HOME:web.getUrl())))
            .setNegativeButton("Close", null)
            .show();
    }

    private void highlightReferenceInput() {
        // Search only within sections labelled for model-reference uploads.
        // No synthetic upload or automatic public publication is performed.
        String script = "(function(){" +
            "var inputs=document.querySelectorAll('input[type=file]');" +
            "for(var i=0;i<inputs.length;i++){" +
            "var node=inputs[i],ctx='';" +
            "for(var p=node,k=0;p&&k<5;k++,p=p.parentElement){" +
            "var s=(p.innerText||'').trim();" +
            "if(s.length>0&&s.length<450)ctx+=' '+s;" +
            "}" +
            "if(/qwen edit image input|primary image|required|controlnet\\+\\+|flux kontext image input/i.test(ctx)" +
            "&&!/this is not a private upload|upload ai images & videos|publicly visible/i.test(ctx)){" +
            "var target=node;while(target&&target.getBoundingClientRect().height<8)target=target.parentElement;" +
            "if(!target)target=node.parentElement;" +
            "if(target){target.scrollIntoView({block:'center',behavior:'smooth'});" +
            "target.style.outline='3px solid #65c7ff';}" +
            "return 'found';" +
            "}" +
            "}" +
            "return 'missing';" +
            "})()";
        web.evaluateJavascript(script, value -> {
            if ("\"found\"".equals(value)) {
                Toast.makeText(this,
                    "Tap the highlighted reference image field to pick a photo.",
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                    "Select Qwen Edit and open its image-input settings first.",
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    // Save the largest displayed HTML image when it has a permanent URL.
    // For canvas, blob: images, and other renderers, capture the visible WebView.
    private void saveLargestOrScreenshot() {
        String script = "(function(){var a=document.querySelectorAll('img'),winner='',area=0;" +
            "for(var i=0;i<a.length;i++){var im=a[i],r=im.getBoundingClientRect();" +
            "var z=r.width*r.height;if(im.complete&&im.naturalWidth>=256&&r.width>=180&&" +
            "r.height>=180&&r.bottom>0&&r.top<innerHeight&&z>area){" +
            "area=z;winner=im.currentSrc||im.src;}}" +
            "return winner;})()";
        web.evaluateJavascript(script, value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (decoded instanceof String && ((String) decoded).startsWith("https://")) {
                    downloadOriginal((String) decoded);
                    return;
                }
            } catch (Exception ignored) {}
            Toast.makeText(this, "Saving visible screen to Gallery", Toast.LENGTH_SHORT).show();
            saveScreenshot();
        });
    }

    private void downloadOriginal(String url) {
        try {
            String file = URLUtil.guessFileName(url, null, null);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(file);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new Exception("Download service unavailable");
            manager.enqueue(request);
            Toast.makeText(this, "Original image download started", Toast.LENGTH_LONG).show();
        } catch (Exception err) {
            Toast.makeText(this, "Original unavailable: saving screen instead", Toast.LENGTH_LONG).show();
            saveScreenshot();
        }
    }

    private void saveScreenshot() {
        if (web == null || web.getWidth() == 0 || web.getHeight() == 0) {
            Toast.makeText(this, "Please open the image first", Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(web.getWidth(), web.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (Exception ex) {
            Toast.makeText(this, "Insufficient memory", Toast.LENGTH_LONG).show();
            return;
        }
        int[] position = new int[2];
        web.getLocationInWindow(position);
        Rect rect = new Rect(position[0], position[1],
            position[0] + web.getWidth(), position[1] + web.getHeight());
        try {
            PixelCopy.request(getWindow(), rect, bitmap, result -> {
                try {
                    if (result != PixelCopy.SUCCESS) web.draw(new Canvas(bitmap));
                    saveBitmapToGallery(bitmap);
                } catch (Exception ex) {
                    Toast.makeText(this, "Could not save screenshot", Toast.LENGTH_LONG).show();
                } finally { bitmap.recycle(); }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception e) {
            try {
                web.draw(new Canvas(bitmap));
                saveBitmapToGallery(bitmap);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not save screenshot", Toast.LENGTH_LONG).show();
            } finally { bitmap.recycle(); }
        }
    }

    private void saveBitmapToGallery(Bitmap bitmap) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            throw new Exception("PNG conversion failed");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "AI_Image_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Web Access");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri destination = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (destination == null) throw new Exception("Cannot create image");
        try {
            try (OutputStream stream = getContentResolver().openOutputStream(destination)) {
                if (stream == null) throw new Exception("Cannot open image");
                stream.write(output.toByteArray());
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(destination, done, null, null);
            Toast.makeText(this, "Saved to Gallery > Pictures > AI Web Access", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            getContentResolver().delete(destination, null, null);
            throw e;
        }
    }

    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private void external(Uri u){
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}
        catch(Exception ex){Toast.makeText(this,"Cannot open link",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if (code == PICK && files != null) {
            Uri[] selected = null;
            if (result == RESULT_OK && data != null) {
                LinkedHashSet<Uri> chosen = new LinkedHashSet<>();
                if (data.getData() != null) chosen.add(data.getData());
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri item = clip.getItemAt(i).getUri();
                        if (item != null) chosen.add(item);
                    }
                }
                if (!chosen.isEmpty()) selected = chosen.toArray(new Uri[0]);
            }
            files.onReceiveValue(selected);
            files = null;
            if (selected != null) Toast.makeText(this,
                "Photo selected. Check Stuffer AI image input.", Toast.LENGTH_LONG).show();
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
