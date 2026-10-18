package com.gauravkharva.faceswapstudio;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/** Image-domain aligned face overlay using ML Kit face geometry. No cloud data or API keys. */
final class FaceSwapEngine {
    private FaceSwapEngine() { }

    static Bitmap swap(Bitmap source, Bitmap target, Face srcFace, Face dstFace) {
        final int w = target.getWidth();
        final int h = target.getHeight();
        if (w < 1 || h < 1) throw new IllegalArgumentException("Invalid target bitmap");

        // Align source eye centers and nose to the target eye centers and nose.
        float[] sourcePoints = anchorPoints(srcFace);
        float[] destPoints = anchorPoints(dstFace);
        Matrix warp = new Matrix();
        if (!warp.setPolyToPoly(sourcePoints, 0, destPoints, 0, 3)) {
            throw new IllegalArgumentException("Could not align both faces");
        }

        Bitmap movedSource = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas movedCanvas = new Canvas(movedSource);
        Paint resample = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        movedCanvas.drawBitmap(source, warp, resample);

        // Use ML Kit's target face oval to mask the face, with a softly feathered edge.
        Path oval = faceOutline(dstFace);
        Rect box = dstFace.getBoundingBox();
        Matrix inset = new Matrix();
        inset.setScale(0.96f, 0.96f, box.exactCenterX(), box.exactCenterY());
        oval.transform(inset);
        float feather = Math.max(5f, Math.min(32f, Math.min(box.width(), box.height()) * 0.045f));

        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setColor(Color.WHITE);
        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setMaskFilter(new BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL));
        new Canvas(mask).drawPath(oval, maskPaint);

        // Destination-in keeps only the source pixels inside the feathered target oval.
        Canvas transparentFace = new Canvas(movedSource);
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        transparentFace.drawBitmap(mask, 0, 0, cut);
        cut.setXfermode(null);

        Bitmap result = target.copy(Bitmap.Config.ARGB_8888, true);
        Canvas resultCanvas = new Canvas(result);
        resultCanvas.drawBitmap(movedSource, 0, 0,
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        markAsAIEdited(resultCanvas, w, h);
        mask.recycle();
        movedSource.recycle();
        return result;
    }

    private static float[] anchorPoints(Face face) {
        Rect b = face.getBoundingBox();
        PointF left = point(face, FaceLandmark.LEFT_EYE,
                b.left + 0.30f * b.width(), b.top + 0.43f * b.height());
        PointF right = point(face, FaceLandmark.RIGHT_EYE,
                b.left + 0.70f * b.width(), b.top + 0.43f * b.height());
        PointF nose = point(face, FaceLandmark.NOSE_BASE,
                b.left + 0.50f * b.width(), b.top + 0.61f * b.height());
        return new float[]{left.x, left.y, right.x, right.y, nose.x, nose.y};
    }

    private static PointF point(Face face, int name, float fallbackX, float fallbackY) {
        FaceLandmark lm = face.getLandmark(name);
        return lm == null ? new PointF(fallbackX, fallbackY) : lm.getPosition();
    }

    private static Path faceOutline(Face face) {
        Path outline = new Path();
        FaceContour contour = face.getContour(FaceContour.FACE);
        List<PointF> pts = contour == null ? null : contour.getPoints();
        if (pts != null && pts.size() >= 8) {
            PointF first = pts.get(0);
            outline.moveTo(first.x, first.y);
            for (int i = 1; i < pts.size(); i++) {
                PointF p = pts.get(i);
                outline.lineTo(p.x, p.y);
            }
            outline.close();
        } else {
            Rect bounds = face.getBoundingBox();
            outline.addOval(new RectF(bounds.left, bounds.top,
                    bounds.right, bounds.bottom), Path.Direction.CW);
        }
        return outline;
    }

    private static void markAsAIEdited(Canvas canvas, int w, int h) {
        float textSize = Math.max(10f, Math.min(25f, w / 46f));
        float padding = Math.max(7f, textSize * 0.55f);
        String label = "AI EDITED  •  FACE SWAP";
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(textSize);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float width = text.measureText(label);
        // Prevent overflow for narrow/low-resolution images.
        if (width + 2 * padding > w) {
            textSize = Math.max(7f, (w - 2 * padding) / (width / textSize));
            text.setTextSize(textSize);
            width = text.measureText(label);
        }
        float left = Math.max(0, w - width - 2 * padding);
        float top = Math.max(0, h - textSize - 2.5f * padding);
        float bottom = Math.min(h, top + textSize + 2.5f * padding);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.argb(200, 10, 14, 28));
        canvas.drawRoundRect(new RectF(left, top, w, bottom), padding * 0.7f,
                padding * 0.7f, background);
        canvas.drawText(label, left + padding, bottom - padding * 0.85f, text);
    }
}
