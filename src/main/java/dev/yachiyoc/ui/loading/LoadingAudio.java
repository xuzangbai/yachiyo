package dev.yachiyoc.ui.loading;

import dev.yachiyoc.platform.Debug;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Startup audio must work while Minecraft's sound resources are still being reloaded. */
final class LoadingAudio implements AutoCloseable {
    private record Request(long serial, String file, long elapsedMs, long requestedAt, float gain) { }

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "YachiyoC-loading-audio");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Request requested = new Request(0, null, 0, 0, 0);
    private volatile boolean closed;
    private Clip line;
    private String opened;
    private long applied = -1;
    private float gain = -1;
    private boolean unavailable;

    LoadingAudio() { worker.scheduleWithFixedDelay(this::apply, 0, 25, TimeUnit.MILLISECONDS); }

    void play(String file, long elapsedMs, float volume) {
        Request previous = requested;
        requested = new Request(previous.serial + 1, file, elapsedMs, System.nanoTime(), Math.clamp(volume, 0, 1));
    }

    void gain(float volume) {
        Request previous = requested;
        requested = new Request(previous.serial, previous.file, previous.elapsedMs, previous.requestedAt, Math.clamp(volume, 0, 1));
    }

    void stop() {
        Request previous = requested;
        if (previous.file != null) requested = new Request(previous.serial + 1, null, 0, 0, 0);
    }

    private void apply() {
        if (closed || unavailable) return;
        Request request = requested;
        try {
            if (request.file == null) {
                if (line != null) line.stop();
                return;
            }
            if (!request.file.equals(opened)) {
                if (line != null) { line.close(); line = null; }
                try (InputStream raw = LoadingAudio.class.getResourceAsStream(LoadingAssets.ROOT + request.file)) {
                    if (raw == null) throw new IllegalStateException("Missing loading audio " + request.file);
                    try (AudioInputStream stream = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                        line = AudioSystem.getClip();
                        line.open(stream);
                    }
                }
                opened = request.file;
                gain = -1;
            }
            if (request.gain != gain) {
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float decibels = request.gain <= 0 ? control.getMinimum() : (float) (20 * Math.log10(request.gain));
                    control.setValue(Math.clamp(decibels, control.getMinimum(), control.getMaximum()));
                } else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                    FloatControl control = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                    control.setValue(Math.clamp(request.gain, control.getMinimum(), control.getMaximum()));
                }
                gain = request.gain;
            }
            if (request.serial != applied) {
                line.stop();
                long offset = request.elapsedMs * 1000 + Math.max(0, (System.nanoTime() - request.requestedAt) / 1000);
                line.setMicrosecondPosition(Math.min(offset, Math.max(0, line.getMicrosecondLength() - 1000)));
                if (request.gain > 0) line.start();
                applied = request.serial;
            }
        } catch (Throwable exception) {
            unavailable = true;
            if (line != null) { line.close(); line = null; }
            MinecraftClient.getInstance().execute(() -> Debug.log("loading", "Loading audio unavailable: %s", exception.toString()));
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> { if (line != null) { line.stop(); line.close(); line = null; } });
        worker.shutdown();
    }
}
