package dev.yachiyoc.module;

import net.minecraft.text.Text;

/**
 * Module categories. The order here is the order shown in the ClickGUI.
 * Colors are ARGB and intentionally muted so category accents do not fight
 * with the enabled-module highlight.
 */
public enum ModuleCategory {
    COMBAT("combat", 0xFFFF6B6B),
    MOVEMENT("movement", 0xFF4DD0E1),
    PLAYER("player", 0xFF81C784),
    WORLD("world", 0xFFFFD54F),
    RENDER("render", 0xFFBA68C8),
    MISC("misc", 0xFF90A4AE),
    CLIENT("client", 0xFF64B5F6);

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
