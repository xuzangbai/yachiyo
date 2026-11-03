package dev.yachiyoc.ui.widget;

import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class CategoryTab {
    private final ModuleCategory category;
    private final int count;
    private final Anim hover = new Anim(0.0F, 120L, Easing.OUT_CUBIC);
    private final Anim activeAnim = new Anim(0.0F, 180L, Easing.OUT_CUBIC);
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean active;

    public CategoryTab(ModuleCategory category, int count) {
        this.category = category;
        this.count = count;
    }

    public ModuleCategory category() {
        return category;
    }

    public float y() {
        return y;
    }

    public float height() {
        return height;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            activeAnim.set(active ? 1.0F : 0.0F);
        }
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, int accent) {
        boolean hovered = contains(mouseX, mouseY);
        hover.set(hovered ? 1.0F : 0.0F);
        float activeAmount = activeAnim.value();

        int base = hovered ? Theme.STATE_LAYER_STRONG : Theme.STATE_LAYER;
        Render2D.roundedRect(ctx, x, y, width, height, Theme.SMALL_RADIUS, base);
        if (activeAmount > 0.01F) {
            Render2D.roundedRect(ctx, x, y, width, height, Theme.SMALL_RADIUS,
                    Render2D.withAlpha(Theme.PRIMARY_CONTAINER, 0.90F * activeAmount));
            Render2D.roundedRect(ctx, x + 5.0F, y + 6.0F, 3.0F, height - 12.0F,
                    1.5F, Render2D.withAlpha(accent, 0.95F * activeAmount));
        }

        int textColor = activeAmount > 0.5F ? Theme.TEXT : Theme.TEXT_DIM;
        Text2D.draw(ctx, category.label(), x + 15.0F, y + (height - Text2D.height()) / 2.0F + 1.0F,
                textColor, false);
        Text2D.drawRight(ctx, Text.literal(String.valueOf(count)), x + width - 11.0F,
                y + (height - Text2D.height()) / 2.0F + 1.0F,
                activeAmount > 0.5F ? accent : Theme.TEXT_MUTED, false);
    }

    public boolean mouseClicked(Click click) {
        return contains(click.x(), click.y());
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
