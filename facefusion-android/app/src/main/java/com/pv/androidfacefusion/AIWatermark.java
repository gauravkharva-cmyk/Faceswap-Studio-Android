package com.pv.androidfacefusion;

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
