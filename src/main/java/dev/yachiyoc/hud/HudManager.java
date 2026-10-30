package dev.yachiyoc.hud;

import com.google.gson.JsonElement;
import dev.yachiyoc.config.ConfigData;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.ui.render.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class HudManager {
    private static final Map<String, HudType> TYPES = new LinkedHashMap<>();
    private static final List<HudElement> ELEMENTS = new ArrayList<>();

    private HudManager() {
    }

    public static void bootstrap() {
        register(new HudType("item_display", "hud.yachiyoc.item_display.description", ItemDisplayHud::new));
        register(new HudType("drop_display", "hud.yachiyoc.drop_display.description", DropDisplayHud::new));
    }

    private static void register(HudType type) {
        TYPES.put(type.id(), type);
    }

    public static List<HudType> types() {
        return List.copyOf(TYPES.values());
    }

    public static List<HudElement> elements() {
        return List.copyOf(ELEMENTS);
    }

    public static HudElement add(String typeId, float x, float y) {
        HudType type = TYPES.get(typeId);
        if (type == null) {
            return null;
        }
        HudElement element = type.factory().get();
        element.setPosition(x, y);
        element.layout();
        ELEMENTS.add(element);
        return element;
    }

    public static void remove(HudElement element) {
        ELEMENTS.remove(element);
    }

    public static HudElement findAt(double mouseX, double mouseY) {
        for (int i = ELEMENTS.size() - 1; i >= 0; i--) {
            HudElement element = ELEMENTS.get(i);
            if (element.contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    public static void render(DrawContext context) {
        for (HudElement element : ELEMENTS) {
            element.layout();
            element.render(context);
        }
    }

    public static void renderEditorOverlay(DrawContext context, double mouseX, double mouseY, HudElement selected) {
        for (HudElement element : ELEMENTS) {
            boolean hovered = element.contains(mouseX, mouseY);
            int color = element == selected ? 0xFF63B8FF : hovered ? 0xB0FFFFFF : 0x55FFFFFF;
            Render2D.roundedRect(context, element.x() - 2.0F, element.y() - 2.0F,
                    3.0F, element.height() + 4.0F, 1.5F, color);
            Render2D.roundedRect(context, element.x() + element.width() - 1.0F, element.y() - 2.0F,
                    3.0F, element.height() + 4.0F, 1.5F, color);
        }
    }

    public static void load(ConfigData data) {
        ELEMENTS.clear();
        for (ConfigData.HudData entry : data.hudElements) {
            HudType type = TYPES.get(entry.type);
            if (type == null) {
                continue;
            }
            HudElement element = type.factory().get();
            element.setPosition(entry.x, entry.y);
            for (Setting<?> setting : element.settings()) {
                JsonElement value = entry.settings.get(setting.id());
                if (value != null) {
                    setting.fromJson(value);
                }
            }
            element.layout();
            ELEMENTS.add(element);
        }
    }

    public static void save(ConfigData data) {
        data.hudElements.clear();
        for (HudElement element : ELEMENTS) {
            ConfigData.HudData entry = new ConfigData.HudData();
            entry.type = element.id();
            entry.x = element.x();
            entry.y = element.y();
            for (Setting<?> setting : element.settings()) {
                entry.settings.put(setting.id(), setting.toJson());
            }
            data.hudElements.add(entry);
        }
    }

    public record HudType(String id, String descriptionKey, Supplier<HudElement> factory) {
    }
}
