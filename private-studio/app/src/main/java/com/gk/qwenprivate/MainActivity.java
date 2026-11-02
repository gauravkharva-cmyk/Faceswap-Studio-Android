package com.gk.qwenprivate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

public final class MainActivity extends Activity {
    private static final int PICK = 10;
    private static final int SAVE = 11;
    private static final int MAX_IMAGES = 10;
    private static final String KEY_ALIAS = "GKQwenPrivateApiToken";
    private static final int MAX_OUTPUT = 24 * 1024 * 1024;

    private final ExecutorService jobs = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> selected = new ArrayList<>();
    private EditText serverField, apiKeyField, promptField;
    private TextView imageCount, status;
    private ImageView preview;
    private Button generateButton, saveButton;
    private volatile boolean working = false;
    private byte[] resultData;

    private TextView text(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(8, 8, 8, 8);
        return label;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.rgb(17, 26, 44));
        getWindow().setNavigationBarColor(Color.rgb(17, 26, 44));

        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(14, 12, 14, 8);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView head = text("GK Qwen Image 2.1 • PRIVATE", 21, Color.rgb(18, 47, 84));
        head.setGravity(Gravity.CENTER);
        layout.addView(head);

        TextView caution = text("Self-hosted only. No public Qwen demo is used. Photos are sent ONLY to the HTTPS server you configure.", 13, Color.rgb(74, 85, 104));
        layout.addView(caution);
        layout.addView(text("Your HTTPS backend address", 14, Color.DKGRAY));
        serverField = new EditText(this);
        serverField.setSingleLine(true);
        serverField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverField.setHint("https://private.example.com");
        layout.addView(serverField);

        layout.addView(text("Private API key (stored encrypted on device)", 14, Color.DKGRAY));
        apiKeyField = new EditText(this);
        apiKeyField.setSingleLine(true);
        apiKeyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyField.setHint("Paste your server API key");
        layout.addView(apiKeyField);

        LinearLayout serverRow = new LinearLayout(this);
        serverRow.addView(button("Save settings", this::saveSettings), new LinearLayout.LayoutParams(0, -2, 1));
        serverRow.addView(button("Test server", this::testServer), new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(serverRow);

        layout.addView(text("Edit prompt", 14, Color.DKGRAY));
        promptField = new EditText(this);
        promptField.setTextSize(16f);
        promptField.setMinLines(3);
        promptField.setMaxLines(6);
        promptField.setGravity(Gravity.TOP);
        promptField.setHint("Describe what to change in your fictional character image...");
        layout.addView(promptField);

        layout.addView(button("Select reference images (up to 10)", this::pickPhotos));
        imageCount = text("No reference images selected • text-to-image also supported", 13, Color.rgb(55, 65, 81));
        layout.addView(imageCount);

        generateButton = button("Generate on MY private server", this::confirmGeneration);
        layout.addView(generateButton);
        status = text("Not connected • no photos uploaded", 13, Color.rgb(20, 90, 70));
        layout.addView(status);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setMinimumHeight(120);
        layout.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        saveButton = button("Save image", this::requestSave);
        saveButton.setEnabled(false);
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Clear session", this::clearSession), new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(actions);

        TextView privacy = text("Privacy: Result stays in app memory until saved or cleared. The server returns bytes without writing image files; temporary upload handles are closed. Your hosting company, operating system, backups and network metadata may still retain information.", 12, Color.rgb(85, 92, 102));
        layout.addView(privacy);
        TextView credit = text("Developed By : Gaurav Kharva", 12, Color.DKGRAY);
        credit.setGravity(Gravity.RIGHT);
        layout.addView(credit);

        scrolling.addView(layout);
        setContentView(scrolling);
        SharedPreferences prefs = getSharedPreferences("private_config", MODE_PRIVATE);
        serverField.setText(prefs.getString("url", ""));
        try { apiKeyField.setText(readEncryptedToken()); }
        catch (Exception e) { status.setText("Re-enter API key. Android KeyStore could not unlock it."); }
    }

