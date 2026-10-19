#!/usr/bin/env python3
"""Brand the vendored MIT AndroidFaceFusion application and label exports."""
from pathlib import Path

root = Path("facefusion-android")
gradle = root / "app/build.gradle.kts"
s = gradle.read_text()
assert 'applicationId = "com.pv.androidfacefusion"' in s
s = s.replace('applicationId = "com.pv.androidfacefusion"', 'applicationId = "com.gauravkharva.faceswapstudio.ai"')
s = s.replace('versionCode = 1', 'versionCode = 3')
s = s.replace('versionName = "1.0"', 'versionName = "2.0"')
gradle.write_text(s)

strings = root / "app/src/main/res/values/strings.xml"
strings.write_text(strings.read_text().replace("AndroidFaceFusion", "FaceSwap Studio AI"))

java_dir = root / "app/src/main/java/com/pv/androidfacefusion"
main = java_dir / "MainActivity.java"
j = main.read_text()
hooks = [
    ("btnProcess.setOnClickListener(v -> processFaceFusion());",
     "btnProcess.setOnClickListener(v -> confirmConsent(this::processFaceFusion));"),
    ("btnLibraryProcess.setOnClickListener(v -> processLibraryFaceFusion());",
     "btnLibraryProcess.setOnClickListener(v -> confirmConsent(this::processLibraryFaceFusion));"),
]
for old, new in hooks:
    assert old in j, "Upstream UI hook changed: " + old
    j = j.replace(old, new)

anchor = "    private void saveResult() {"
assert anchor in j, "Missing saveResult method"
consent = """    private void confirmConsent(Runnable action) {
        new AlertDialog.Builder(this)
            .setTitle("Consent required")
            .setMessage("I confirm that I have permission to use the faces in these images. Exported images will be marked AI EDITED.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I have permission", (dialog, which) -> action.run())
            .show();
    }

"""
j = j.replace(anchor, consent + anchor, 1)
compress = "resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);"
assert j.count(compress) == 2, "Unexpected export implementation"
j = j.replace(compress, "AIWatermark.compressResult(resultBitmap, out);")
main.write_text(j)

(java_dir / "AIWatermark.java").write_text("""package com.pv.androidfacefusion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.OutputStream;

/** Permanent AI-disclosure label on all saved/shared face-swapped photos. */
final class AIWatermark {
    private AIWatermark() { }

    static void compressResult(Bitmap original, OutputStream out) {
        Bitmap marked = original.copy(Bitmap.Config.ARGB_8888, true);
        try {
            Canvas canvas = new Canvas(marked);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.WHITE);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float size = Math.max(11f, Math.min(26f, marked.getWidth() / 40f));
            text.setTextSize(size);
            String label = "AI EDITED - FACE SWAP";
            float padding = Math.max(9f, size * 0.6f);
            if (text.measureText(label) + 2f * padding > marked.getWidth()) {
                text.setTextSize(Math.max(7f,
                    (marked.getWidth() - 2f * padding) / (text.measureText(label) / size)));
            }
            float left = Math.max(0f, marked.getWidth() - text.measureText(label) - 2f * padding);
            float top = Math.max(0f, marked.getHeight() - text.getTextSize() - 2.5f * padding);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(Color.argb(210, 16, 20, 33));
            canvas.drawRoundRect(new RectF(left, top, marked.getWidth(), marked.getHeight()),
                padding * 0.6f, padding * 0.6f, bg);
            canvas.drawText(label, left + padding, marked.getHeight() - padding, text);
            marked.compress(Bitmap.CompressFormat.JPEG, 95, out);
        } finally {
            marked.recycle();
        }
    }
}
""")

(root / "FACE_SWAP_STUDIO_NOTES.md").write_text("""# FaceSwap Studio AI v2

This folder contains the full Android FaceFusion source, vendored from
https://github.com/Parasaran-Python/android-face-fusion
at commit aafb3c3638529a61ba94fe8de579097fbcfb6885.

Original source: MIT license; see LICENSE and copyright attribution.
Modifications: separate application ID; AI export watermark; consent dialog.
Developed By : Gaurav Kharva (FaceSwap Studio adaptation).
Underlying Android FaceFusion by Parasaran Vedanarayanan.

ONNX model files (~739 MB) download on first launch and are not
redistributed here. InsightFace model licensing is non-commercial
research only. No commercial use without appropriate model licenses.
""")
print("Patched full FaceFusion Android source successfully.")
