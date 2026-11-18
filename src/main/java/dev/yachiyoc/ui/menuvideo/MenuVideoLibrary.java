package dev.yachiyoc.ui.menuvideo;

import com.google.gson.JsonParser;
import dev.yachiyoc.platform.Debug;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MenuVideoLibrary {
    static final Set<String> EXTENSIONS = Set.of("mp4", "mkv", "mov", "webm", "avi", "flv", "wmv", "m4v", "ts", "mpg", "mpeg", "3gp");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("ogg", "mp3", "wav", "flac", "m4a", "aac", "opus", "wma");
    private static final Comparator<Path> FILE_ORDER = Comparator.comparing(path -> path.getFileName().toString());

    record Entry(String id, String name, List<Path> clips, Path audio) {
        static Entry file(Path path) {
            Path absolute = path.toAbsolutePath().normalize();
            return new Entry(absolute.toString(), absolute.getFileName().toString(), List.of(absolute), null);
        }
    }

    private MenuVideoLibrary() { }

    static Path folder() { return FabricLoader.getInstance().getGameDir().resolve("diyvideo"); }

    static List<Entry> scan(String saved) {
        var entries = new ArrayList<Entry>();
        try {
            Files.createDirectories(folder());
        } catch (IOException | SecurityException e) {
            Debug.log("menuvideo", "Could not create the video library: %s", e.getMessage());
        }
        addGroups(entries, folder(), "video_local_");
        addGroups(entries, folder().resolveSibling("diyvideo-1"), "video_scene_");
        for (Path path : children(folder().resolveSibling("yachiyo-backgrounds"))) {
            if (isVideo(path)) entries.add(Entry.file(path));
        }
        FabricLoader.getInstance().getModContainer("yachiyoc").ifPresent(mod -> {
            for (Path root : mod.getRootPaths()) addGroups(entries, root.resolve("diy/menu_video"), "video_bundled_");
        });
        Entry external = external(saved);
        if (external != null && entries.stream().noneMatch(entry -> entry.id().equals(external.id()))) {
            entries.addFirst(external);
        }
        return List.copyOf(entries);
    }

    private static Entry external(String saved) {
        if (saved == null || saved.isEmpty()) return null;
        try {
            Path path = Path.of(saved);
            return path.isAbsolute() && isVideo(path) ? Entry.file(path) : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static void addGroups(List<Entry> entries, Path root, String prefix) {
        for (Path folder : children(root)) {
            if (!Files.isDirectory(folder)) continue;
            List<Path> files = children(folder);
            List<Path> videos = files.stream().filter(MenuVideoLibrary::isVideo).toList();
            if (videos.isEmpty()) continue;
            String name = folder.getFileName().toString();
            String id = prefix + name;
            if (entries.stream().anyMatch(entry -> entry.id().equals(id))) continue;
            List<Path> clips = playlist(folder.resolve("scene.json"), videos);
            if (clips.isEmpty()) {
                Path intro = videos.stream().filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                        .startsWith("intro")).findFirst().orElse(null);
                Path loop = videos.stream().filter(path -> !path.equals(intro)).findFirst().orElse(videos.getFirst());
                clips = intro == null || intro.equals(loop) ? List.of(loop) : List.of(intro, loop);
            }
            Path audio = files.stream().filter(path -> matches(path, AUDIO_EXTENSIONS)).findFirst().orElse(null);
            entries.add(new Entry(id, name, clips, audio));
        }
    }

    private static List<Path> playlist(Path manifest, List<Path> videos) {
        if (!Files.isRegularFile(manifest)) return List.of();
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return List.of();
            var ordered = root.getAsJsonObject().get("playlist");
            if (ordered == null || !ordered.isJsonArray()) return List.of();
            var clips = new ArrayList<Path>();
            for (var item : ordered.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
                String name = item.getAsString();
                videos.stream().filter(path -> path.getFileName().toString().equals(name)).findFirst().ifPresent(clips::add);
            }
            return List.copyOf(clips);
        } catch (IOException | RuntimeException e) {
            Debug.log("menuvideo", "Could not read %s: %s", manifest, e.getMessage());
            return List.of();
        }
    }

    private static List<Path> children(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.sorted(FILE_ORDER).toList();
        } catch (IOException | SecurityException e) {
            Debug.log("menuvideo", "Could not list %s: %s", directory, e.getMessage());
            return List.of();
        }
    }

    static boolean isVideo(Path path) {
        return matches(path, EXTENSIONS);
    }

    private static boolean matches(Path path, Set<String> extensions) {
        if (path.getFileName() == null || !Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
