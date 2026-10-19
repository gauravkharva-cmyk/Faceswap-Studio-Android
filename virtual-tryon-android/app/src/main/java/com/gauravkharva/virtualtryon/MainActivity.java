package com.gauravkharva.virtualtryon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI clothing replacement with an explicit privacy gate and a user-supplied API key.
 * No nudification tools, and the remote API is configured with conservative moderation.
 */
public class MainActivity extends Activity {
    private static final int PICK_PERSON = 101;
    private static final int PICK_GARMENT = 102;
    private static final int SAVE_IMAGE = 103;
    private static final String RUN_URL = "https://api.fashn.ai/v1/run";
    private static final String STATUS_PREFIX = "https://api.fashn.ai/v1/status/";
    private static final int MAX_DIMENSION = 1300;
    private static final int DARK = Color.rgb(14, 18, 34);
    private static final int CARD = Color.rgb(28, 35, 57);
    private static final int PRIMARY = Color.rgb(106, 99, 248);
    private static final int TEXT = Color.rgb(247, 249, 255);
    private static final int MUTED = Color.rgb(175, 188, 210);

    private Bitmap person;
    private Bitmap garment;
    private Bitmap generated;
    private ImageView personPreview;
    private ImageView garmentPreview;
    private ImageView resultPreview;
    private Button selectPerson;
    private Button selectGarment;
    private Button generateButton;
    private Button saveButton;
    private EditText apiKey;
    private Spinner clothingType;
    private TextView status;
    private ProgressBar progress;
    private CheckBox consent;
    private boolean busy;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(DARK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(25), dp(18), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("✦  VIRTUAL TRY-ON AI", 14, PRIMARY, true));
        root.addView(text("Try on different outfits", 27, TEXT, true), marginTop(11));
        root.addView(text("Select your photo and a photo of real clothing. Create an AI-styled outfit preview.", 14, MUTED, false), marginTop(8));

        TextView privacy = text("PRIVACY FIRST\nYour photos remain on your phone unless you explicitly choose to send them to FASHN AI. Cloud AI requires an API key and may use paid credits.", 13, TEXT, false);
        privacy.setPadding(dp(13), dp(14), dp(13), dp(14));
        privacy.setBackground(round(CARD, 14));
        root.addView(privacy, marginTop(18));

        LinearLayout pictures = new LinearLayout(this);
        pictures.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(pictures, marginTop(18));

        LinearLayout personBox = imageTile("1. PERSON PHOTO");
        personPreview = (ImageView) personBox.getChildAt(1);
        selectPerson = button("Select person", false);
        selectPerson.setOnClickListener(v -> choosePhoto(PICK_PERSON));
        personBox.addView(selectPerson, marginTop(8));
        LinearLayout.LayoutParams halfA = new LinearLayout.LayoutParams(0, -2, 1);
        halfA.setMargins(0, 0, dp(6), 0);
        pictures.addView(personBox, halfA);

        LinearLayout garmentBox = imageTile("2. CLOTHING PHOTO");
        garmentPreview = (ImageView) garmentBox.getChildAt(1);
        selectGarment = button("Select clothing", false);
        selectGarment.setOnClickListener(v -> choosePhoto(PICK_GARMENT));
        garmentBox.addView(selectGarment, marginTop(8));
        LinearLayout.LayoutParams halfB = new LinearLayout.LayoutParams(0, -2, 1);
        halfB.setMargins(dp(6), 0, 0, 0);
        pictures.addView(garmentBox, halfB);

