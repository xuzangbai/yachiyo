package dev.yachiyoc.ui.render;

import net.minecraft.util.Identifier;

/**
 * UI font choice. Only the Ma Shan Zheng font is bundled; external fonts are
 * loaded from config/yachiyoc/fonts/custom.ttf at runtime.
 */
public enum FontChoice {
    VANILLA(null),
    MA_SHAN(Identifier.of("yachiyoc", "mashan")),
    EXTERNAL(null);

    private final Identifier fontId;

    FontChoice(Identifier fontId) {
        this.fontId = fontId;
    }

    public Identifier fontId() {
        return fontId;
    }
}
