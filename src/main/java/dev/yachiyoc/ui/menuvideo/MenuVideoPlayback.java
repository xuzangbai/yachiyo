package dev.yachiyoc.ui.menuvideo;

import dev.yachiyoc.platform.Debug;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/** Decodes ahead by at most three frames. The screen consumes them on a monotonic media clock. */
final class MenuVideoPlayback implements AutoCloseable {
    private static final int BUFFERS = 3;
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    record Frame(WindowsVideo.Format format, ByteBuffer pixels, long timeNanos, int clip) { }

    private final ArrayBlockingQueue<Frame> ready = new ArrayBlockingQueue<>(BUFFERS);
    private final ArrayBlockingQueue<ByteBuffer> free = new ArrayBlockingQueue<>(BUFFERS);
    private final Object ownership = new Object();
    private final Thread worker;
    private volatile boolean closed;
    private volatile String failure;
    private long cursor;
    private long started = -1;
    private long skipped;

    MenuVideoPlayback(List<Path> clips) {
        for (int i = 0; i < BUFFERS; i++) free.add(EMPTY);
        worker = Thread.ofPlatform().daemon().name("Yachiyo-menu-video").start(() -> decode(clips));
    }

    private void decode(List<Path> clips) {
        try {
            for (int i = 0; i < clips.size() && !closed; i++) {
                Path path = clips.get(i);
                boolean loop = i == clips.size() - 1;
                try {
                    decodeClip(path, i, loop);
                } catch (IOException e) {
                    if (loop) throw e;
                    Debug.log("menuvideo", "Skipping unavailable opening clip %s: %s", path, e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException | LinkageError | OutOfMemoryError e) {
            if (!closed) failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (closed) releaseUnused();
        }
    }

    private void decodeClip(Path path, int clip, boolean loop) throws IOException, InterruptedException {
        try (var media = LocalMedia.open(path)) {
            long start = cursor;
            try { stream(media.path(), clip, loop, start, true); }
            catch (IOException first) {
                if (closed || cursor != start) throw first;
                stream(media.path(), clip, loop, start, false);
            }
        }
    }

    private void stream(Path path, int clip, boolean loop, long offset, boolean allowD3d) throws IOException, InterruptedException {
        try (WindowsVideo video = WindowsVideo.open(path, allowD3d)) {
            WindowsVideo.Format format = video.format;
            long firstPts = Long.MIN_VALUE;
            long lastTime = offset - 1;
            long loopOffset = offset;
            long tailDuration = format.nominalFrameNanos();
            while (!closed) {
                ByteBuffer buffer = free.take();
                if (buffer.capacity() != format.frameBytes()) {
                    release(buffer);
                    buffer = MemoryUtil.memAlloc(format.frameBytes());
                }
                boolean queued = false;
                try {
                    long pts = video.read(buffer);
                    if (pts == WindowsVideo.END_OF_VIDEO) {
                        if (firstPts == Long.MIN_VALUE) throw new IOException("No decoded pictures");
                        if (!loop) return;
                        video.rewind();
                        loopOffset = cursor;
                        firstPts = Long.MIN_VALUE;
                        continue;
                    }
                    if (firstPts == Long.MIN_VALUE) firstPts = pts;
                    long time = loopOffset + Math.max(0, pts - firstPts) * 100;
                    if (time <= lastTime) time = lastTime + tailDuration;
                    if (time > loopOffset) tailDuration = time - lastTime;
                    lastTime = time;
                    synchronized (ownership) {
                        if (closed) return;
                        ready.add(new Frame(format, buffer, time, clip));
                        queued = true;
                        cursor = time + tailDuration;
                    }
                } finally {
                    if (!queued) recycle(buffer);
                }
            }
        }
    }

    Frame next(long now) {
        Frame first = ready.peek();
        if (first == null) return null;
        if (started < 0) started = now;
        long elapsed = now - started;
        Frame latest = null;
        for (int i = 0; i < BUFFERS && first != null && first.timeNanos() <= elapsed; i++) {
            if (latest != null) { recycle(latest.pixels()); skipped++; }
            latest = ready.poll();
            first = ready.peek();
        }
        return latest;
    }

    String failure() { return failure; }
    long skipped() { return skipped; }

    void recycle(ByteBuffer buffer) {
        synchronized (ownership) {
            if (closed) release(buffer);
            else free.add(buffer);
        }
    }

    private void releaseUnused() {
        synchronized (ownership) {
            Frame frame;
            while ((frame = ready.poll()) != null) release(frame.pixels());
            ByteBuffer buffer;
            while ((buffer = free.poll()) != null) release(buffer);
        }
    }

    private static void release(ByteBuffer buffer) {
        if (buffer != EMPTY) MemoryUtil.memFree(buffer);
    }

    @Override public void close() {
        synchronized (ownership) {
            closed = true;
            worker.interrupt();
            releaseUnused();
        }
    }
}
