package dev.yachiyoc.ui.home;

import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;

final class HomeButton extends ClickableWidget {
    enum Style { PRIMARY, PLAY, UTILITY, SWITCH }

    private final Style style;
    private final Anim appear;
    private final Anim hover = new Anim(0);
    private final Consumer<AbstractInput> action;
    private final Text label;
    private final int labelWidth;
    private final int labelHeight;

    HomeButton(int x, int y, int width, int height, Text message, Style style, Anim appear,
               Consumer<AbstractInput> action) {
        super(x, y, width, height, message);
        this.style = style;
        this.appear = appear;
        this.action = action;
        label = Text2D.trim(message, Math.max(1, width - (style == Style.PRIMARY ? 44 : 12)));
        if (label != message) setTooltip(Tooltip.of(message));
        labelWidth = Text2D.width(label);
        labelHeight = Text2D.height();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        hover.set(active && (isHovered() || isFocused()) ? 1 : 0);
        float emphasis = hover.value();
        boolean primary = style == Style.PRIMARY;
        int x = getX();
        int y = getY();
        int base = switch (style) {
            case PRIMARY -> Theme.PRIMARY_CONTAINER;
            case PLAY -> Theme.HOME_BUTTON;
            case UTILITY -> Render2D.withAlpha(Theme.HOME_BUTTON, 0.60F);
            case SWITCH -> Theme.HOME_PANEL;
        };
        int highlight = primary ? Theme.SECONDARY_CONTAINER : Theme.HOME_BUTTON_HOVER;
        int outline = Render2D.mix(Theme.HOME_BUTTON_BORDER, Theme.OUTLINE_STRONG, emphasis);
        int textColor = !active ? Theme.TEXT_MUTED : primary ? Theme.TEXT : Theme.SURFACE_HIGH;
        int radius = switch (style) {
            case PRIMARY, SWITCH -> 10;
            case PLAY, UTILITY -> 7;
        };
        if (isFocused() && active) outline = Theme.OUTLINE;
        Render2D.pushOpacity(appear.value());
        Render2D.roundedBox(context, x, y, width, height, radius, outline, Render2D.mix(base, highlight, emphasis));
        if (height >= labelHeight + 1) {
            float labelX = primary ? x + 14 + emphasis * 2 : x + (width - labelWidth) / 2.0F;
            float labelY = y + (height - labelHeight) / 2.0F;
            Text2D.draw(context, label, labelX, labelY, textColor, false);
            if (primary && width >= 48) drawArrow(context, x + width - 17 + emphasis * 2, y + height / 2, Theme.ACCENT);
        }
        Render2D.popOpacity();
    }

    private static void drawArrow(DrawContext context, float x, int y, int color) {
        Render2D.fill(context, x, y, 6, 1, color);
        Render2D.fill(context, x + 4, y - 1, 1, 3, color);
        Render2D.fill(context, x + 3, y - 2, 1, 1, color);
        Render2D.fill(context, x + 3, y + 2, 1, 1, color);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        action.accept(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!active || !visible || !input.isEnterOrSpace()) return false;
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        action.accept(input);
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
