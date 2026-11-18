package dev.yachiyoc.ui.menuvideo;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.file.Path;

/** A PCM reader sharing the menu video Media Foundation bridge. */
final class WindowsAudio implements AutoCloseable {
    private final long handle;
    final AudioFormat format;

    WindowsAudio(Path file) throws IOException {
        WindowsVideo.load();
        int[] metadata = new int[2];
        handle = open0(file.toAbsolutePath().normalize().toString(), metadata);
        if (handle == 0) throw new IOException("Media Foundation did not return an audio reader");
        format = new AudioFormat(metadata[0], 16, metadata[1], true, false);
    }

    int read(byte[] target) throws IOException { return read0(handle, target); }
    void rewind() throws IOException { rewind0(handle); }
    @Override public void close() { close0(handle); }

    private static native long open0(String file, int[] metadata) throws IOException;
    private static native int read0(long handle, byte[] target) throws IOException;
    private static native void rewind0(long handle) throws IOException;
    private static native void close0(long handle);
}
