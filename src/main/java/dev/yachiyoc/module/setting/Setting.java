package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import net.minecraft.text.Text;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A single configurable value owned by a module.
 *
 * <p>Settings are deliberately Minecraft-light: they expose a display value and
 * JSON (de)serialization, while the GUI decides how to render/edit them. Adding
 * a new setting type only requires a new subclass plus one branch in
 * {@code SettingRow}.</p>
 */
public abstract class Setting<T> {
    private final String owner;
    private final String id;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visible = () -> true;
    private Supplier<Text> description =
            () -> Text.translatable("ui.yachiyoc.no_description");

    protected Setting(String owner, String id, T defaultValue) {
        this.owner = Objects.requireNonNull(owner);
        this.id = Objects.requireNonNull(id);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String owner() {
        return owner;
    }

    public String id() {
        return id;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        if (!Objects.equals(value, newValue)) {
            value = newValue;
            onChanged();
        }
    }

    public T defaultValue() {
        return defaultValue;
    }

    public void reset() {
        set(defaultValue);
    }

    public Setting<T> visibleWhen(BooleanSupplier predicate) {
        this.visible = Objects.requireNonNull(predicate);
        return this;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    public Setting<T> description(Supplier<Text> supplier) {
        this.description = Objects.requireNonNull(supplier);
        return this;
    }

    public Text description() {
        return description.get();
    }

    public String translationKey() {
        return "setting.yachiyoc." + owner + "." + id;
    }

    public Text label() {
        return Text.translatable(translationKey());
    }

    public abstract String displayValue();

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);

    protected void onChanged() {
    }
}
