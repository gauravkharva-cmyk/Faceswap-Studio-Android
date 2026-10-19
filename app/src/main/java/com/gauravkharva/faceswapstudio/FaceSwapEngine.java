package com.gauravkharva.faceswapstudio;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;

/**
 * More realistic image-domain face swapping.
 *
 * Strategy:
 * 1) affine-align the source face to the target face using eyes/nose/mouth anchors;
 * 2) build a softened face mask from the target face contour;
 * 3) match source face color statistics to the target face region;
 * 4) alpha-blend the corrected source face into the target image.
 *
 * This is still a local image-editing approach rather than a full generative model,
 * but it is materially better than a raw pasted-face overlay.
 */
final class FaceSwapEngine {
    private static final float MAX_BLEND_ALPHA = 0.95f;

    private FaceSwapEngine() { }

    static Bitmap swap(Bitmap source, Bitmap target, Face srcFace, Face dstFace) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target images are required");
        }
        final int w = target.getWidth();
        final int h = target.getHeight();
        if (w < 1 || h < 1) {
            throw new IllegalArgumentException("Invalid target bitmap");
        }

        Matrix warp = createWarp(srcFace, dstFace);

        Bitmap alignedSource = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas alignedCanvas = new Canvas(alignedSource);
        Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        alignedCanvas.drawBitmap(source, warp, drawPaint);

        Path maskPath = createBlendPath(dstFace);
        Bitmap alphaMask = createAlphaMask(w, h, maskPath, featherRadius(dstFace.getBoundingBox()));

        matchColorsInPlace(alignedSource, target, alphaMask);
        Bitmap result = blend(alignedSource, target, alphaMask);
        markAsAIEdited(result);

        alphaMask.recycle();
        alignedSource.recycle();
        return result;
    }

    private static Matrix createWarp(Face srcFace, Face dstFace) {
        float[] src = controlPoints(srcFace);
        float[] dst = controlPoints(dstFace);
        Matrix warp = new Matrix();
        int pointCount = Math.min(src.length, dst.length) / 2;
        if (pointCount < 3 || !warp.setPolyToPoly(src, 0, dst, 0, pointCount)) {
            throw new IllegalArgumentException("Could not align faces");
        }
        return warp;
    }

    private static float[] controlPoints(Face face) {
        Rect b = face.getBoundingBox();
        PointF leftEye = point(face, FaceLandmark.LEFT_EYE,
                b.left + 0.32f * b.width(), b.top + 0.42f * b.height());
        PointF rightEye = point(face, FaceLandmark.RIGHT_EYE,
                b.left + 0.68f * b.width(), b.top + 0.42f * b.height());
        PointF nose = point(face, FaceLandmark.NOSE_BASE,
                b.left + 0.50f * b.width(), b.top + 0.61f * b.height());
        PointF mouth = mouthPoint(face,
                b.left + 0.50f * b.width(), b.top + 0.79f * b.height());
        return new float[]{
                leftEye.x, leftEye.y,
                rightEye.x, rightEye.y,
                nose.x, nose.y,
                mouth.x, mouth.y
        };
    }

    private static PointF point(Face face, int landmarkType, float fallbackX, float fallbackY) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        return landmark == null ? new PointF(fallbackX, fallbackY) : landmark.getPosition();
    }

    private static PointF mouthPoint(Face face, float fallbackX, float fallbackY) {
        FaceLandmark left = face.getLandmark(FaceLandmark.MOUTH_LEFT);
        FaceLandmark right = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
        if (left != null && right != null) {
            PointF lp = left.getPosition();
            PointF rp = right.getPosition();
            return new PointF((lp.x + rp.x) * 0.5f, (lp.y + rp.y) * 0.5f);
        }
        FaceLandmark bottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
        return bottom == null ? new PointF(fallbackX, fallbackY) : bottom.getPosition();
    }

    private static Path createBlendPath(Face face) {
        Path outline = new Path();
        FaceContour contour = face.getContour(FaceContour.FACE);
        List<PointF> points = contour == null ? null : contour.getPoints();
        if (points != null && points.size() >= 10) {
            PointF first = points.get(0);
            outline.moveTo(first.x, first.y);
            for (int i = 1; i < points.size(); i++) {
                PointF p = points.get(i);
                outline.lineTo(p.x, p.y);
            }
            outline.close();
        } else {
            Rect b = face.getBoundingBox();
            outline.addOval(new RectF(b.left, b.top, b.right, b.bottom), Path.Direction.CW);
        }

        // Slightly shrink the mask to avoid including hair/background at the border.
        Rect b = face.getBoundingBox();
        Matrix inset = new Matrix();
        inset.setScale(0.90f, 0.92f, b.exactCenterX(), b.exactCenterY() + b.height() * 0.03f);
        outline.transform(inset);
        return outline;
    }

    private static Bitmap createAlphaMask(int width, int height, Path path, float feather) {
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mask);

        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        core.setColor(Color.WHITE);
        core.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, core);

        Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        blur.setColor(Color.WHITE);
        blur.setStyle(Paint.Style.FILL);
        blur.setMaskFilter(new BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(path, blur);
        return mask;
    }

    private static float featherRadius(Rect box) {
        return Math.max(8f, Math.min(34f, Math.min(box.width(), box.height()) * 0.085f));
    }

    private static void matchColorsInPlace(Bitmap sourceFace, Bitmap target, Bitmap mask) {
        final int width = sourceFace.getWidth();
        final int height = sourceFace.getHeight();
        final int count = width * height;

        int[] src = new int[count];
        int[] dst = new int[count];
        int[] maskPixels = new int[count];
        sourceFace.getPixels(src, 0, width, 0, 0, width, height);
        target.getPixels(dst, 0, width, 0, 0, width, height);
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height);

        ColorStats srcStats = collectStats(src, maskPixels, true);
        ColorStats dstStats = collectStats(dst, maskPixels, false);
        if (srcStats.weight < 1f || dstStats.weight < 1f) {
            return;
        }

        float gainR = clamp(dstStats.stdR / Math.max(10f, srcStats.stdR), 0.78f, 1.28f);
        float gainG = clamp(dstStats.stdG / Math.max(10f, srcStats.stdG), 0.78f, 1.28f);
        float gainB = clamp(dstStats.stdB / Math.max(10f, srcStats.stdB), 0.78f, 1.28f);

        for (int i = 0; i < count; i++) {
            int srcPx = src[i];
            int alpha = Color.alpha(srcPx);
            float maskWeight = weight(maskPixels[i], alpha);
            if (maskWeight <= 0.01f) {
                continue;
            }

            int r = Color.red(srcPx);
            int g = Color.green(srcPx);
            int b = Color.blue(srcPx);

            int nr = clampToByte((r - srcStats.meanR) * gainR + dstStats.meanR);
            int ng = clampToByte((g - srcStats.meanG) * gainG + dstStats.meanG);
            int nb = clampToByte((b - srcStats.meanB) * gainB + dstStats.meanB);

            // Blend correction with original color slightly to avoid plastic-looking over-correction.
            float correctionMix = 0.82f;
            nr = clampToByte(r * (1f - correctionMix) + nr * correctionMix);
            ng = clampToByte(g * (1f - correctionMix) + ng * correctionMix);
            nb = clampToByte(b * (1f - correctionMix) + nb * correctionMix);

            src[i] = Color.argb(alpha, nr, ng, nb);
        }

        sourceFace.setPixels(src, 0, width, 0, 0, width, height);
    }

    private static ColorStats collectStats(int[] pixels, int[] maskPixels, boolean requireAlpha) {
        ColorStats stats = new ColorStats();
        final int count = pixels.length;
        for (int i = 0; i < count; i++) {
            int px = pixels[i];
            int alpha = Color.alpha(px);
            float w = weight(maskPixels[i], requireAlpha ? alpha : 255);
            if (w <= 0.01f) continue;
            stats.weight += w;
            stats.sumR += Color.red(px) * w;
            stats.sumG += Color.green(px) * w;
            stats.sumB += Color.blue(px) * w;
            stats.sumSqR += Color.red(px) * Color.red(px) * w;
            stats.sumSqG += Color.green(px) * Color.green(px) * w;
            stats.sumSqB += Color.blue(px) * Color.blue(px) * w;
        }
        if (stats.weight > 0f) {
            stats.meanR = stats.sumR / stats.weight;
            stats.meanG = stats.sumG / stats.weight;
            stats.meanB = stats.sumB / stats.weight;
            stats.stdR = (float) Math.sqrt(Math.max(1f, stats.sumSqR / stats.weight - stats.meanR * stats.meanR));
            stats.stdG = (float) Math.sqrt(Math.max(1f, stats.sumSqG / stats.weight - stats.meanG * stats.meanG));
            stats.stdB = (float) Math.sqrt(Math.max(1f, stats.sumSqB / stats.weight - stats.meanB * stats.meanB));
        }
        return stats;
    }

    private static float weight(int maskPixel, int alpha) {
        return (Color.alpha(maskPixel) / 255f) * (alpha / 255f);
    }

    private static Bitmap blend(Bitmap sourceFace, Bitmap target, Bitmap mask) {
        final int width = target.getWidth();
        final int height = target.getHeight();
        final int count = width * height;

        int[] src = new int[count];
        int[] dst = new int[count];
        int[] maskPixels = new int[count];
        sourceFace.getPixels(src, 0, width, 0, 0, width, height);
        target.getPixels(dst, 0, width, 0, 0, width, height);
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height);

        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int srcPx = src[i];
            int dstPx = dst[i];
            float a = Math.min(MAX_BLEND_ALPHA, weight(maskPixels[i], Color.alpha(srcPx)));
            if (a <= 0.01f) {
                out[i] = dstPx;
                continue;
            }

            int r = clampToByte(Color.red(srcPx) * a + Color.red(dstPx) * (1f - a));
            int g = clampToByte(Color.green(srcPx) * a + Color.green(dstPx) * (1f - a));
            int b = clampToByte(Color.blue(srcPx) * a + Color.blue(dstPx) * (1f - a));
            out[i] = Color.argb(255, r, g, b);
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return result;
    }

    private static int clampToByte(float value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void markAsAIEdited(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float textSize = Math.max(10f, Math.min(24f, w / 48f));
        float padding = Math.max(7f, textSize * 0.55f);
        String label = "AI EDITED  •  FACE SWAP";

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(textSize);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float width = text.measureText(label);
        if (width + 2 * padding > w) {
            textSize = Math.max(7f, (w - 2 * padding) / (width / textSize));
            text.setTextSize(textSize);
            width = text.measureText(label);
        }

        float left = Math.max(0f, w - width - 2 * padding);
        float top = Math.max(0f, h - textSize - 2.5f * padding);
        float bottom = Math.min(h, top + textSize + 2.5f * padding);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.argb(195, 10, 14, 28));
        canvas.drawRoundRect(new RectF(left, top, w, bottom), padding * 0.7f,
                padding * 0.7f, background);
        canvas.drawText(label, left + padding, bottom - padding * 0.85f, text);
    }

    private static final class ColorStats {
        float weight;
        float sumR;
        float sumG;
        float sumB;
        float sumSqR;
        float sumSqG;
        float sumSqB;
        float meanR;
        float meanG;
        float meanB;
        float stdR;
        float stdG;
        float stdB;
    }
}
