package dev.yachiyoc.ui;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.platform.RegistryCatalog;
import dev.yachiyoc.platform.RegistryKind;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import dev.yachiyoc.ui.widget.SearchField;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Meteor-style registry selector: available entries on the left, selected on
 * the right, search on top. Items, blocks and entity types all use this screen.
 */
public final class RegistrySelectionScreen extends Screen {
    private static final float ROW_HEIGHT = 20.0F;

    private final RegistryListSetting setting;
    private final Screen parent;
    private final RegistryKind kind;
    private final List<RegistryCatalog.Entry> all;
    private final SearchField search = new SearchField(0, 0, 1, 1);

    private float windowX;
    private float windowY;
    private float windowWidth;
    private float windowHeight;
    private float leftX;
    private float leftY;
    private float leftWidth;
    private float leftHeight;
    private float rightX;
    private float rightY;
    private float rightWidth;
    private float rightHeight;
    private float leftScroll;
    private float rightScroll;
    private float closeX;
    private float closeY;
    private static final float CLOSE_SIZE = 18.0F;

    public RegistrySelectionScreen(RegistryListSetting setting, Screen parent) {
        super(Text.translatable(setting.kind().titleKey()));
        this.setting = setting;
        this.parent = parent;
        this.kind = setting.kind();
        this.all = RegistryCatalog.entries(kind);
        search.setPlaceholder(Text.translatable("selector.yachiyoc.search"));
        search.focus();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout();

        Render2D.roundedBox(context, windowX, windowY, windowWidth, windowHeight,
                14.0F, Theme.WINDOW_BORDER, 0xF2141922);
        Text2D.draw(context, title, windowX + 18.0F, windowY + 14.0F, Theme.TEXT, true);
        Text2D.draw(context, Text.translatable("selector.yachiyoc.hint"),
                windowX + 18.0F, windowY + 28.0F, Theme.TEXT_MUTED, false);

        closeX = windowX + windowWidth - 28.0F;
        closeY = windowY + 12.0F;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE
                && mouseY >= closeY && mouseY <= closeY + CLOSE_SIZE;
        Render2D.roundedRect(context, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 6.0F,
                closeHovered ? 0x44FF5A5A : 0x18FFFFFF);
        for (int i = 0; i < 6; i++) {
            Render2D.fill(context, closeX + 6 + i, closeY + 6 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
            Render2D.fill(context, closeX + 11 - i, closeY + 6 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
        }

        search.render(context, mouseX, mouseY);
        drawPanel(context, true, mouseX, mouseY);
        drawPanel(context, false, mouseX, mouseY);
    }

    private void layout() {
        windowWidth = Math.min(width - 32.0F, 720.0F);
        windowHeight = Math.min(height - 32.0F, 430.0F);
        windowX = (width - windowWidth) / 2.0F;
        windowY = (height - windowHeight) / 2.0F;

        search.setBounds(windowX + 18.0F, windowY + 46.0F, windowWidth - 36.0F, 20.0F);

        float panelTop = windowY + 76.0F;
        float panelBottom = windowY + windowHeight - 14.0F;
        float gap = 12.0F;
        float panelWidth = (windowWidth - 36.0F - gap) / 2.0F;
        leftX = windowX + 18.0F;
        leftY = panelTop;
        leftWidth = panelWidth;
        leftHeight = panelBottom - panelTop;
        rightX = leftX + panelWidth + gap;
        rightY = panelTop;
        rightWidth = panelWidth;
        rightHeight = leftHeight;

        List<RegistryCatalog.Entry> left = available();
        List<RegistryCatalog.Entry> right = selected();
        leftScroll = clamp(leftScroll, 0.0F, maxScroll(left));
        rightScroll = clamp(rightScroll, 0.0F, maxScroll(right));
    }

    private void drawPanel(DrawContext context, boolean left, int mouseX, int mouseY) {
        float x = left ? leftX : rightX;
        float y = left ? leftY : rightY;
        float w = left ? leftWidth : rightWidth;
        float h = left ? leftHeight : rightHeight;
        List<RegistryCatalog.Entry> entries = left ? available() : selected();
        float scroll = left ? leftScroll : rightScroll;

        Render2D.roundedRect(context, x, y, w, h, 10.0F, Theme.CONTENT);
        Text2D.draw(context, Text.translatable(left ? "selector.yachiyoc.available"
                        : "selector.yachiyoc.selected", entries.size()),
                x + 10.0F, y + 8.0F, left ? Theme.TEXT_MUTED : Theme.ACCENT, false);

        float listTop = y + 24.0F;
        float listHeight = h - 30.0F;
        Render2D.scissor(context, x + 2.0F, listTop, w - 4.0F, listHeight);

        if (entries.isEmpty()) {
            Text2D.drawCentered(context, Text.translatable(left ? "selector.yachiyoc.empty_available"
                            : "selector.yachiyoc.empty_selected"),
                    x + w / 2.0F, listTop + 30.0F, Theme.TEXT_MUTED, false);
        } else {
            int start = Math.max(0, (int) (scroll / ROW_HEIGHT));
            for (int i = start; i < entries.size(); i++) {
                float rowY = listTop + 2.0F + i * ROW_HEIGHT - scroll;
                if (rowY > listTop + listHeight) {
                    break;
                }
                drawRow(context, entries.get(i), x, rowY, w, left, mouseX, mouseY);
            }
        }
        Render2D.unscissor(context);
    }

    private void drawRow(DrawContext context, RegistryCatalog.Entry entry, float x, float rowY,
                         float width, boolean left, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x + 4 && mouseX <= x + width - 4
                && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1;
        if (hovered) {
            Render2D.roundedRect(context, x + 4.0F, rowY, width - 8.0F, ROW_HEIGHT - 1.0F,
                    4.0F, Render2D.withAlpha(Theme.ACCENT, 0.14F));
        }

        float iconX = x + 8.0F;
        if (!entry.stack().isEmpty()) {
            context.drawItem(entry.stack(), (int) iconX, (int) rowY + 2);
        } else {
            Render2D.roundedRect(context, iconX + 2.0F, rowY + 5.0F, 8.0F, 8.0F, 2.0F,
                    Theme.TEXT_MUTED);
        }

        Text name = Text2D.trim(Text.literal(entry.name()), (int) width - 54);
        Text2D.draw(context, name, x + 28.0F, rowY + 6.0F, Theme.TEXT_DIM, false);
        Text action = Text.literal(left ? "+" : "-");
        Text2D.drawRight(context, action, x + width - 12.0F, rowY + 6.0F,
                left ? Theme.ACCENT : 0xFFFF7A7A, false);
    }

    private List<RegistryCatalog.Entry> available() {
        String query = search.query().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(entry -> !setting.contains(entry.id()))
                .filter(entry -> matches(entry, query))
                .toList();
    }

    private List<RegistryCatalog.Entry> selected() {
        String query = search.query().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(entry -> setting.contains(entry.id()))
                .filter(entry -> matches(entry, query))
                .toList();
    }

    private boolean matches(RegistryCatalog.Entry entry, String query) {
        return query.isBlank()
                || entry.name().toLowerCase(Locale.ROOT).contains(query)
                || entry.id().toLowerCase(Locale.ROOT).contains(query);
    }

    private float maxScroll(List<RegistryCatalog.Entry> entries) {
        return Math.max(0.0F, entries.size() * ROW_HEIGHT - (leftHeight - 36.0F));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        layout();
        if (click.x() >= closeX && click.x() <= closeX + CLOSE_SIZE
                && click.y() >= closeY && click.y() <= closeY + CLOSE_SIZE) {
            close();
            return true;
        }
        if (search.mouseClicked(click)) {
            return true;
        }
        search.unfocus();

        if (clickInPanel(click, true)) {
            int index = rowIndex(click, true);
            List<RegistryCatalog.Entry> entries = available();
            if (index >= 0 && index < entries.size()) {
                setting.toggle(entries.get(index).id());
                save();
                return true;
            }
        }
        if (clickInPanel(click, false)) {
            int index = rowIndex(click, false);
            List<RegistryCatalog.Entry> entries = selected();
            if (index >= 0 && index < entries.size()) {
                setting.remove(entries.get(index).id());
                save();
                return true;
            }
        }
        return false;
    }

    private boolean clickInPanel(Click click, boolean left) {
        float x = left ? leftX : rightX;
        float y = left ? leftY : rightY;
        float w = left ? leftWidth : rightWidth;
        float h = left ? leftHeight : rightHeight;
        return click.x() >= x && click.x() <= x + w && click.y() >= y + 24 && click.y() <= y + h;
    }

    private int rowIndex(Click click, boolean left) {
        float y = left ? leftY : rightY;
        float scroll = left ? leftScroll : rightScroll;
        return (int) ((click.y() - (y + 26.0F) + scroll) / ROW_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float amount = (float) verticalAmount * ROW_HEIGHT * 2.0F;
        if (mouseX >= leftX && mouseX <= leftX + leftWidth
                && mouseY >= leftY && mouseY <= leftY + leftHeight) {
            leftScroll = clamp(leftScroll - amount, 0.0F, maxScroll(available()));
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + rightWidth
                && mouseY >= rightY && mouseY <= rightY + rightHeight) {
            rightScroll = clamp(rightScroll - amount, 0.0F, maxScroll(selected()));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return search.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (search.charTyped(input)) {
            leftScroll = 0.0F;
            rightScroll = 0.0F;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void save() {
        YachiyoC.config().save();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
