package dev.yachiyoc.ui.widget;

import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Screen-top description line for settings. Any SettingRow can call set() while
 * hovered; the screen renders it once, so the same behavior is reusable by the
 * module settings window, HUD settings and future panels.
 */
public final class SettingDescriptionOverlay {
    private static Text description;

    private SettingDescriptionOverlay() {
    }

    public static void beginFrame() {
        description = null;
    }

    public static void set(Text text) {
        description = text;
    }

    public static void render(DrawContext context, int screenWidth) {
        if (description == null) {
            return;
        }
        Text shown = Text2D.trim(description, screenWidth - 36);
        float width = Math.min(screenWidth - 16.0F, Text2D.width(shown) + 24.0F);
        Render2D.roundedBox(context, 8.0F, 6.0F, width, 22.0F, 6.0F,
                Theme.CARD_BORDER, Render2D.withAlpha(Theme.WINDOW, 0.96F));
        Text2D.draw(context, shown, 16.0F, 11.0F, Theme.TEXT_DIM, false);
    }
}
