package dev.yachiyoc.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists the .ogg files bundled in assets/yachiyoc/textures/sounds. The list is
 * intentionally file-driven: dropping a new ogg in that folder makes it appear
 * in every sound dropdown after the next resource build.
 */
public final class SoundRegistry {
    private static List<String> cache;

    private SoundRegistry() {
    }

    public static List<String> names() {
        if (cache == null) {
            cache = scan();
        }
        return cache;
    }

    public static void refresh() {
        cache = null;
    }

    private static List<String> scan() {
        List<String> result = new ArrayList<>();
        FabricLoader.getInstance().getModContainer("yachiyoc").flatMap(container ->
                container.findPath("assets/yachiyoc/textures/sounds")).ifPresent(dir -> {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ogg"))
                        .map(path -> path.getFileName().toString())
                        .sorted(Comparator.naturalOrder())
                        .forEach(result::add);
            } catch (IOException ignored) {
            }
        });
        if (result.isEmpty()) {
            readBundledList(result);
        }
        if (result.isEmpty()) {
            result.add("ding0.4.ogg");
        }
        return List.copyOf(result);
    }

    private static void readBundledList(List<String> result) {
        try (InputStream stream = SoundRegistry.class.getResourceAsStream(
                "/assets/yachiyoc/sound_list.txt")) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.lines()
                        .map(String::trim)
                        .filter(name -> name.endsWith(".ogg"))
                        .sorted(Comparator.naturalOrder())
                        .forEach(result::add);
            }
        } catch (IOException ignored) {
        }
    }
}
