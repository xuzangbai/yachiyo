package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Horizontal module card. Left click toggles the module; right click is handled
 * by ClickGuiScreen and opens the floating ModuleSettingsWindow.
 */
public final class ModuleCard {
    private final Module module;
    private final Anim hover = new Anim(0.0F, 130L, Easing.OUT_CUBIC);
    private final Anim toggleAnim;
    private float x;
    private float y;
    private float width;

    public ModuleCard(Module module) {
        this.module = module;
        this.toggleAnim = new Anim(module.enabled() ? 1.0F : 0.0F, 150L, Easing.OUT_CUBIC);
    }

    public Module module() {
        return module;
    }

    public float height() {
        return Theme.CARD_HEIGHT;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + Theme.CARD_HEIGHT;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, int accent) {
        boolean hovered = contains(mouseX, mouseY);
        if (hovered) {
            SettingDescriptionOverlay.set(module.description());
        }
        hover.set(hovered ? 1.0F : 0.0F);
        float hoverAmount = hover.value();

        int cardColor = module.enabled()
                ? Render2D.mix(Theme.CARD_ACTIVE, accent, 0.10F)
                : Render2D.mix(Theme.CARD, Theme.CARD_HOVER, hoverAmount);
        int borderColor = module.enabled() || hovered
                ? Render2D.withAlpha(accent, 0.35F + hoverAmount * 0.25F)
                : Theme.CARD_BORDER;
        Render2D.elevation(ctx, x, y, width, Theme.CARD_HEIGHT, Theme.CARD_RADIUS, Theme.SHADOW);
        Render2D.roundedBox(ctx, x, y, width, Theme.CARD_HEIGHT, Theme.CARD_RADIUS, borderColor, cardColor);
        if (hovered) {
            Render2D.roundedRect(ctx, x + 1.0F, y + 1.0F, width - 2.0F, Theme.CARD_HEIGHT - 2.0F,
                    Theme.CARD_RADIUS - 1.0F, Theme.STATE_LAYER);
        }

        if (module.enabled()) {
            Render2D.roundedRect(ctx, x + 8.0F, y + 13.0F, 3.0F, Theme.CARD_HEIGHT - 26.0F,
                    1.5F, accent);
        }

        float textX = x + 18.0F;
        Text name = Text2D.trim(module.name().copy().formatted(Formatting.BOLD), (int) width - 170);
        Text2D.draw(ctx, name, textX, y + 10.0F, module.enabled() ? accent : Theme.TEXT, false);
        Text description = Text2D.trim(module.description(), (int) width - 170);
        Text2D.draw(ctx, description, textX, y + 26.0F, Theme.TEXT_MUTED, false);

        if (!module.implemented()) {
            Text2D.drawScaled(ctx, Text.translatable("ui.yachiyoc.wip"),
                    x + width - 132.0F, y + 21.0F, 0.72F, Theme.WIP, false);
        }

        if (module.toggleable()) {
            float toggleX = x + width - 60.0F;
            float toggleY = y + (Theme.CARD_HEIGHT - 20.0F) / 2.0F;
            float toggle = toggleAnim.value();
            Render2D.roundedRect(ctx, toggleX, toggleY, 38.0F, 20.0F, 10.0F,
                    Render2D.mix(Theme.TRACK_OFF, Theme.TRACK_ON, toggle));
            float knobX = toggleX + 2.0F + toggle * 18.0F;
            Render2D.roundedRect(ctx, knobX, toggleY + 2.0F, 16.0F, 16.0F, 8.0F, Theme.KNOB);
        } else {
            Text2D.drawRight(ctx, Text.translatable("ui.yachiyoc.settings"),
                    x + width - 44.0F, y + 21.0F, Theme.TEXT_MUTED, false);
        }

        Text2D.draw(ctx, Text.literal("..."), x + width - 18.0F, y + 21.0F,
                Theme.TEXT_MUTED, false);
    }

    public boolean mouseClicked(Click click) {
        if (!contains(click.x(), click.y())) {
            return false;
        }
        if (click.button() == 0) {
            YachiyoC.toggleModuleFromGui(module);
            toggleAnim.set(module.enabled() ? 1.0F : 0.0F);
            return true;
        }
        return false;
    }
}
