package dev.yachiyoc.render;

import dev.yachiyoc.config.ConfigData;
import dev.yachiyoc.module.ModuleRegistry;
import dev.yachiyoc.module.impl.render.NameTags;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Standalone checks for fixed pixel size, independent health layers and feature compatibility. */
public final class NameTagLayoutTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (double depth : new double[] {0.1, 1, 4, 16, 96, 512}) {
            for (double projection : new double[] {0.75, 1.428, 3.73}) {
                for (int height : new int[] {240, 360, 1080}) {
                    float units = NameTagLayout.unitsPerPixel(depth, projection, height);
                    for (double scale : new double[] {0.4, 1, 2.5}) {
                        double pixels = units * 9 * scale * projection * height / (2 * depth);
                        close(pixels, 9 * scale, "Projected text size is independent of distance, FOV and viewport");
                    }
                }
            }
        }
        close(NameTagLayout.unitsPerPixel(-2, 1.4, 360), 0, "Behind-camera anchors are rejected");
        close(NameTagLayout.unitsPerPixel(0.01, 1.4, 360), 0, "Near-plane anchors are rejected");
        close(NameTagLayout.unitsPerPixel(4, 0, 360), 0, "Invalid projection is rejected");

        NameTagLayout.Vitals vitals = new NameTagLayout.Vitals();
        long start = 1_000_000_000L;
        vitals.update(20, 20, 8, start);
        close(vitals.fill(), 1, "Absorption does not compress the full-health bar");
        close(vitals.absorption(), 0.4, "Absorption uses its own track");
        vitals.update(10, 20, 8, start + 100_000_000L);
        check(vitals.fill() > 0.5 && vitals.fill() < 1, "Health loss animates toward the new value");
        close(vitals.trail(), 1, "Damage trail holds the old health value");
        vitals.update(10, 20, 8, start + 400_000_000L);
        close(vitals.trail(), 1, "Repeated frames do not restart or prematurely clear the damage hold");
        vitals.update(10, 20, 8, start + 800_000_000L);
        check(vitals.trail() < 1 && vitals.trail() >= vitals.fill(), "Damage trail decays behind current HP");
        vitals.update(20, 20, 0, start + 1_000_000_000L);
        check(vitals.fill() > 0.9 && vitals.trail() >= vitals.fill(), "Healing keeps the visual layers consistent");
        check(vitals.absorption() > 0 && vitals.absorption() < 0.4, "Absorption fades instead of disappearing abruptly");
        vitals.update(20, 40, 0, start + 1_100_000_000L);
        close(vitals.fill(), 0.5, "Max-health changes immediately establish the new ratio");
        close(vitals.trail(), 0.5, "Max-health changes do not invent damage");
        vitals.update(-5, 0, -2, start + 1_200_000_000L);
        close(vitals.fill(), 0, "Invalid low health is clamped");
        close(vitals.absorption(), 0, "Negative absorption is clamped");

        String customName = "这是完整的自定义名字😀 Protection IV";
        List<String> wrapped = NameTagLayout.wrap(customName, 5, text -> text.codePointCount(0, text.length()));
        check(String.join("", wrapped).equals(customName), "Wrapping preserves the complete name and Unicode");
        for (String line : wrapped) check(line.codePointCount(0, line.length()) <= 5, "Wrapped text fits the column");
        check(NameTagLayout.wrap("", 5, String::length).isEmpty(), "Empty text does not allocate empty display rows");

        NameTags module = new NameTags();
        Set<String> ids = module.settings().stream().map(setting -> setting.id()).collect(Collectors.toSet());
        for (String id : List.of("bind", "trigger", "range", "scale", "height", "health", "health_bar", "distance",
                "ping", "potions", "equip_gap_x", "equip_gap_y", "equip_scale", "equipment", "equip_name", "equip_enchant",
                "turtle_size", "effect_time", "turtle_strict", "background", "through_walls", "self", "invisible")) {
            check(ids.contains(id), "Existing setting is preserved: " + id);
        }
        check(module.settings().stream().anyMatch(setting -> setting.id().equals("constant_size") && Boolean.TRUE.equals(setting.get())),
                "Constant screen size is enabled for existing configs without the new field");
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module);
        ConfigData legacy = new ConfigData();
        legacy.moduleStates.put("nametags", true);
        legacy.moduleSettings.put("nametags", Map.of("scale", new JsonPrimitive(1.35), "equipment", new JsonPrimitive(true),
                "effect_time", new JsonPrimitive(false), "turtle_strict", new JsonPrimitive(true)));
        registry.load(legacy);
        check(module.enabled(), "Existing enabled state is restored");
        check(module.settings().stream().anyMatch(setting -> setting.id().equals("scale") && setting.get().equals(1.35)),
                "Existing scale is retained");
        java.lang.reflect.Field listeners = Render3DCallbacks.class.getDeclaredField("LISTENERS");
        listeners.setAccessible(true);
        check(!((List<?>) listeners.get(null)).isEmpty(), "Loaded enabled state has a renderer without calling onEnable");
        System.out.println("NameTag layout and compatibility checks passed: " + checks);
    }

    private static void close(double actual, double expected, String message) { check(Math.abs(actual - expected) < 0.00001, message + ": " + actual); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
