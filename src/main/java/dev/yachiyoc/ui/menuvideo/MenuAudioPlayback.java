package dev.yachiyoc.ui.menuvideo;

import dev.yachiyoc.platform.Debug;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.file.Path;

/** Streams a companion track on its own clock, independently of video loops. */
final class MenuAudioPlayback implements AutoCloseable {
    private final Thread worker;
    private volatile boolean closed;
    private volatile SourceDataLine line;
    private volatile float gain;

    MenuAudioPlayback(Path file, float gain) {
        this.gain = gain;
        worker = Thread.ofPlatform().daemon().name("Yachiyo-menu-audio").start(() -> play(file));
    }

    private void play(Path file) {
        try (var media = LocalMedia.open(file);
             var decoder = new WindowsAudio(media.path());
             var output = AudioSystem.getSourceDataLine(decoder.format)) {
            if (closed) return;
            int frameSize = decoder.format.getFrameSize();
            int buffered = Math.round(decoder.format.getSampleRate() / 20) * frameSize;
            output.open(decoder.format, buffered);
            if (closed) return;
            output.start();
            line = output;
            byte[] pcm = new byte[4096];
            boolean readSinceSeek = false;
            while (!closed) {
                int count = decoder.read(pcm);
                if (count < 0) {
                    if (!readSinceSeek) throw new IOException("No decoded audio samples");
                    decoder.rewind();
                    readSinceSeek = false;
                    continue;
                }
                readSinceSeek = true;
                applyGain(pcm, count, gain);
                for (int offset = 0; offset < count && !closed;) {
                    int written = output.write(pcm, offset, count - offset);
                    if (written == 0 && !closed) throw new IOException("Audio output stopped");
                    offset += written;
                }
            }
            output.flush();
        } catch (IOException | LineUnavailableException | RuntimeException | LinkageError e) {
            if (!closed) MinecraftClient.getInstance().execute(() ->
                    Debug.log("menuvideo", "Background audio unavailable for %s: %s", file.getFileName(), e.getMessage()));
        } finally {
            line = null;
        }
    }

    private static void applyGain(byte[] pcm, int count, float gain) {
        if (gain == 1) return;
        for (int i = 0; i < count; i += 2) {
            short sample = (short) ((pcm[i] & 0xff) | pcm[i + 1] << 8);
            int scaled = Math.round(sample * gain);
            pcm[i] = (byte) scaled;
            pcm[i + 1] = (byte) (scaled >> 8);
        }
    }

    void gain(float volume) { gain = volume; }
    boolean isPlaying() {
        SourceDataLine output = line;
        return !closed && output != null && output.isOpen();
    }

    @Override public void close() {
        closed = true;
        worker.interrupt();
    }
}
