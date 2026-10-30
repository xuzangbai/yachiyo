package dev.yachiyoc.ui.widget;

import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A non-category rail action placed directly after the Render tab. It opens the
 * HUD editor instead of switching a module category.
 */
public final class HudRailButton {
    private final Anim hover = new Anim(0.0F, 120L, Easing.OUT_CUBIC);
    private float x;
    private float y;
    private float width;
    private float height;

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        hover.set(hovered ? 1.0F : 0.0F);
        int accent = 0xFFBA68C8;
        Render2D.roundedRect(ctx, x, y, width, height, Theme.SMALL_RADIUS,
                Render2D.withAlpha(accent, 0.10F + hover.value() * 0.12F));
        Render2D.roundedRect(ctx, x, y + 5.0F, 3.0F, height - 10.0F, 1.5F, accent);
        Text2D.draw(ctx, Text.translatable("hud.yachiyoc.editor.short"), x + 14.0F,
                y + (height - Text2D.height()) / 2.0F + 1.0F, Theme.TEXT_DIM, false);
        Text2D.draw(ctx, Text.literal(">"), x + width - 10.0F,
                y + (height - Text2D.height()) / 2.0F + 1.0F, Theme.TEXT_MUTED, false);
    }

    public boolean mouseClicked(Click click) {
        return contains(click.x(), click.y());
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
