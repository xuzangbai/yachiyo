package dev.yachiyoc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON configuration. Writes through a temp file + atomic move so a crash cannot
 * leave a half-written config behind.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("yachiyoc/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path path;
    private ConfigData data = new ConfigData();

    public ConfigManager() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("yachiyoc.json");
    }

    public ConfigData data() {
        return data;
    }

    public Path path() {
        return path;
    }

    public void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                data = loaded;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read {}, using defaults", path, e);
            backupBrokenFile();
        }
        normalize();
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save {}", path, e);
        }
    }

    private void normalize() {
        if (data.prefix == null || data.prefix.isBlank()) {
            data.prefix = "--";
        }
        if (data.openKey <= 0) {
            data.openKey = 265;
        }
        if (data.language == null || data.language.isBlank()) {
            data.language = "zh_cn";
        }
        if (data.moduleStates == null) {
            data.moduleStates = new java.util.LinkedHashMap<>();
        }
        if (data.moduleSettings == null) {
            data.moduleSettings = new java.util.LinkedHashMap<>();
        }
        if (data.hudElements == null) {
            data.hudElements = new java.util.ArrayList<>();
        }
    }

    private void backupBrokenFile() {
        try {
            String name = path.getFileName() + ".broken-" + LocalDateTime.now().format(STAMP);
            Files.move(path, path.resolveSibling(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}
