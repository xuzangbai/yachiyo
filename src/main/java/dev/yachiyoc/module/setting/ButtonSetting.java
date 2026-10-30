package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

public final class ButtonSetting extends Setting<Void> {
    private final Runnable action;

    public ButtonSetting(String owner, String id, Runnable action) {
        super(owner, id, null);
        this.action = action;
    }

    public void run() {
        action.run();
    }

    @Override
    public String displayValue() {
        return "";
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromJson(JsonElement element) {
        // action only; nothing to load
    }
}
