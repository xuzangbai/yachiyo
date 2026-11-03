package dev.yachiyoc.module;

import net.minecraft.text.Text;

/**
 * Module categories. The order here is the order shown in the ClickGUI.
 * Colors are ARGB and intentionally muted so category accents do not fight
 * with the enabled-module highlight.
 */
public enum ModuleCategory {
    COMBAT("combat", 0xFFD97A9E),
    MOVEMENT("movement", 0xFF8C7BD8),
    PLAYER("player", 0xFFC08CD8),
    WORLD("world", 0xFF7F86D8),
    RENDER("render", 0xFF9B7EDC),
    MISC("misc", 0xFF9A8FB0),
    TOOLS("tools", 0xFFB57BD0),
    CLIENT("client", 0xFF7C6AE0);

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
