package dev.yachiyoc.ui.menuvideo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** A Source Reader and its COM apartment, owned by one playback worker. */
final class WindowsVideo implements AutoCloseable {
    static final long END_OF_VIDEO = Long.MIN_VALUE;
    private static boolean loaded;

    record Format(int width, int height, int fpsNumerator, int fpsDenominator, int matrix, boolean fullRange,
                  boolean d3d, int aspectWidth, int aspectHeight, int rotation, int strideHint) {
        int textureWidth() { return (width + 3) / 4; }
        int textureHeight() { return height + height / 2; }
        int frameBytes() { return Math.multiplyExact(Math.multiplyExact(textureWidth(), textureHeight()), 4); }
        double fps() { return fpsNumerator > 0 && fpsDenominator > 0 ? (double) fpsNumerator / fpsDenominator : 0; }
        long nominalFrameNanos() { return fps() > 0 ? Math.round(1_000_000_000 / fps()) : 33_333_333; }
    }

    private final long handle;
    final Format format;

    static boolean supported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "");
        return os.startsWith("windows") && (architecture.equals("amd64") || architecture.equals("x86_64"));
    }

    static WindowsVideo open(Path path, boolean allowD3d) throws IOException {
        load();
        return new WindowsVideo(path.toAbsolutePath().normalize(), allowD3d);
    }

    private WindowsVideo(Path path, boolean allowD3d) throws IOException {
        int[] metadata = new int[11];
        handle = open0(path.toString(), metadata, allowD3d);
        if (handle == 0) throw new IOException("Media Foundation did not return a video reader");
        format = new Format(metadata[0], metadata[1], metadata[2], metadata[3], metadata[4], metadata[5] != 0,
                metadata[6] != 0, metadata[7], metadata[8], metadata[9], metadata[10]);
    }

    static synchronized void load() throws IOException {
        if (loaded) return;
        if (!supported()) throw new IOException("Windows x64 video backend unavailable");
        byte[] library;
        try (InputStream stream = WindowsVideo.class.getResourceAsStream("/assets/yachiyoc/native/windows-x86_64/menu_video.dll")) {
            if (stream == null) throw new IOException("Missing Media Foundation bridge");
            library = stream.readAllBytes();
        }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(library)); }
        catch (NoSuchAlgorithmException e) { throw new IOException(e); }
        Path folder = Files.createTempDirectory("yachiyoc-menu-video-");
        folder.toFile().deleteOnExit();
        Path file = folder.resolve("yachiyoc-" + hash.substring(0, 16) + ".dll");
        try { Files.write(file, library, java.nio.file.StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException e) { throw new IOException("Video bridge already exists in its private directory", e); }
        file.toFile().deleteOnExit();
        System.load(file.toString());
        loaded = true;
    }

    long read(ByteBuffer destination) throws IOException { return read0(handle, destination, format.strideHint); }
    void rewind() throws IOException { rewind0(handle); }
    @Override public void close() { close0(handle); }

    private static native long open0(String file, int[] metadata, boolean allowD3d) throws IOException;
    private static native long read0(long handle, ByteBuffer destination, int strideHint) throws IOException;
    private static native void rewind0(long handle) throws IOException;
    private static native void close0(long handle);
}
