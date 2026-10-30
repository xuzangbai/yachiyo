package dev.yachiyoc.module;

import com.google.gson.JsonElement;
import dev.yachiyoc.config.ConfigData;
import dev.yachiyoc.module.setting.Setting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit module registry. Registration is a single place by design: it keeps
 * start-up order deterministic and makes trimming builds trivial.
 */
public final class ModuleRegistry {
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public void bootstrap() {
        register(new StubModule("aura", ModuleCategory.COMBAT));
        register(new StubModule("sprint", ModuleCategory.MOVEMENT));
        register(new StubModule("autotool", ModuleCategory.PLAYER));
        register(new StubModule("scaffold", ModuleCategory.WORLD));
        register(new StubModule("hud", ModuleCategory.RENDER));
        register(new StubModule("config", ModuleCategory.CLIENT));
        register(new ExampleModule());
    }

    public void register(Module module) {
        if (byId.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("Duplicate module id: " + module.id());
        }
        modules.add(module);
    }

    public Module byId(String id) {
        return byId.get(id);
    }

    public List<Module> all() {
        return List.copyOf(modules);
    }

    public List<Module> byCategory(ModuleCategory category) {
        return modules.stream()
                .filter(module -> module.category() == category)
                .sorted(Comparator.comparing(Module::id))
                .toList();
    }

    public int count() {
        return modules.size();
    }

    public void load(ConfigData data) {
        for (Module module : modules) {
            Boolean enabled = data.moduleStates.get(module.id());
            if (enabled != null) {
                module.setEnabled(enabled, false);
            }
            Map<String, JsonElement> values = data.moduleSettings.get(module.id());
            if (values == null) {
                continue;
            }
            for (Setting<?> setting : module.settings()) {
                JsonElement element = values.get(setting.id());
                if (element != null) {
                    setting.fromJson(element);
                }
            }
        }
    }

    public void save(ConfigData data) {
        for (Module module : modules) {
            data.moduleStates.put(module.id(), module.enabled());
            Map<String, JsonElement> values = new LinkedHashMap<>();
            for (Setting<?> setting : module.settings()) {
                values.put(setting.id(), setting.toJson());
            }
            data.moduleSettings.put(module.id(), values);
        }
    }
}