        root.addView(text("Clothing type", 13, MUTED, true), marginTop(18));
        clothingType = new Spinner(this);
        String[] labels = {"Automatic", "Tops / Shirts", "Bottoms / Trousers", "Dresses / One-pieces"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        clothingType.setAdapter(adapter);
        clothingType.setBackground(round(Color.rgb(232, 234, 251), 12));
        root.addView(clothingType, marginTop(8));

        root.addView(text("FASHN API key", 13, MUTED, true), marginTop(17));
        apiKey = new EditText(this);
        apiKey.setSingleLine(true);
        apiKey.setTextSize(14);
        apiKey.setTextColor(Color.rgb(29, 34, 46));
        apiKey.setHint("Enter your API key (not saved)");
        apiKey.setHintTextColor(Color.GRAY);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setPadding(dp(13), dp(9), dp(13), dp(9));
        apiKey.setBackground(round(Color.WHITE, 12));
        root.addView(apiKey, marginTop(8));
        root.addView(text("Get an API key from app.fashn.ai. One generated image uses a provider credit.", 12, MUTED, false), marginTop(7));

        consent = new CheckBox(this);
        consent.setText("I have permission to use these photos. I understand that generating an outfit uploads both images securely to FASHN AI.");
        consent.setTextColor(TEXT);
        consent.setTextSize(13);
        root.addView(consent, marginTop(13));

        generateButton = button("✦  Generate AI outfit", true);
        generateButton.setOnClickListener(v -> confirmAndGenerate());
        root.addView(generateButton, marginTop(9));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(12), 0, 0);
        root.addView(progress, progressParams);
        status = text("Choose photos to begin. Nothing is sent online automatically.", 13, MUTED, false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status, marginTop(13));

