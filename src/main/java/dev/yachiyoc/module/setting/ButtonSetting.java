package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public final class ButtonSetting extends Setting<Void> {
    private final Runnable action;
    private final Supplier<Text> caption;

    public ButtonSetting(String owner, String id, Runnable action) {
        this(owner, id, action, () -> Text.translatable("ui.yachiyoc.run"));
    }

    public ButtonSetting(String owner, String id, Runnable action, Supplier<Text> caption) {
        super(owner, id, null);
        this.action = action;
        this.caption = caption;
    }

    public Text caption() {
        return caption.get();
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
