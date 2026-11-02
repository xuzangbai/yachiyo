package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.yachiyoc.ui.render.ExternalFontRenderer;
import dev.yachiyoc.ui.render.FontChoice;
import dev.yachiyoc.ui.render.Text2D;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Font picker whose options are built once at startup:
 * vanilla, the bundled Ma Shan font, and every .ttf in the game's
 * config/yachiyoc/fonts folder. Adding files requires a game restart.
 */
public final class FontSetting extends Setting<String> {
    public enum Kind {
        VANILLA,
        BUNDLED,
        EXTERNAL
    }

    public record Option(String id, String label, Kind kind, Path path) {
    }

    private final List<Option> options = new ArrayList<>();

    public FontSetting(String owner, String id) {
        super(owner, id, "vanilla");
        scan();
        apply();
    }

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("config").resolve("yachiyoc").resolve("fonts");
    }

    private void scan() {
        options.add(new Option("vanilla", "Default", Kind.VANILLA, null));
        options.add(new Option("mashan", "Ma Shan Zheng", Kind.BUNDLED, null));
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(path -> {
                            String file = path.getFileName().toString();
                            String name = file.substring(0, file.length() - 4);
                            options.add(new Option("ext:" + file, name, Kind.EXTERNAL, path));
                        });
            }
        } catch (IOException ignored) {
        }
    }

    public List<Option> options() {
        return List.copyOf(options);
    }

    public Option selected() {
        for (Option option : options) {
            if (option.id().equals(get())) {
                return option;
            }
        }
        return options.getFirst();
    }

    private void apply() {
        Option option = selected();
        switch (option.kind()) {
            case VANILLA -> Text2D.setFont(FontChoice.VANILLA);
            case BUNDLED -> Text2D.setFont(FontChoice.MA_SHAN);
            case EXTERNAL -> Text2D.setExternalFont(option.path());
        }
    }

    @Override
    public void set(String newValue) {
        String candidate = newValue;
        boolean valid = candidate != null
                && options.stream().anyMatch(option -> option.id().equals(candidate));
        super.set(valid ? candidate : "vanilla");
        apply();
    }

    public String label(Option option) {
        return switch (option.kind()) {
            case VANILLA -> Text.translatable("setting.yachiyoc.client.config.font.value.vanilla").getString();
            case BUNDLED -> Text.translatable("setting.yachiyoc.client.config.font.value.ma_shan").getString();
            case EXTERNAL -> option.label();
        };
    }

    @Override
    public String displayValue() {
        return label(selected());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsString());
        }
    }
}
