package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating, draggable module settings window. Opened by right-clicking a module
 * card; all settings are shown in this window instead of the old inline dropdown.
 */
public final class ModuleSettingsWindow {
    private static final float WIDTH = 300.0F;
    private static final float HEADER = 28.0F;
    private static final float ROW = 24.0F;
    private static final float FOOTER = 36.0F;

    private final Module module;
    private final List<SettingRow> rows = new ArrayList<>();
    private float x;
    private float y;
    private float height;
    private float scroll;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean closeRequested;
    private boolean confirmReset;
    private float confirmX;
    private float confirmY;
    private float cancelX;
    private float cancelY;

    public ModuleSettingsWindow(Module module, float x, float y) {
        this.module = module;
        this.x = x;
        this.y = y;
        for (Setting<?> setting : module.settings()) {
            rows.add(new SettingRow(setting));
        }
    }

    public Module module() {
        return module;
    }

    public boolean closeRequested() {
        return closeRequested;
    }

    public void render(DrawContext context, float mouseX, float mouseY, int screenWidth, int screenHeight) {
        height = Math.min(screenHeight - 24.0F, HEADER + rows.size() * ROW + FOOTER + 10.0F);
        x = clamp(x, 6.0F, screenWidth - WIDTH - 6.0F);
        y = clamp(y, 6.0F, screenHeight - height - 6.0F);
        float contentHeight = height - HEADER - FOOTER - 4.0F;

        Render2D.roundedBox(context, x, y, WIDTH, height, 12.0F, Theme.CARD_BORDER, Theme.WINDOW);
        Text2D.draw(context, module.name(), x + 12.0F, y + 8.0F, Theme.TEXT, true);
        Text2D.draw(context, Text.literal("::"), x + WIDTH - 54.0F, y + 8.0F,
                Theme.TEXT_MUTED, false);

        float closeX = x + WIDTH - 24.0F;
        float closeY = y + 7.0F;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 15
                && mouseY >= closeY && mouseY <= closeY + 15;
        Render2D.roundedRect(context, closeX, closeY, 15.0F, 15.0F, 4.0F,
                closeHovered ? 0x44FF5A5A : Render2D.withAlpha(Theme.TEXT, 0.08F));
        for (int i = 0; i < 5; i++) {
            Render2D.fill(context, closeX + 5 + i, closeY + 5 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
            Render2D.fill(context, closeX + 9 - i, closeY + 5 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
        }

        Render2D.horizontalLine(context, x + 8.0F, y + HEADER, WIDTH - 16.0F, Theme.SEPARATOR);
        Render2D.scissor(context, x + 1.0F, y + HEADER + 2.0F, WIDTH - 2.0F, contentHeight);

        float rowY = y + HEADER + 4.0F - scroll;
        for (SettingRow row : rows) {
            if (!row.visible()) {
                continue;
            }
            row.setBounds(x + 8.0F, rowY, WIDTH - 16.0F);
            row.render(context, mouseX, mouseY, Theme.ACCENT);
            rowY += ROW;
        }
        Render2D.unscissor(context);

        float maxScroll = Math.max(0.0F, rows.size() * ROW - contentHeight);
        scroll = clamp(scroll, 0.0F, maxScroll);
        if (maxScroll > 0.0F) {
            float barHeight = Math.max(20.0F, contentHeight * contentHeight / (rows.size() * ROW));
            float barY = y + HEADER + 4.0F + scroll / maxScroll * (contentHeight - barHeight);
            Render2D.roundedRect(context, x + WIDTH - 5.0F, barY, 2.0F, barHeight, 1.0F,
                    Render2D.withAlpha(Theme.TEXT, 0.35F));
        }

        float footerY = y + height - FOOTER + 5.0F;
        boolean footerHovered = mouseX >= x + 8.0F && mouseX <= x + WIDTH - 8.0F
                && mouseY >= footerY && mouseY <= footerY + 24.0F;
        Render2D.roundedBox(context, x + 8.0F, footerY, WIDTH - 16.0F, 24.0F, 6.0F,
                Render2D.withAlpha(Theme.DANGER, footerHovered ? 0.95F : 0.70F),
                Render2D.withAlpha(Theme.DANGER, footerHovered ? 0.92F : 0.75F));
        Text2D.drawCentered(context, Text.translatable("module.yachiyoc.reset_all"),
                x + WIDTH / 2.0F, footerY + 8.0F, 0xFFFFFFFF, false);

        if (confirmReset) {
            renderConfirm(context, mouseX, mouseY, screenWidth, screenHeight);
        }
    }

    private void renderConfirm(DrawContext context, float mouseX, float mouseY,
                               int screenWidth, int screenHeight) {
        context.fill(0, 0, screenWidth, screenHeight, 0x44000000);
        float width = Math.min(330.0F, screenWidth - 30.0F);
        float height = 116.0F;
        float panelX = (screenWidth - width) / 2.0F;
        float panelY = (screenHeight - height) / 2.0F;
        Render2D.roundedBox(context, panelX, panelY, width, height, 12.0F,
                Theme.WINDOW_BORDER, Theme.WINDOW);

        Text message = Text.translatable("module.yachiyoc.reset_confirm", module.name());
        Text2D.drawCentered(context, message, panelX + width / 2.0F, panelY + 28.0F,
                Theme.TEXT, false);

        float buttonY = panelY + height - 38.0F;
        confirmX = panelX + width / 2.0F - 98.0F;
        confirmY = buttonY;
        cancelX = panelX + width / 2.0F + 8.0F;
        cancelY = buttonY;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + 90
                && mouseY >= buttonY && mouseY <= buttonY + 24;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + 90
                && mouseY >= buttonY && mouseY <= buttonY + 24;
        Render2D.roundedBox(context, confirmX, confirmY, 90.0F, 24.0F, 6.0F,
                Render2D.withAlpha(Theme.DANGER, 0.95F),
                Render2D.withAlpha(Theme.DANGER, confirmHovered ? 0.95F : 0.78F));
        Text2D.drawCentered(context, Text.translatable("module.yachiyoc.confirm"),
                confirmX + 45.0F, confirmY + 8.0F, 0xFFFFFFFF, false);
        Render2D.roundedBox(context, cancelX, cancelY, 90.0F, 24.0F, 6.0F,
                Theme.CARD_BORDER, Theme.FIELD);
        Text2D.drawCentered(context, Text.translatable("module.yachiyoc.cancel"),
                cancelX + 45.0F, cancelY + 8.0F, Theme.TEXT_DIM, false);
    }

    public boolean mouseClicked(Click click, int screenWidth, int screenHeight) {
        if (confirmReset) {
            if (click.x() >= confirmX && click.x() <= confirmX + 90
                    && click.y() >= confirmY && click.y() <= confirmY + 24) {
                resetAll();
            }
            confirmReset = false;
            return true;
        }
        float footerY = y + height - FOOTER + 5.0F;
        if (click.x() >= x + 8.0F && click.x() <= x + WIDTH - 8.0F
                && click.y() >= footerY && click.y() <= footerY + 24.0F) {
            confirmReset = true;
            return true;
        }
        float closeX = x + WIDTH - 24.0F;
        float closeY = y + 7.0F;
        if (click.x() >= closeX && click.x() <= closeX + 15
                && click.y() >= closeY && click.y() <= closeY + 15) {
            closeRequested = true;
            return true;
        }
        if (click.x() >= x && click.x() <= x + WIDTH
                && click.y() >= y && click.y() <= y + HEADER) {
            dragging = true;
            dragOffsetX = (float) click.x() - x;
            dragOffsetY = (float) click.y() - y;
            return true;
        }
        if (!inside(click.x(), click.y())) {
            return false;
        }
        for (SettingRow row : rows) {
            if (row.visible() && row.mouseClicked(click)) {
                return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (confirmReset) {
            return true;
        }
        if (dragging) {
            x = (float) click.x() - dragOffsetX;
            y = (float) click.y() - dragOffsetY;
            return true;
        }
        for (SettingRow row : rows) {
            if (row.visible() && row.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(Click click) {
        if (confirmReset) {
            return true;
        }
        boolean changed = false;
        for (SettingRow row : rows) {
            if (row.visible()) {
                row.mouseReleased(click);
                changed = true;
            }
        }
        if (dragging) {
            dragging = false;
            changed = true;
        }
        return changed;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (confirmReset) {
            return true;
        }
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        float contentHeight = height - HEADER - FOOTER - 4.0F;
        float maxScroll = Math.max(0.0F, rows.size() * ROW - contentHeight);
        scroll = clamp(scroll - (float) amount * ROW * 2.0F, 0.0F, maxScroll);
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (confirmReset) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                confirmReset = false;
            }
            return true;
        }
        for (SettingRow row : rows) {
            if (row.visible() && row.keyPressed(input)) {
                return true;
            }
        }
        return false;
    }

    private void resetAll() {
        for (Setting<?> setting : module.settings()) {
            setting.reset();
        }
        for (SettingRow row : rows) {
            row.refresh();
        }
        ColorPickerPopup.close();
        YachiyoC.config().save();
    }

    public void cancelListening() {
        for (SettingRow row : rows) {
            row.cancelListening();
        }
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
