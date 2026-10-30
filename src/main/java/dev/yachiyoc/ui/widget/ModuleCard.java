package dev.yachiyoc.ui.widget;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal module card used by the center column: name/description on the
 * left, WIP badge + toggle + expand chevron on the right, settings grid below.
 */
public final class ModuleCard {
    private final Module module;
    private final List<SettingRow> settingRows = new ArrayList<>();
    private final Anim hover = new Anim(0.0F, 130L, Easing.OUT_CUBIC);
    private final Anim expand = new Anim(0.0F, 190L, Easing.OUT_CUBIC);
    private final Anim toggleAnim;
    private float x;
    private float y;
    private float width;
    private boolean expanded;

    public ModuleCard(Module module) {
        this.module = module;
        this.toggleAnim = new Anim(module.enabled() ? 1.0F : 0.0F, 150L, Easing.OUT_CUBIC);
        for (Setting<?> setting : module.settings()) {
            settingRows.add(new SettingRow(setting));
        }
    }

    public Module module() {
        return module;
    }

    public float height() {
        return Theme.CARD_HEIGHT + expand.value() * settingsHeight();
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, int accent) {
        boolean hovered = contains(mouseX, mouseY);
        hover.set(hovered ? 1.0F : 0.0F);
        float hoverAmount = hover.value();
        float panelHeight = height();

        int cardColor = module.enabled()
                ? Render2D.mix(Theme.CARD_ACTIVE, accent, 0.10F)
                : Render2D.mix(Theme.CARD, Theme.CARD_HOVER, hoverAmount);
        int borderColor = module.enabled() || hovered
                ? Render2D.withAlpha(accent, 0.35F + hoverAmount * 0.25F)
                : Theme.CARD_BORDER;
        Render2D.roundedBox(ctx, x, y, width, panelHeight, Theme.CARD_RADIUS, borderColor, cardColor);

        if (module.enabled()) {
            Render2D.roundedRect(ctx, x + 8.0F, y + 13.0F, 3.0F, Theme.CARD_HEIGHT - 26.0F,
                    1.5F, accent);
        }

        float textX = x + 18.0F;
        Text name = Text2D.trim(module.name(), (int) width - 170);
        Text2D.draw(ctx, name, textX, y + 10.0F, module.enabled() ? accent : Theme.TEXT, false);
        Text description = Text2D.trim(module.description(), (int) width - 170);
        Text2D.draw(ctx, description, textX, y + 26.0F, Theme.TEXT_MUTED, false);

        if (!module.implemented()) {
            Text2D.drawScaled(ctx, Text.translatable("ui.yachiyoc.wip"),
                    x + width - 132.0F, y + 21.0F, 0.72F, Theme.WIP, false);
        }

        float toggleX = x + width - 54.0F;
        float toggleY = y + (Theme.CARD_HEIGHT - 16.0F) / 2.0F;
        float toggle = toggleAnim.value();
        Render2D.roundedRect(ctx, toggleX, toggleY, 32.0F, 16.0F, 8.0F,
                Render2D.mix(Theme.TRACK_OFF, Theme.TRACK_ON, toggle));
        float knobX = toggleX + 2.0F + toggle * 16.0F;
        Render2D.roundedRect(ctx, knobX, toggleY + 2.0F, 12.0F, 12.0F, 6.0F, Theme.KNOB);

        Text chevron = Text.literal(expanded ? "v" : ">");
        Text2D.draw(ctx, chevron, x + width - 16.0F, y + 21.0F, Theme.TEXT_MUTED, false);

        if (expanded || expand.value() > 0.01F) {
            float settingsHeight = settingsHeight();
            float clip = expand.value() * settingsHeight;
            Render2D.horizontalLine(ctx, x + 12.0F, y + Theme.CARD_HEIGHT,
                    width - 24.0F, 0x1AFFFFFF);
            Render2D.scissor(ctx, x + 1.0F, y + Theme.CARD_HEIGHT, width - 2.0F, clip);

            float cellWidth = (width - 38.0F) / 2.0F;
            float rowY = y + Theme.CARD_HEIGHT + 8.0F;
            int column = 0;
            for (SettingRow row : settingRows) {
                if (!row.visible()) {
                    continue;
                }
                float cellX = x + 14.0F + column * (cellWidth + 10.0F);
                row.setBounds(cellX, rowY, cellWidth);
                row.render(ctx, mouseX, mouseY, accent);
                column++;
                if (column == 2) {
                    column = 0;
                    rowY += Theme.SETTING_HEIGHT;
                }
            }
            Render2D.unscissor(ctx);
        }
    }

    public boolean mouseClicked(Click click) {
        if (!contains(click.x(), click.y())) {
            return false;
        }
        if (click.y() <= y + Theme.CARD_HEIGHT) {
            if (expandArea(click.x())) {
                expanded = !expanded;
                expand.set(expanded ? 1.0F : 0.0F);
            } else if (click.button() == 0) {
                module.toggle();
                toggleAnim.set(module.enabled() ? 1.0F : 0.0F);
            } else if (click.button() == 1) {
                expanded = !expanded;
                expand.set(expanded ? 1.0F : 0.0F);
            }
            return true;
        }
        if (expanded) {
            for (SettingRow row : settingRows) {
                if (row.visible() && row.mouseClicked(click)) {
                    return true;
                }
            }
        }
        return true;
    }

    public void cancelListening() {
        for (SettingRow row : settingRows) {
            row.cancelListening();
        }
    }

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        for (SettingRow row : settingRows) {
            if (row.visible() && row.keyPressed(input)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (expanded) {
            for (SettingRow row : settingRows) {
                if (row.mouseDragged(click, deltaX, deltaY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void mouseReleased(Click click) {
        for (SettingRow row : settingRows) {
            row.mouseReleased(click);
        }
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return contains(mouseX, mouseY);
    }

    private boolean expandArea(double mouseX) {
        return mouseX >= x + width - 24.0F;
    }

    private float settingsHeight() {
        int visible = 0;
        for (SettingRow row : settingRows) {
            if (row.visible()) {
                visible++;
            }
        }
        int rows = (visible + 1) / 2;
        return rows * Theme.SETTING_HEIGHT + 12.0F;
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height();
    }
}
