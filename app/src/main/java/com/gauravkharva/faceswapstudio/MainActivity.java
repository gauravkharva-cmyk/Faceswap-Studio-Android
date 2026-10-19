package com.gauravkharva.faceswapstudio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consent-based, entirely local face swapping between two photographs.
 * Version 1.1 focuses on more realistic results and rejects poor input pairs.
 */
public class MainActivity extends Activity {
    private static final int PICK_SOURCE = 101;
    private static final int PICK_TARGET = 102;
    private static final int SAVE_PNG = 103;
    private static final int MAX_SIDE = 1600;
    private static final int BG = Color.rgb(10, 14, 28);
    private static final int CARD = Color.rgb(21, 28, 46);
    private static final int TEXT = Color.rgb(240, 244, 255);
    private static final int MUTED = Color.rgb(166, 177, 199);
    private static final int PRIMARY = Color.rgb(105, 99, 247);

    private Bitmap sourceBitmap;
    private Bitmap targetBitmap;
    private Bitmap resultBitmap;
    private ImageView sourcePreview;
    private ImageView targetPreview;
    private ImageView resultPreview;
    private TextView status;
    private Button pickSource;
    private Button pickTarget;
    private Button swapButton;
    private Button saveButton;
    private CheckBox consent;
    private ProgressBar spinner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
    }

    private void buildInterface() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19), dp(28), dp(19), dp(24));
        scroller.addView(root);
        setContentView(scroller);

        TextView icon = label("✦  FACE SWAP STUDIO", 13, PRIMARY, true);
        root.addView(icon);
        TextView heading = label("Create a more realistic face-swap photo", 26, TEXT, true);
        heading.setPadding(0, dp(10), 0, dp(7));
        root.addView(heading);
        TextView sub = label("Private on-device editing · Improved blend engine · PNG export", 13, MUTED, false);
        root.addView(sub);

        TextView how = label(
                "Choose the face to copy and the photo to edit. Best results come from front-facing portraits with similar angle, lighting, and age.",
                14, TEXT, false);
        how.setPadding(dp(14), dp(14), dp(14), dp(14));
        how.setBackground(round(CARD, 16));
        LinearLayout.LayoutParams howParams = new LinearLayout.LayoutParams(-1, -2);
        howParams.setMargins(0, dp(18), 0, dp(18));
        root.addView(how, howParams);

        LinearLayout twoPhotos = new LinearLayout(this);
        twoPhotos.setOrientation(LinearLayout.HORIZONTAL);
        twoPhotos.setGravity(Gravity.TOP);
        root.addView(twoPhotos);

        LinearLayout sourceTile = imageTile("1. FACE SOURCE");
        sourcePreview = (ImageView) sourceTile.getChildAt(1);
        pickSource = actionButton("Choose face", false);
        sourceTile.addView(pickSource, fullWidthWithTop(8));
        pickSource.setOnClickListener(v -> choosePhoto(PICK_SOURCE));
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(0, -2, 1);
        sourceParams.setMargins(0, 0, dp(6), 0);
        twoPhotos.addView(sourceTile, sourceParams);

        LinearLayout targetTile = imageTile("2. TARGET PHOTO");
        targetPreview = (ImageView) targetTile.getChildAt(1);
        pickTarget = actionButton("Choose target", false);
        targetTile.addView(pickTarget, fullWidthWithTop(8));
        pickTarget.setOnClickListener(v -> choosePhoto(PICK_TARGET));
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(0, -2, 1);
        targetParams.setMargins(dp(6), 0, 0, 0);
        twoPhotos.addView(targetTile, targetParams);

        consent = new CheckBox(this);
        consent.setText("I have permission from the people in both photos.");
        consent.setTextColor(TEXT);
        consent.setTextSize(13);
        consent.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        root.addView(consent, fullWidthWithTop(17));

        swapButton = actionButton("✦  Swap Face", true);
        swapButton.setOnClickListener(v -> runFaceSwap());
        root.addView(swapButton, fullWidthWithTop(12));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.setMargins(0, dp(10), 0, 0);
        root.addView(spinner, spinnerParams);

        status = label("Select two photos to begin.", 13, MUTED, false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status, fullWidthWithTop(12));

        TextView resultLabel = label("RESULT PREVIEW", 13, MUTED, true);
        root.addView(resultLabel, fullWidthWithTop(24));
        resultPreview = new ImageView(this);
        resultPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        resultPreview.setBackground(round(CARD, 14));
        resultPreview.setContentDescription("Face-swap result preview");
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, dp(285));
        resultParams.setMargins(0, dp(10), 0, 0);
        root.addView(resultPreview, resultParams);
        TextView placeholder = label("Your edited image will appear above.", 12, MUTED, false);
        placeholder.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(placeholder, fullWidthWithTop(9));

        saveButton = actionButton("Save PNG to phone", false);
        saveButton.setOnClickListener(v -> saveResult());
        root.addView(saveButton, fullWidthWithTop(16));

        TextView note = label(
                "Every exported image is marked “AI EDITED • FACE SWAP”. Photos never leave this phone.\n\nDeveloped By : Gaurav Kharva",
                12, MUTED, false);
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(note, fullWidthWithTop(19));
        updateButtons();
    }

    private LinearLayout imageTile(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(9), dp(12), dp(9), dp(12));
        panel.setBackground(round(CARD, 15));
        TextView heading = label(title, 11, MUTED, true);
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.addView(heading);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round(Color.rgb(31, 39, 63), 10));
        preview.setContentDescription(title + " preview");
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(178));
        previewParams.setMargins(0, dp(10), 0, 0);
        panel.addView(preview, previewParams);
        return panel;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setLineSpacing(dp(2), 1.0f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(primary ? PRIMARY : Color.rgb(55, 68, 102), 12));
        button.setMinHeight(dp(48));
        return button;
    }

    private GradientDrawable round(int color, int corners) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(corners));
        return d;
    }

    private LinearLayout.LayoutParams fullWidthWithTop(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(top), 0, 0);
        return params;
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private void choosePhoto(int requestCode) {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == SAVE_PNG) {
            if (resultBitmap == null) return;
            final Bitmap toSave = resultBitmap;
            setBusy(true, "Saving image…");
            worker.execute(() -> {
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    if (stream == null || !toSave.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("Unable to write the selected file.");
                    }
                    runOnUiThread(() -> {
                        setBusy(false, "Image saved successfully.");
                        Toast.makeText(this, "PNG saved", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    fail("Cannot save PNG: " + readable(e));
                }
            });
            return;
        }
        if (requestCode != PICK_SOURCE && requestCode != PICK_TARGET) return;
        setBusy(true, "Loading selected photograph…");
        worker.execute(() -> {
            try {
                Bitmap loaded = loadBitmap(uri);
                runOnUiThread(() -> {
                    if (requestCode == PICK_SOURCE) {
                        sourceBitmap = loaded;
                        sourcePreview.setImageBitmap(loaded);
                    } else {
                        targetBitmap = loaded;
                        targetPreview.setImageBitmap(loaded);
                    }
                    resultBitmap = null;
                    resultPreview.setImageDrawable(null);
                    setBusy(false, "Photo loaded. Select the other photo, then tap Swap Face.");
                });
            } catch (Exception e) {
                fail("Could not open photograph: " + readable(e));
            }
        });
    }

    private Bitmap loadBitmap(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("File unavailable");
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Not a valid image");
        int sample = 1;
        while (Math.max(options.outWidth / sample, options.outHeight / sample) > MAX_SIDE) sample *= 2;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("File unavailable");
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("Unable to decode image");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input != null) {
                ExifInterface exif = new ExifInterface(input);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {
            // Some image providers do not expose EXIF metadata.
        }
        Matrix correction = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                correction.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                correction.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                correction.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                correction.setRotate(90f);
                correction.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                correction.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                correction.setRotate(270f);
                correction.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                correction.setRotate(270f);
                break;
            default:
                break;
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return decoded;
        }
        Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(),
                decoded.getHeight(), correction, true);
        if (rotated != decoded) decoded.recycle();
        return rotated;
    }

    private void runFaceSwap() {
        if (busy) return;
        if (sourceBitmap == null || targetBitmap == null) {
            status.setText("Choose both a face source and target photo first.");
            return;
        }
        if (!consent.isChecked()) {
            status.setText("Please confirm you have permission to use both photos.");
            return;
        }
        setBusy(true, "Detecting source face…");
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        final FaceDetector detector = FaceDetection.getClient(options);
        Task<List<Face>> findSource = detector.process(InputImage.fromBitmap(sourceBitmap, 0));
        findSource.addOnSuccessListener(sourceFaces -> {
            Face sourceFace = biggestFace(sourceFaces);
            if (sourceFace == null) {
                detector.close();
                setBusy(false, "No face found in the source photo. Try a clearer portrait.");
                return;
            }
            status.setText("Detecting target face…");
            detector.process(InputImage.fromBitmap(targetBitmap, 0))
                    .addOnSuccessListener(targetFaces -> {
                        detector.close();
                        Face targetFace = biggestFace(targetFaces);
                        if (targetFace == null) {
                            setBusy(false, "No face found in the target photo. Try a clearer portrait.");
                            return;
                        }
                        String pairIssue = compatibilityIssue(sourceFace, targetFace);
                        if (pairIssue != null) {
                            setBusy(false, pairIssue);
                            return;
                        }
                        status.setText("Aligning, correcting color, and blending faces…");
                        Bitmap originalSource = sourceBitmap;
                        Bitmap originalTarget = targetBitmap;
                        worker.execute(() -> {
                            try {
                                Bitmap swapped = FaceSwapEngine.swap(originalSource, originalTarget,
                                        sourceFace, targetFace);
                                runOnUiThread(() -> {
                                    resultBitmap = swapped;
                                    resultPreview.setImageBitmap(swapped);
                                    setBusy(false, "Done! Preview the result and tap Save PNG.");
                                });
                            } catch (Exception e) {
                                fail("Face swap unsuccessful: " + readable(e));
                            }
                        });
                    })
                    .addOnFailureListener(error -> {
                        detector.close();
                        setBusy(false, "Target face detection failed: " + readable(error));
                    });
        }).addOnFailureListener(error -> {
            detector.close();
            setBusy(false, "Source face detection failed: " + readable(error));
        });
    }

    private String compatibilityIssue(Face sourceFace, Face targetFace) {
        Rect src = sourceFace.getBoundingBox();
        Rect dst = targetFace.getBoundingBox();
        if (Math.min(src.width(), src.height()) < 120) {
            return "Source face is too small for a realistic swap. Use a closer portrait.";
        }
        if (Math.min(dst.width(), dst.height()) < 120) {
            return "Target face is too small for a realistic swap. Use a closer portrait.";
        }
        float yawGap = Math.abs(sourceFace.getHeadEulerAngleY() - targetFace.getHeadEulerAngleY());
        if (yawGap > 18f) {
            return "Photos are facing different directions. Use portraits with a more similar side angle.";
        }
        float rollGap = Math.abs(sourceFace.getHeadEulerAngleZ() - targetFace.getHeadEulerAngleZ());
        if (rollGap > 16f) {
            return "Head tilt differs too much. Use photos with a more similar tilt.";
        }
        return null;
    }

    private Face biggestFace(List<Face> faces) {
        Face best = null;
        int max = -1;
        for (Face face : faces) {
            Rect b = face.getBoundingBox();
            int area = b.width() * b.height();
            if (area > max) {
                best = face;
                max = area;
            }
        }
        return best;
    }

    private void saveResult() {
        if (resultBitmap == null || busy) return;
        if (!consent.isChecked()) {
            status.setText("Confirm permission before saving this image.");
            return;
        }
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.setType("image/png");
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.putExtra(Intent.EXTRA_TITLE,
                String.format(Locale.US, "faceswap-edited-%d.png", System.currentTimeMillis()));
        startActivityForResult(save, SAVE_PNG);
    }

    private void setBusy(boolean inProgress, String message) {
        busy = inProgress;
        spinner.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        status.setText(message);
        updateButtons();
    }

    private void updateButtons() {
        pickSource.setEnabled(!busy);
        pickTarget.setEnabled(!busy);
        swapButton.setEnabled(!busy && sourceBitmap != null && targetBitmap != null);
        saveButton.setEnabled(!busy && resultBitmap != null);
        swapButton.setAlpha(swapButton.isEnabled() ? 1f : 0.45f);
        saveButton.setAlpha(saveButton.isEnabled() ? 1f : 0.45f);
    }

    private String readable(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void fail(String error) {
        runOnUiThread(() -> setBusy(false, error));
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
