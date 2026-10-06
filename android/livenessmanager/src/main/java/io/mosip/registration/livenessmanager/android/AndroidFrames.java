/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Surface;

import java.nio.ByteBuffer;

import io.mosip.registration.livenessmanager.model.FaceFrame;

/** Bitmap / surface helpers shared by the frame sources. No pixel data ever leaves this process. */
final class AndroidFrames {
    /** Analysis frames are capped to this long side to bound inference cost on low-end phones. */
    static final int MAX_ANALYSIS_SIDE = 640;

    private AndroidFrames() {
    }

    /** Rotates (and optionally scales down) to an upright ARGB_8888 bitmap. */
    static Bitmap upright(Bitmap src, int rotationDegrees) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        float scale = longSide > MAX_ANALYSIS_SIDE ? (float) MAX_ANALYSIS_SIDE / longSide : 1f;
        if (rotationDegrees % 360 == 0 && scale == 1f && src.getConfig() == Bitmap.Config.ARGB_8888) {
            return src;
        }
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postRotate(rotationDegrees);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copy = out.copy(Bitmap.Config.ARGB_8888, false);
            if (out != src) out.recycle();
            out = copy;
        }
        return out;
    }

    /** Copies the bitmap into {@code reuse} when large enough. Bitmap ARGB_8888 memory order is RGBA. */
    static FaceFrame toFrame(long id, long tsNanos, Bitmap upright, boolean mirrored, byte[][] reuse) {
        int size = upright.getWidth() * upright.getHeight() * 4;
        byte[] rgba = reuse[0];
        if (rgba == null || rgba.length != size) {
            rgba = new byte[size];
            reuse[0] = rgba;
        }
        upright.copyPixelsToBuffer(ByteBuffer.wrap(rgba));
        return new FaceFrame(id, tsNanos, upright.getWidth(), upright.getHeight(), rgba, mirrored, upright);
    }

    /** Draws a frame onto a preview surface, letterboxed; failures are ignored (preview only). */
    static void draw(Surface surface, Bitmap bitmap, boolean mirror, Paint paint) {
        if (surface == null || !surface.isValid()) return;
        Canvas c = null;
        try {
            c = surface.lockHardwareCanvas();
            c.drawColor(0xFF0F1215);
            float s = Math.min((float) c.getWidth() / bitmap.getWidth(), (float) c.getHeight() / bitmap.getHeight());
            int w = Math.round(bitmap.getWidth() * s), h = Math.round(bitmap.getHeight() * s);
            int x = (c.getWidth() - w) / 2, y = (c.getHeight() - h) / 2;
            if (mirror) {
                c.save();
                c.scale(-1f, 1f, c.getWidth() / 2f, c.getHeight() / 2f);
            }
            c.drawBitmap(bitmap, null, new Rect(x, y, x + w, y + h), paint);
            if (mirror) c.restore();
        } catch (RuntimeException ignored) {
            // surface released while drawing
        } finally {
            if (c != null) {
                try {
                    surface.unlockCanvasAndPost(c);
                } catch (RuntimeException ignored) {
                    // surface already gone
                }
            }
        }
    }
}
