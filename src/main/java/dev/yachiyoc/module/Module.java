package dev.yachiyoc.module;

import dev.yachiyoc.module.setting.BindSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.Setting;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base module. A module is intentionally a plain state holder plus lifecycle
 * hooks; gameplay logic will live in subclasses later. The UI never calls
 * Minecraft APIs on behalf of a module, it only flips {@link #setEnabled(boolean)}.
 */
public abstract class Module {
    private final String id;
    private final ModuleCategory category;
    private final boolean implemented;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;

    public final BindSetting bind = add(new BindSetting("client", "bind", -1));
    public final EnumSetting<TriggerMode> trigger =
            add(new EnumSetting<>("client", "trigger", TriggerMode.class, TriggerMode.TOGGLE));

    protected Module(String id, ModuleCategory category) {
        this(id, category, true);
    }

    protected Module(String id, ModuleCategory category, boolean implemented) {
        this.id = Objects.requireNonNull(id);
        this.category = Objects.requireNonNull(category);
        this.implemented = implemented;
    }

    protected final <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String id() {
        return id;
    }

    public ModuleCategory category() {
        return category;
    }

    public boolean implemented() {
        return implemented;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public Text name() {
        return Text.translatable("module.yachiyoc." + id);
    }

    public Text description() {
        return Text.translatable("module.yachiyoc." + id + ".description");
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        setEnabled(value, true);
    }

    public void setEnabled(boolean value, boolean notify) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (!notify) {
            return;
        }
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    @Override
    public String toString() {
        return id;
    }
}
