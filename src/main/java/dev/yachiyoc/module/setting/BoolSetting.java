package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String owner, String id, boolean defaultValue) {
        super(owner, id, defaultValue);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public String displayValue() {
        return get() ? "ON" : "OFF";
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsBoolean());
        }
    }
}
