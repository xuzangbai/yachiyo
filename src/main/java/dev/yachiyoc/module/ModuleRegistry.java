package dev.yachiyoc.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.yachiyoc.config.ConfigData;
import dev.yachiyoc.module.impl.client.Colors;
import dev.yachiyoc.module.impl.client.Config;
import dev.yachiyoc.module.impl.combat.Aura;
import dev.yachiyoc.module.impl.misc.Example;
import dev.yachiyoc.module.impl.misc.ViaFix;
import dev.yachiyoc.module.impl.render.NameTags;
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
    /** 功能级按键在模块设置表里的键前缀（`key:mace_swap` → 70）。 */
    private static final String BIND_PREFIX = "key:";

    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public void bootstrap() {
        register(new Aura());
        register(new StubModule("sprint", ModuleCategory.MOVEMENT));
        register(new StubModule("autotool", ModuleCategory.PLAYER));
        register(new StubModule("scaffold", ModuleCategory.WORLD));
        register(new NameTags());
        register(new StubModule("toolbox", ModuleCategory.TOOLS));
        register(new Config());
        register(new Colors());
        register(new Example());
        register(new ViaFix());
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
            // 功能级按键存在同一个表里，用 "key:" 前缀区分
            for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                if (entry.getKey().startsWith(BIND_PREFIX)
                        && entry.getValue() != null
                        && entry.getValue().isJsonPrimitive()) {
                    module.setBindById(entry.getKey().substring(BIND_PREFIX.length()),
                            entry.getValue().getAsInt());
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
            for (Map.Entry<String, Integer> entry : module.settingBinds().entrySet()) {
                values.put(BIND_PREFIX + entry.getKey(), new JsonPrimitive(entry.getValue()));
            }
            data.moduleSettings.put(module.id(), values);
        }
    }
}