    private String baseUrl() throws Exception {
        String value = serverField.getText().toString().trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
              uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
              uri.getRawFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new IllegalArgumentException("Enter an HTTPS origin with no path (https://your-domain)");
        }
        return value;
    }

    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private void saveSettings() {
        try {
            String url = baseUrl();
            String token = apiKeyField.getText().toString();
            if (token.length() < 24) throw new IllegalArgumentException("Enter the private API key (24+ characters)");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] encoded = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            SharedPreferences.Editor edit = getSharedPreferences("private_config", MODE_PRIVATE).edit();
            edit.putString("url", url);
            edit.putString("token", Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
                    Base64.encodeToString(encoded, Base64.NO_WRAP));
            edit.apply();
            status.setText("Settings saved locally. Test server before uploading.");
        } catch (Exception e) { alert(e.getMessage()); }
    }

    private String readEncryptedToken() throws Exception {
        String stored = getSharedPreferences("private_config", MODE_PRIVATE).getString("token", "");
        if (stored.isEmpty()) return "";
        String[] parts = stored.split("\\.");
        if (parts.length != 2) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private void pickPhotos() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(picker, PICK);
    }

    private void confirmGeneration() {
        try {
            final String host = URI.create(baseUrl()).getHost();
            if (apiKeyField.getText().length() < 24) throw new IllegalArgumentException("Enter your server API key");
            if (promptField.getText().toString().trim().isEmpty()) throw new IllegalArgumentException("Enter a prompt");
            new AlertDialog.Builder(this)
                .setTitle("Send photos to your server?")
                .setMessage("The selected photos and prompt will be uploaded to " + host +
                        ". Make sure you control and trust this server and its hosting provider.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Upload & generate", (dialog, which) -> generate())
                .show();
        } catch (Exception e) { alert(e.getMessage()); }
    }

    private interface Task { void run() throws Exception; }

    private void startTask(Task task) {
        if (working) { alert("Already working on a request"); return; }
        working = true;
        generateButton.setEnabled(false);
        jobs.execute(() -> {
            try { task.run(); }
            catch (Exception e) {
                runOnUiThread(() -> status.setText("Request failed: " + safeMessage(e)));
            } finally {
                working = false;
                runOnUiThread(() -> generateButton.setEnabled(true));
            }
        });
    }

    private String safeMessage(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "Request timed out. Server may still be processing.";
        if (error instanceof javax.net.ssl.SSLException) return "HTTPS certificate error. Configure valid TLS.";
        if (error instanceof java.net.ConnectException) return "Cannot reach your server.";
        if (error instanceof IllegalArgumentException) return error.getMessage();
        return "Connection or processing error; check server status.";
    }

    private void testServer() {
        final String url, key;
        try { url = baseUrl() + "/health"; key = apiKeyField.getText().toString(); }
        catch (Exception e) { alert(e.getMessage()); return; }
        status.setText("Testing private server...");
        startTask(() -> {
            HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            try {
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("X-API-Key", key);
                int response = connection.getResponseCode();
                runOnUiThread(() -> status.setText(response == 200
                        ? "Connected to private backend."
                        : "Server health check failed (HTTP " + response + ")."));
            } finally { connection.disconnect(); }
        });
    }

    private void generate() {
        final String url, key, prompt;
        final ArrayList<Uri> photos = new ArrayList<>(selected);
        try {
            url = baseUrl() + "/v1/edit";
            key = apiKeyField.getText().toString();
            prompt = promptField.getText().toString().trim();
        } catch (Exception e) { alert(e.getMessage()); return; }
        status.setText("Uploading to YOUR configured server; AI generation may be slow...");
        startTask(() -> {
            String boundary = "GK" + Long.toHexString(System.nanoTime());
            HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            try {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(900000);
                conn.setChunkedStreamingMode(65536);
                conn.setRequestProperty("X-API-Key", key);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Cache-Control", "no-store");
                try (OutputStream output = conn.getOutputStream()) {
                    partHeader(output, boundary, "prompt", null, "text/plain; charset=utf-8");
                    output.write(prompt.getBytes(StandardCharsets.UTF_8));
                    output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    byte[] buffer = new byte[64 * 1024];
                    for (int i = 0; i < photos.size(); i++) {
                        Uri uri = photos.get(i);
                        partHeader(output, boundary, "images", "upload_" + i + ".png", "application/octet-stream");
                        try (InputStream input = getContentResolver().openInputStream(uri)) {
                            if (input == null) throw new IllegalArgumentException("Cannot read selected photo");
                            int length;
                            long count = 0;
                            while ((length = input.read(buffer)) != -1) {
                                count += length;
                                if (count > 8L * 1024 * 1024) throw new IllegalArgumentException("Each image must be at most 8 MB");
                                output.write(buffer, 0, length);
                            }
                        }
                        output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
                    Arrays.fill(buffer, (byte)0);
                }
                int code = conn.getResponseCode();
                if (code != 200) {
                    final int errorCode = code;
                    runOnUiThread(() -> status.setText("Private server returned HTTP " + errorCode +
                            (errorCode == 429 ? " (GPU busy)" : "") + ". No output was saved."));
                    return;
                }
                byte[] bytes;
                try (InputStream stream = conn.getInputStream()) {
                    bytes = readLimited(stream, MAX_OUTPUT);
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IllegalArgumentException("Server returned an invalid PNG");
                runOnUiThread(() -> {
                    clearOutput();
                    resultData = bytes;
                    preview.setImageBitmap(bitmap);
                    saveButton.setEnabled(true);
                    status.setText("Image generated. Only held in app memory until you save it.");
                });
            } finally { conn.disconnect(); }
        });
    }

    private static byte[] readLimited(InputStream input, int max) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] block = new byte[64 * 1024];
        int n;
        while ((n = input.read(block)) != -1) {
            if (bytes.size() + n > max) throw new IllegalArgumentException("Server output exceeds 24 MB");
            bytes.write(block, 0, n);
        }
        Arrays.fill(block, (byte)0);
        return bytes.toByteArray();
    }

    private static void partHeader(OutputStream out, String boundary, String name, String filename, String mime) throws Exception {
        String text = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) text += "; filename=\"" + filename + "\"";
        text += "\r\nContent-Type: " + mime + "\r\n\r\n";
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void requestSave() {
        if (resultData == null) { alert("No generated result to save"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "GK-Qwen-" + System.currentTimeMillis() + ".png");
        startActivityForResult(intent, SAVE);
    }

    private void saveResult(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null || resultData == null) throw new IllegalStateException("Output unavailable");
            output.write(resultData);
            status.setText("Saved to your chosen location.");
        } catch (Exception e) { alert("Could not save image"); }
    }

    private void clearOutput() {
        if (resultData != null) Arrays.fill(resultData, (byte)0);
        resultData = null;
        preview.setImageDrawable(null);
        saveButton.setEnabled(false);
    }

    private void clearSession() {
        if (working) { alert("Generation in progress; clear after it finishes."); return; }
        selected.clear();
        imageCount.setText("No reference images selected");
        promptField.setText("");
        clearOutput();
        status.setText("Local session cleared. No remote deletion claim.");
    }

    private void alert(String message) {
        new AlertDialog.Builder(this).setMessage(message == null ? "Operation failed" : message)
                .setPositiveButton("OK", null).show();
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (code != RESULT_OK || data == null) return;
        if (request == SAVE) { if (data.getData() != null) saveResult(data.getData()); return; }
        if (request != PICK) return;
        selected.clear();
        ClipData multiple = data.getClipData();
        if (multiple != null) {
            for (int i = 0; i < Math.min(MAX_IMAGES, multiple.getItemCount()); i++)
                selected.add(multiple.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }
        imageCount.setText(selected.size() + " reference image(s) selected • nothing uploaded yet");
    }

    @Override protected void onDestroy() {
        clearOutput();
        jobs.shutdownNow();
        super.onDestroy();
    }
}
