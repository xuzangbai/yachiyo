package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Consumer;

public final class StringSetting extends Setting<String> {
    private static StringSetting editing;

    private final Consumer<String> listener;

    public StringSetting(String owner, String id, String defaultValue) {
        this(owner, id, defaultValue, null);
    }

    public StringSetting(String owner, String id, String defaultValue, Consumer<String> listener) {
        super(owner, id, defaultValue);
        this.listener = listener;
    }

    @Override
    public void set(String value) {
        super.set(value);
        if (listener != null) {
            listener.accept(get());
        }
    }

    public boolean editing() {
        return editing == this;
    }

    public void startEditing() {
        if (editing != null && editing != this) {
            editing = null;
        }
        editing = this;
    }

    public void stopEditing() {
        if (editing == this) {
            editing = null;
        }
    }

    public static StringSetting active() {
        return editing;
    }

    public static void stopAllEditing() {
        editing = null;
    }

    public void append(char c) {
        if (c < 32 || value.length() >= 256) {
            return;
        }
        set(value + c);
    }

    public void backspace() {
        if (!value.isEmpty()) {
            set(value.substring(0, value.length() - 1));
        }
    }

    @Override
    public String displayValue() {
        return value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsString());
        }
    }
}
