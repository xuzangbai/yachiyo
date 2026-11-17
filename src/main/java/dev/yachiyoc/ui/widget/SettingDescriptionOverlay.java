package dev.yachiyoc.ui.widget;

import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
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
    private static Text lastDescription;
    private static final Anim visibility = new Anim(0, 130L, Easing.OUT_CUBIC);

    private SettingDescriptionOverlay() {
    }

    public static void beginFrame() {
        description = null;
    }

    public static void set(Text text) {
        description = text;
    }

    public static void render(DrawContext context, int screenWidth) {
        visibility.set(description == null ? 0 : 1);
        if (description != null) lastDescription = description;
        float amount = visibility.value();
        if (lastDescription == null || amount < 0.002F) {
            return;
        }
        Text shown = Text2D.trim(lastDescription, screenWidth - 36);
        float width = Math.min(screenWidth - 16.0F, Text2D.width(shown) + 24.0F);
        Render2D.pushOpacity(amount);
        Render2D.floatingSurface(context, 8.0F, 6.0F, width, 22.0F, 6.0F,
                Theme.CARD_BORDER, Theme.WINDOW, -1000, -1000);
        Text2D.draw(context, shown, 16.0F, 11.0F, Theme.TEXT_DIM, false);
        Render2D.popOpacity();
    }
}