        root.addView(text("AI-STYLED PREVIEW", 13, MUTED, true), marginTop(22));
        resultPreview = new ImageView(this);
        resultPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        resultPreview.setBackground(round(CARD, 15));
        resultPreview.setContentDescription("AI outfit result");
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, dp(320));
        resultParams.setMargins(0, dp(10), 0, 0);
        root.addView(resultPreview, resultParams);

        saveButton = button("Save edited image to phone", false);
        saveButton.setOnClickListener(v -> savePhoto());
        root.addView(saveButton, marginTop(12));
        TextView footer = text("AI-created outfit previews are labeled. No nudification. No analytics. No photo backup by this app.\n\nDeveloped By : Gaurav Kharva", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(footer, marginTop(19));
        refresh();
    }

    private LinearLayout imageTile(String heading) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(round(CARD, 14));
        col.setPadding(dp(9), dp(13), dp(9), dp(13));
        TextView t = text(heading, 11, MUTED, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(t);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round(Color.rgb(53, 64, 91), 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(170));
        lp.setMargins(0, dp(9), 0, 0);
        col.addView(preview, lp);
        return col;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }
    private Button button(String title, boolean main) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(main ? PRIMARY : Color.rgb(65, 79, 118), 12));
        b.setMinHeight(dp(48));
        return b;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private LinearLayout.LayoutParams marginTop(int space) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(space);
        return lp;
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void refresh() {
        selectPerson.setEnabled(!busy);
        selectGarment.setEnabled(!busy);
        generateButton.setEnabled(!busy && person != null && garment != null);
        saveButton.setEnabled(!busy && generated != null);
        generateButton.setAlpha(generateButton.isEnabled() ? 1f : 0.46f);
        saveButton.setAlpha(saveButton.isEnabled() ? 1f : 0.46f);
    }
    private void working(boolean now, String description) {
        busy = now;
        progress.setVisibility(now ? View.VISIBLE : View.GONE);
        status.setText(description);
        refresh();
    }
    private void showFailure(Exception e) {
        String reason = e.getMessage();
        runOnUiThread(() -> working(false, "Unable to generate: " + (reason == null ? "Unknown error" : reason)));
    }

    private void choosePhoto(int request) {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == SAVE_IMAGE) {
            if (generated == null) return;
            Bitmap snapshot = generated;
            working(true, "Saving image...");
            worker.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null || !snapshot.compress(Bitmap.CompressFormat.PNG, 100, out))
                        throw new IOException("Cannot write PNG");
                    runOnUiThread(() -> {
                        working(false, "Saved successfully.");
                        Toast.makeText(this, "Saved to phone", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) { showFailure(e); }
            });
            return;
        }
        if (requestCode != PICK_PERSON && requestCode != PICK_GARMENT) return;
        working(true, "Loading photo from your phone...");
        worker.execute(() -> {
            try {
                Bitmap b = loadResized(uri);
                runOnUiThread(() -> {
                    if (requestCode == PICK_PERSON) {
                        person = b;
                        personPreview.setImageBitmap(b);
                    } else {
                        garment = b;
                        garmentPreview.setImageBitmap(b);
                    }
                    generated = null;
                    resultPreview.setImageDrawable(null);
                    working(false, "Photo loaded locally. Select the second photo, then generate.");
                });
            } catch (Exception e) { showFailure(e); }
        });
    }

    private Bitmap loadResized(Uri uri) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Selected photo could not be opened");
            BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth < 20 || opts.outHeight < 20) throw new IOException("Selected image is too small");
        int sample = 1;
        while (Math.max(opts.outWidth / sample, opts.outHeight / sample) > MAX_DIMENSION) sample *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Selected photo could not be opened");
            b = BitmapFactory.decodeStream(in, null, opts);
        }
        if (b == null) throw new IOException("Unsupported photo format");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { }
        Matrix transform = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: transform.setRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: transform.setRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: transform.setRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: transform.setRotate(90); transform.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: transform.setRotate(270); transform.postScale(-1, 1); break;
            default: break;
        }
        if (!transform.isIdentity()) {
            Bitmap corrected = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), transform, true);
            if (corrected != b) b.recycle();
            b = corrected;
        }
        return b;
    }

    private void confirmAndGenerate() {
        if (busy || person == null || garment == null) return;
        final String key = apiKey.getText().toString().trim();
        if (key.isEmpty()) {
            status.setText("Enter your FASHN API key to use realistic AI try-on.");
            return;
        }
        if (!consent.isChecked()) {
            status.setText("Confirm you have permission and understand the cloud upload.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Send photos for AI processing?")
                .setMessage("Both photos will be uploaded over HTTPS to FASHN AI for outfit generation. This uses your API credits. The app does not upload anything until you select Continue.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue & generate", (dialog, which) -> generate(key))
                .show();
    }

    private void generate(String key) {
        working(true, "Preparing images for secure AI request...");
        final Bitmap p = person;
        final Bitmap g = garment;
        final String[] categories = {"auto", "tops", "bottoms", "one-pieces"};
        final String category = categories[Math.max(0, Math.min(3, clothingType.getSelectedItemPosition()))];
        worker.execute(() -> {
            try {
                String personData = toDataUri(p);
                String garmentData = toDataUri(g);
                JSONObject input = new JSONObject();
                input.put("model_image", personData);
                input.put("garment_image", garmentData);
                input.put("category", category);
                input.put("garment_photo_type", "auto");
                input.put("mode", "quality");
                input.put("num_samples", 1);
                input.put("output_format", "png");
                input.put("return_base64", true);
                input.put("moderation_level", "conservative");
                JSONObject payload = new JSONObject();
                payload.put("model_name", "tryon-v1.6");
                payload.put("inputs", input);

                runOnUiThread(() -> status.setText("Uploading photos to FASHN for virtual try-on..."));
                JSONObject started = apiRequest("POST", RUN_URL, key, payload.toString());
                String predictionId = started.optString("id", "");
                if (predictionId.isEmpty()) throw new IOException("AI service did not issue a prediction ID");
                if (!predictionId.matches("[a-zA-Z0-9_-]{5,150}")) throw new IOException("Invalid prediction ID");

                for (int attempt = 0; attempt < 65; attempt++) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    Thread.sleep(3000);
                    final int elapsed = (attempt + 1) * 3;
                    runOnUiThread(() -> status.setText("Creating AI outfit... " + elapsed + "s"));
                    JSONObject checked = apiRequest("GET", STATUS_PREFIX + predictionId, key, null);
                    String state = checked.optString("status", "");
                    if ("failed".equals(state)) {
                        JSONObject err = checked.optJSONObject("error");
                        String detail = err == null ? "AI generation failed" : err.optString("message", "AI generation failed");
                        throw new IOException(shortMessage(detail));
                    }
                    if ("completed".equals(state)) {
                        JSONArray out = checked.optJSONArray("output");
                        if (out == null || out.length() == 0) throw new IOException("AI returned no image");
                        String encoded = out.optString(0, "");
                        Bitmap raw;
                        if (encoded.startsWith("data:image/")) {
                            int comma = encoded.indexOf(',');
                            if (comma < 0) throw new IOException("AI output was incomplete");
                            byte[] bytes = Base64.decode(encoded.substring(comma + 1), Base64.DEFAULT);
                            raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        } else if (encoded.startsWith("https://")) {
                            raw = loadResultFromFashn(encoded);
                        } else throw new IOException("Unexpected AI output format");
                        if (raw == null) throw new IOException("The AI output could not be decoded");
                        Bitmap labeled = watermark(raw);
                        if (raw != labeled) raw.recycle();
                        runOnUiThread(() -> {
                            generated = labeled;
                            resultPreview.setImageBitmap(labeled);
                            working(false, "Outfit created. Save the image to your phone.");
                        });
                        return;
                    }
                }
                throw new IOException("AI service did not finish within 195 seconds. Try again later.");
            } catch (Exception e) { showFailure(e); }
        });
    }

    private String toDataUri(Bitmap src) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!src.compress(Bitmap.CompressFormat.JPEG, 85, out)) throw new IOException("Image conversion failed");
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private JSONObject apiRequest(String method, String endpoint, String key, String body) throws Exception {
        URL url = new URL(endpoint);
        if (!"https".equals(url.getProtocol()) || !"api.fashn.ai".equals(url.getHost()))
            throw new IOException("Untrusted AI service URL");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(100000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                InputStream err = connection.getErrorStream();
                String details = err == null ? "" : readString(err, 8192);
                String text = details.length() == 0 ? "HTTP " + code : shortMessage(details);
                throw new IOException("FASHN API error (" + code + "): " + text);
            }
            try (InputStream in = connection.getInputStream()) {
                return new JSONObject(readString(in, 14000000));
            }
        } finally { connection.disconnect(); }
    }

    private static String readString(InputStream in, int maxBytes) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                if (buf.size() + count > maxBytes) throw new IOException("Response too large");
                buf.write(chunk, 0, count);
            }
            return buf.toString("UTF-8");
        }
    }

    private Bitmap loadResultFromFashn(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !("cdn.fashn.ai".equals(url.getHost()) || "media.fashn.ai".equals(url.getHost())))
            throw new IOException("AI output was served from an unexpected domain");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(18000);
        conn.setReadTimeout(45000);
        conn.setInstanceFollowRedirects(false);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("Could not retrieve AI image");
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } finally { conn.disconnect(); }
    }

    private Bitmap watermark(Bitmap source) {
        Bitmap marked = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(marked);
        float padding = Math.max(9f, marked.getWidth() / 95f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(Color.WHITE);
        p.setTextSize(Math.max(11f, marked.getWidth() / 42f));
        String label = "AI STYLED  •  VIRTUAL TRY-ON";
        if (p.measureText(label) > marked.getWidth() - 2f * padding)
            p.setTextSize(Math.max(7f, p.getTextSize() * ((marked.getWidth() - 2f * padding) / p.measureText(label))));
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(213, 14, 18, 34));
        float boxLeft = Math.max(0f, marked.getWidth() - p.measureText(label) - padding * 2);
        float boxTop = Math.max(0f, marked.getHeight() - p.getTextSize() - padding * 2.8f);
        c.drawRoundRect(new RectF(boxLeft, boxTop, marked.getWidth(), marked.getHeight()), padding, padding, bg);
        c.drawText(label, boxLeft + padding, marked.getHeight() - padding, p);
        return marked;
    }

    private void savePhoto() {
        if (generated == null || busy) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("image/png");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, String.format(Locale.US, "virtual-tryon-ai-%d.png", System.currentTimeMillis()));
        startActivityForResult(i, SAVE_IMAGE);
    }

    private String shortMessage(String value) {
        if (value == null) return "Unknown error";
        return value.length() > 250 ? value.substring(0, 250) : value;
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
