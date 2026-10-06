/*
 * Copyright (c) Modular Open Source Identity Platform
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
*/

package io.mosip.registration.livenessmanager.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.view.Surface;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mosip.registration.livenessmanager.api.FaceFrameSource;
import io.mosip.registration.livenessmanager.api.PreviewSink;

/**
 * Plays a recorded video (mp4/3gp/webm) or a folder of still images (jpg/png, name order) as a live
 * frame stream, so the full flow runs without a camera or certified device. Loops until stopped.
 * <p>
 * {@code mirrored} declares whether the recording is a selfie-mirrored image (most phone front-camera
 * recordings are not); it controls the left/right meaning for head-turn challenges.
 */
public final class MockVideoFrameSource implements FaceFrameSource {
    private final File media;
    private final boolean mirrored;
    private final int fps;
    private final boolean loop;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final byte[][] buffer = new byte[1][];
    private Thread thread;

    public MockVideoFrameSource(File media, boolean mirrored, int fps, boolean loop) {
        this.media = media;
        this.mirrored = mirrored;
        this.fps = Math.max(1, Math.min(30, fps));
        this.loop = loop;
    }

    static boolean isImageFolder(File f) {
        return f.isDirectory();
    }

    @Override
    public SourceType getType() {
        return SourceType.MOCK_VIDEO;
    }

    @Override
    public void start(PreviewSink sink, Listener listener) {
        if (!running.compareAndSet(false, true)) return;
        thread = new Thread(() -> play(sink, listener), "liveness-mock-source");
        thread.start();
    }

    private void play(PreviewSink sink, Listener listener) {
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Surface surface = null;
        long frameId = 0;
        long periodNanos = 1_000_000_000L / fps;
        MediaMetadataRetriever retriever = null;
        File[] stills = null;
        int count;
        try {
            if (isImageFolder(media)) {
                stills = media.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpg|jpeg|png)$"));
                if (stills == null || stills.length == 0) {
                    listener.onError(Failure.UNAVAILABLE, "no frames in mock folder");
                    return;
                }
                Arrays.sort(stills);
                count = stills.length;
            } else {
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(media.getAbsolutePath());
                String n = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                count = n == null ? 0 : Integer.parseInt(n);
                if (count <= 0) {
                    listener.onError(Failure.STREAM_ERROR, "mock video has no frames");
                    return;
                }
            }
            int i = 0;
            long next = System.nanoTime();
            while (running.get()) {
                if (i >= count) {
                    if (!loop) break;
                    i = 0;
                }
                Bitmap raw = stills != null ? BitmapFactory.decodeFile(stills[i].getAbsolutePath())
                        : retriever.getFrameAtIndex(i);
                i++;
                if (raw == null) {
                    listener.onFrame(null);
                    continue;
                }
                Bitmap upright = AndroidFrames.upright(raw, 0);
                if (surface == null && sink != null) {
                    surface = (Surface) sink.acquireSurface(upright.getWidth(), upright.getHeight());
                    sink.onPreviewTransform(upright.getWidth(), upright.getHeight(), 0, !mirrored);
                }
                AndroidFrames.draw(surface, upright, false, paint);
                listener.onFrame(AndroidFrames.toFrame(++frameId, System.nanoTime(), upright, mirrored, buffer));
                if (upright != raw) upright.recycle();
                raw.recycle();

                next += periodNanos;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } else {
                    next = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            if (running.get()) listener.onError(Failure.STREAM_ERROR, "mock decode failed: " + e.getClass().getSimpleName());
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }
}
