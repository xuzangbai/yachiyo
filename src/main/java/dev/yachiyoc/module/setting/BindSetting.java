package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Language;

public final class BindSetting extends Setting<Integer> {
    private boolean listening;

    public BindSetting(String owner, String id, int defaultValue) {
        super(owner, id, defaultValue);
    }

    public boolean listening() {
        return listening;
    }

    public void startListening() {
        listening = true;
    }

    public void cancelListening() {
        listening = false;
    }

    public void setKey(int keyCode) {
        listening = false;
        set(keyCode);
    }

    @Override
    public String displayValue() {
        if (listening) {
            return "...";
        }
        int code = get();
        if (code <= 0) {
            return Language.getInstance().get("ui.yachiyoc.none", "NONE");
        }
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
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
