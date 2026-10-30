package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Language;

import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;

    public EnumSetting(String owner, String id, Class<E> type, E defaultValue) {
        super(owner, id, defaultValue);
        this.values = type.getEnumConstants();
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
        String key = translationKey() + ".value." + get().name().toLowerCase(Locale.ROOT);
        return Language.getInstance().get(key, get().name());
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
