package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class ColorSetting extends Setting<Integer> {
    private static final int[] PALETTE = {
            0xFF64B5F6, 0xFFFF6B6B, 0xFF81C784, 0xFFFFD54F,
            0xFFBA68C8, 0xFFFF8A65, 0xFF4DD0E1, 0xFFF2F2F5
    };

    public ColorSetting(String owner, String id, int defaultValue) {
        super(owner, id, defaultValue);
    }

    public void cycle() {
        int current = get();
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) {
                set(PALETTE[(i + 1) % PALETTE.length]);
                return;
            }
        }
        set(PALETTE[0]);
    }

    public int argb() {
        return get();
    }

    @Override
    public String displayValue() {
        return String.format(Locale.ROOT, "#%08X", get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsInt());
        }
    }
}
