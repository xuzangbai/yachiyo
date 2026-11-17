package dev.yachiyoc.ui.loading;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Classpath assets are available before Minecraft's resource-pack reload has completed. */
public final class LoadingAssets {
    public static final String ROOT = "/assets/yachiyoc/loading/";
    public static final int BACKGROUND = 0xFFF9F044;

    public record Clip(String id, String audio, int sourceWidth, int sourceHeight, int durationMs,
                       int x, int y, int width, int height, int frameWidth, int frameHeight,
                       int columns, int atlasWidth, int atlasHeight, int[] frameTimes) {
        public int frameAt(long millis) {
            int index = Arrays.binarySearch(frameTimes, (int) Math.clamp(millis, 0, durationMs - 1));
            return index >= 0 ? index : Math.max(0, -index - 2);
        }

        public int u(int frame) { return frame % columns * (frameWidth + 4) + 2; }
        public int v(int frame) { return frame / columns * (frameHeight + 4) + 2; }

        private void validate() throws IOException {
            if (id == null || !id.matches("s1e(?:02|07)_0[1-4]") || !audio.equals(id + ".wav")
                    || durationMs < 100 || durationMs > 30_000 || sourceWidth <= 0 || sourceHeight <= 0
                    || x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > sourceWidth || y + height > sourceHeight
                    || columns <= 0 || frameWidth <= 0 || frameHeight <= 0 || atlasWidth > 2048 || atlasHeight > 2048
                    || frameTimes == null || frameTimes.length == 0 || frameTimes[0] != 0) {
                throw new IOException("Invalid bundled loading clip: " + id);
            }
            for (int index = 0; index < frameTimes.length; index++) {
                if (frameTimes[index] < 0 || frameTimes[index] >= durationMs
                        || (index > 0 && frameTimes[index] <= frameTimes[index - 1])
                        || u(index) + frameWidth > atlasWidth || v(index) + frameHeight > atlasHeight) {
                    throw new IOException("Invalid frame bounds: " + id + " / " + index);
                }
            }
        }
    }

    private record Manifest(int version, Clip[] clips) { }

    private LoadingAssets() { }

    public static Clip[] read() throws IOException {
        try (InputStream stream = LoadingAssets.class.getResourceAsStream(ROOT + "manifest.json")) {
            if (stream == null) throw new IOException("Bundled loading manifest is missing");
            Manifest manifest = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Manifest.class);
            if (manifest == null || manifest.version != 1 || manifest.clips == null || manifest.clips.length != 6)
                throw new IOException("Expected the six approved loading clips");
            for (Clip clip : manifest.clips) clip.validate();
            if (Arrays.stream(manifest.clips).map(Clip::id).distinct().count() != 6)
                throw new IOException("Duplicate loading clips");
            return manifest.clips;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot read the bundled loading manifest", exception);
        }
    }
}
