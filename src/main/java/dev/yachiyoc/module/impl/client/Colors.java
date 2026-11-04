package dev.yachiyoc.module.impl.client;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.ui.Theme;

/**
 * Client appearance colors. Every setting writes straight into Theme, so the
 * whole UI updates immediately and the values are saved with the module config.
 */
public final class Colors extends Module {
    public final ColorSetting window =
            add(new ColorSetting("client.colors", "window", Theme.WINDOW, v -> Theme.WINDOW = v));
    public final ColorSetting sidebar =
            add(new ColorSetting("client.colors", "sidebar", Theme.SIDEBAR, v -> Theme.SIDEBAR = v));
    public final ColorSetting content =
            add(new ColorSetting("client.colors", "content", Theme.CONTENT, v -> Theme.CONTENT = v));
    public final ColorSetting card =
            add(new ColorSetting("client.colors", "card", Theme.CARD, v -> Theme.CARD = v));
    public final ColorSetting cardHover =
            add(new ColorSetting("client.colors", "card_hover", Theme.CARD_HOVER, v -> Theme.CARD_HOVER = v));
    public final ColorSetting cardActive =
            add(new ColorSetting("client.colors", "card_active", Theme.CARD_ACTIVE, v -> Theme.CARD_ACTIVE = v));
    public final ColorSetting text =
            add(new ColorSetting("client.colors", "text", Theme.TEXT, v -> Theme.TEXT = v));
    public final ColorSetting textDim =
            add(new ColorSetting("client.colors", "text_dim", Theme.TEXT_DIM, v -> Theme.TEXT_DIM = v));
    public final ColorSetting textMuted =
            add(new ColorSetting("client.colors", "text_muted", Theme.TEXT_MUTED, v -> Theme.TEXT_MUTED = v));
    public final ColorSetting accent =
            add(new ColorSetting("client.colors", "accent", Theme.ACCENT, v -> Theme.ACCENT = v));
    public final ColorSetting windowBorder =
            add(new ColorSetting("client.colors", "window_border", Theme.WINDOW_BORDER, v -> Theme.WINDOW_BORDER = v));
    public final ColorSetting cardBorder =
            add(new ColorSetting("client.colors", "card_border", Theme.CARD_BORDER, v -> Theme.CARD_BORDER = v));
    public final ColorSetting trackOn =
            add(new ColorSetting("client.colors", "track_on", Theme.TRACK_ON, v -> Theme.TRACK_ON = v));
    public final ColorSetting wip =
            add(new ColorSetting("client.colors", "wip", Theme.WIP, v -> Theme.WIP = v));

    public Colors() {
        super("colors", ModuleCategory.CLIENT, true, false);
    }
}
