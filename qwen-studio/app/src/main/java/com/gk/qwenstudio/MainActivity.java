package com.gk.qwenstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.ValueCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String OFFICIAL = "https://huggingface.co/spaces/Qwen/Qwen-Image-2.1";
    private static final String BACKUP = "https://huggingface.co/spaces/peguero/qwen-image-2-1-demo";
    private static final int PICK_IMAGES = 5109;
    private WebView page;
    private TextView status;
    private ProgressBar loadBar;
    private ValueCallback<Uri[]> pendingFiles;

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Button action(String text, Runnable command) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setPadding(8, 0, 8, 0);
        b.setOnClickListener(v -> command.run());
        return b;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(20, 30, 49));
        getWindow().setNavigationBarColor(Color.rgb(20, 30, 49));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("GK  •  Qwen Image 2.1 Studio");
        heading.setTextColor(Color.WHITE);
        heading.setBackgroundColor(Color.rgb(20, 30, 49));
        heading.setTextSize(19f);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(20, 12, 10, 12);
        layout.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scrolling = new HorizontalScrollView(this);
        scrolling.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(3, 0, 3, 0);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(action("Official Qwen 2.1", () -> page.loadUrl(OFFICIAL)));
        bar.addView(action("Backup demo", () -> page.loadUrl(BACKUP)));
        bar.addView(action("Examples", this::examples));
        bar.addView(action("Browser", this::openInBrowser));
        bar.addView(action("Help", this::help));
        scrolling.addView(bar);
        layout.addView(scrolling, new LinearLayout.LayoutParams(-1, -2));

        loadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadBar.setIndeterminate(true);
        loadBar.setVisibility(View.GONE);
        layout.addView(loadBar, new LinearLayout.LayoutParams(-1, 4));

        status = new TextView(this);
        status.setTextSize(11f);
        status.setTextColor(Color.rgb(55, 65, 81));
        status.setPadding(14, 8, 14, 8);
        status.setText("Online demo • Shared GPU • Free access depends on host");
        layout.addView(status, new LinearLayout.LayoutParams(-1, -2));

        page = new WebView(this);
        page.setBackgroundColor(Color.WHITE);
        page.getSettings().setJavaScriptEnabled(true);
        page.getSettings().setDomStorageEnabled(true);
        page.getSettings().setAllowFileAccess(false);
        page.getSettings().setAllowContentAccess(true);
        page.getSettings().setBuiltInZoomControls(true);
        page.getSettings().setDisplayZoomControls(false);
        page.getSettings().setUseWideViewPort(true);
        page.getSettings().setLoadWithOverviewMode(true);
        page.getSettings().setSupportMultipleWindows(false);
        page.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(page, true);

        page.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                loadBar.setVisibility(View.VISIBLE);
                status.setText("Loading Qwen 2.1 image editing...");
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                loadBar.setVisibility(View.GONE);
                status.setText("Use the website's upload, prompt, Generate and download controls");
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equals("huggingface.co") || host.endsWith(".hf.space"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    toast("Unable to open link: " + e.getMessage());
                }
                return true;
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    loadBar.setVisibility(View.GONE);
                    status.setText("Site unavailable. Try Backup demo or Browser.");
                }
            }
        });

        page.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    Intent chooser = params.createIntent();
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    chooser.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(chooser, PICK_IMAGES);
                } catch (Exception e) {
                    pendingFiles.onReceiveValue(null);
                    pendingFiles = null;
                    toast("Cannot open images: " + e.getMessage());
                }
                return true;
            }
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress == 100) loadBar.setVisibility(View.GONE);
            }
        });

        page.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String agent, String disposition, String mime, long size) {
                saveDownload(url, agent, mime);
            }
        });

        page.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = page.getHitTestResult();
            if (hit != null && (hit.getType() == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                String url = hit.getExtra();
                if (url != null && !url.isEmpty()) {
                    new AlertDialog.Builder(this)
                        .setTitle("Save image")
                        .setMessage("Save this image to your device?")
                        .setPositiveButton("Save", (d, w) -> saveDownload(url, page.getSettings().getUserAgentString(), "image/png"))
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                }
            }
            return false;
        });

        layout.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = new TextView(this);
        footer.setText("Developed By : Gaurav Kharva");
        footer.setTextSize(11f);
        footer.setTextColor(Color.rgb(85, 95, 105));
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(8, 4, 12, 8);
        layout.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(layout);

        page.loadUrl(OFFICIAL);
    }

    private void examples() {
        String[] names = {"Keep same face; change background",
                          "Keep character identity; change outfit",
                          "Improve lighting and realism",
                          "Create a new image from text"};
        String[] prompts = {
            "Keep the same adult fictional character's face, facial structure and hairstyle. Change only the background to a sunlit garden. Photorealistic.",
            "Preserve the same fictional adult character's face and body proportions. Change the outfit to a stylish formal suit. Natural lighting.",
            "Improve realistic detail, clean lighting, natural skin texture and sharp focus. Preserve the subject's identity and composition.",
            "A cinematic photograph of a peaceful mountain lake at sunrise, detailed realistic lighting, natural colors."
        };
        new AlertDialog.Builder(this).setTitle("Copy an example prompt")
            .setItems(names, (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Qwen prompt", prompts[which]));
                toast("Prompt copied. Paste it into the Qwen demo.");
            }).setNegativeButton("Close", null).show();
    }

    private void help() {
        new AlertDialog.Builder(this)
            .setTitle("How to use GK Qwen 2.1 Studio")
            .setMessage("1. Wait for the online Qwen demo to load.\n\n" +
                        "2. Select Image Editing and upload your reference images using the website's upload control.\n\n" +
                        "3. Describe your changes, then tap Generate on the website.\n\n" +
                        "4. Download the result using the website's download button; long-press may also work for images.\n\n" +
                        "If the demo is busy, tap Backup demo or Browser.\n\n" +
                        "This is an unofficial client; the actual AI runs on the remote website. It does not bypass queues or content rules.")
            .setPositiveButton("OK", null).show();
    }

    private void openInBrowser() {
        String url = page.getUrl() == null ? OFFICIAL : page.getUrl();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) { toast("No browser found"); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || pendingFiles == null) return;
        Uri[] chosen = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                android.content.ClipData clip = data.getClipData();
                ArrayList<Uri> result = new ArrayList<>();
                for (int n = 0; n < clip.getItemCount(); n++)
                    result.add(clip.getItemAt(n).getUri());
                chosen = result.toArray(new Uri[0]);
            } else if (data.getData() != null) {
                chosen = new Uri[] {data.getData()};
            }
        }
        pendingFiles.onReceiveValue(chosen);
        pendingFiles = null;
    }

    private void saveDownload(String url, String agent, String mime) {
        if (url == null || url.isEmpty()) {
            toast("No file link found"); return;
        }
        if (url.startsWith("blob:")) {
            downloadBlob(url); return;
        }
        if (url.startsWith("data:image/")) {
            decodeDataImage(url); return;
        }
        if (!url.startsWith("https://")) {
            toast("Unsupported or insecure file URL"); return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mime == null || mime.isEmpty() ? "image/png" : mime);
            request.setTitle("GK Qwen Image");
            request.setDescription("Generated image");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "GK-Qwen-" + System.currentTimeMillis() + ".png");
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (agent != null) request.addRequestHeader("User-Agent", agent);
            ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            toast("Download started. Check Downloads.");
        } catch (Exception e) {
            toast("Download failed. Open in Browser and save there.");
        }
    }

    private void downloadBlob(String url) {
        // A web page's blob URL is only readable inside that page's origin.
        // Limit size to avoid high memory use in Android WebView's JS bridge.
        String script = "(async function(){try{const b=await(await fetch(" + JSONObject.quote(url) +
            ")).blob();if(b.size>4000000)return 'TOO_LARGE';" +
            "return await new Promise((ok,bad)=>{const f=new FileReader();" +
            "f.onload=()=>ok(f.result);f.onerror=()=>bad();f.readAsDataURL(b);});" +
            "}catch(e){return 'FAILED';}})()";
        page.evaluateJavascript(script, response -> {
            try {
                String data = new JSONArray("[" + response + "]").getString(0);
                if ("TOO_LARGE".equals(data)) {
                    toast("Large image: use Browser to download it."); return;
                }
                decodeDataImage(data);
            } catch (Exception e) {
                toast("Save through Browser instead (site download restriction).");
            }
        });
    }

    private void decodeDataImage(String dataUrl) {
        try {
            if (dataUrl == null || !dataUrl.startsWith("data:image/") || !dataUrl.contains(";base64,")) {
                toast("This site requires saving in Browser."); return;
            }
            int comma = dataUrl.indexOf(',');
            int semicolon = dataUrl.indexOf(';');
            String mime = dataUrl.substring(5, semicolon);
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length > 8_000_000) { toast("Use Browser for large images."); return; }
            String name = "GK-Qwen-" + System.currentTimeMillis() +
                    ("image/jpeg".equals(mime) ? ".jpg" : ".png");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GK-Qwen");
                Uri dest = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (dest == null) throw new Exception("Cannot create image");
                try (OutputStream output = getContentResolver().openOutputStream(dest)) {
                    if (output == null) throw new Exception("Cannot open image");
                    output.write(bytes);
                }
            } else {
                File folder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GK-Qwen");
                if (!folder.exists() && !folder.mkdirs()) throw new Exception("Cannot create folder");
                try (FileOutputStream output = new FileOutputStream(new File(folder, name))) {
                    output.write(bytes);
                }
            }
            toast("Saved in Pictures/GK-Qwen");
        } catch (Exception e) { toast("Unable to save image: " + e.getMessage()); }
    }

    @Override
    public void onBackPressed() {
        if (page != null && page.canGoBack()) page.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles = null; }
        if (page != null) { page.destroy(); page = null; }
        super.onDestroy();
    }
}
