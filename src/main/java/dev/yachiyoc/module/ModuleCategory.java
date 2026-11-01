package dev.yachiyoc.module;

import net.minecraft.text.Text;

/**
 * Module categories. The order here is the order shown in the ClickGUI.
 * Colors are ARGB and intentionally muted so category accents do not fight
 * with the enabled-module highlight.
 */
public enum ModuleCategory {
    COMBAT("combat", 0xFFC84F4F),
    MOVEMENT("movement", 0xFF2A8F9C),
    PLAYER("player", 0xFF3E8548),
    WORLD("world", 0xFF92731E),
    RENDER("render", 0xFF8A4A96),
    MISC("misc", 0xFF5F6B72),
    TOOLS("tools", 0xFF9B5A9B),
    CLIENT("client", 0xFF2F6C9E);

    private final String id;
    private final int accent;

    ModuleCategory(String id, int accent) {
        this.id = id;
        this.accent = accent;
    }

    public String id() {
        return id;
    }

    public int accent() {
        return accent;
    }

    public Text label() {
        return Text.translatable("category.yachiyoc." + id);
    }
}
