package dev.yachiyoc.ui.menuvideo;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Media Foundation opens local paths. Stage JAR resources as temporary files. */
record LocalMedia(Path path, boolean temporary) implements AutoCloseable {
    static LocalMedia open(Path source) throws IOException {
        if (source.getFileSystem() == FileSystems.getDefault()) return new LocalMedia(source, false);
        String name = source.getFileName().toString();
        Path copy = Files.createTempFile("yachiyo-menu-", name.substring(name.lastIndexOf('.')));
        try {
            Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
            return new LocalMedia(copy, true);
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(copy); }
            catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    @Override public void close() throws IOException {
        if (temporary) Files.deleteIfExists(path);
    }
}
