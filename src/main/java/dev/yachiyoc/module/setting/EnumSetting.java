package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Language;

import java.util.Locale;
import java.util.function.Consumer;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;
    private final Consumer<E> listener;

    public EnumSetting(String owner, String id, Class<E> type, E defaultValue) {
        this(owner, id, type, defaultValue, null);
    }

    public EnumSetting(String owner, String id, Class<E> type, E defaultValue, Consumer<E> listener) {
        super(owner, id, defaultValue);
        this.values = type.getEnumConstants();
        this.listener = listener;
    }

    @Override
    public void set(E value) {
        super.set(value);
        if (listener != null) {
            listener.accept(get());
        }
    }

    public E[] values() {
        return values.clone();
    }

    public void cycle() {
        int next = (get().ordinal() + 1) % values.length;
        set(values[next]);
    }

    @Override
    public String displayValue() {
        return displayValue(get());
    }

    public String displayValue(E value) {
        String key = translationKey() + ".value." + value.name().toLowerCase(Locale.ROOT);
        return Language.getInstance().get(key, value.name());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get().name());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        String name = element.getAsString();
        for (E candidate : values) {
            if (candidate.name().equalsIgnoreCase(name)) {
                set(candidate);
                return;
            }
        }
    }
}
