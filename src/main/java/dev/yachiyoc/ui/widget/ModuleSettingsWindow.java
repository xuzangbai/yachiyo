package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.module.setting.SoundSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Draggable inspector with semantic groups and a cross-group settings search. */
public final class ModuleSettingsWindow {
    private static final float ROW = 30, NAV = 94;
    private static final Map<String, float[]> POSITIONS = new LinkedHashMap<>();
    private final Module module;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final Map<String, Anim> hovers = new LinkedHashMap<>();
    private final SearchField search = new SearchField(0, 0, 100, 24);
    private final Anim open = new Anim(0, 190L, Easing.OUT_CUBIC);
    private final Anim page = new Anim(1, 180L, Easing.OUT_CUBIC);
    private final Anim scroll = new Anim(0, 160L, Easing.OUT_CUBIC);
    private final Anim indicator = new Anim(0, 180L, Easing.OUT_CUBIC);
    private final Anim navigationScroll = new Anim(0, 150L, Easing.OUT_CUBIC);
    private String selected, lastQuery = "";
    private float x, y, width, height, contentX, contentY, contentWidth, contentHeight, totalHeight, offset;
    private float scale = 1, dragOffsetX, dragOffsetY;
    private boolean dragging, draggingScroll, closing, closeRequested, confirmReset;
    private int screenWidth, screenHeight;

    private static final class Entry {
        final SettingRow row;
        final String group;
        final Anim reveal = new Anim(0, 180L, Easing.OUT_CUBIC);
        float y, amount;
        boolean matches;

        Entry(Module module, Setting<?> setting, String group) {
            row = new SettingRow(module, setting);
            this.group = group;
        }
    }

    public ModuleSettingsWindow(Module module, float x, float y) {
        this.module = module;
        this.x = x;
        this.y = y;
        search.setPlaceholder(Text.translatable("ui.yachiyoc.search_settings"));
        for (Setting<?> setting : module.settings()) {
            String group = setting == module.bind || setting == module.trigger ? "keybinds" : setting.group();
            entries.add(new Entry(module, setting, group));
            if (!group.equals("keybinds") && !groups.contains(group)) groups.add(group);
        }
        if (module.toggleable()) groups.add("keybinds");
        if (groups.isEmpty()) groups.add("general");
        selected = groups.getFirst();
        for (String group : groups) hovers.put(group, new Anim(0, 120L, Easing.OUT_CUBIC));
        snapEntries();
        float[] saved = POSITIONS.get(module.id());
        if (saved != null) { this.x = saved[0]; this.y = saved[1]; }
        open.set(1);
    }

    public Module module() { return module; }
    public boolean closing() { return closing; }
    public boolean closeRequested() { return closeRequested; }
    public boolean finishedClosing() { return closing && open.value() <= 0.002F; }

    public void beginClose() {
        if (closing) return;
        closeEditors();
        dragging = draggingScroll = false;
        closing = true;
        open.set(0, 140L, Easing.OUT_CUBIC);
    }

    private void snapEntries() {
        for (Entry entry : entries) entry.reveal.snap(entry.group.equals(selected) && entry.row.visible() ? 1 : 0);
    }

    private void layout(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        width = Math.min(424, screenWidth - 12.0F);
        height = Math.min(386, screenHeight - 12.0F);
        x = clamp(x, 6, screenWidth - width - 6);
        y = clamp(y, 6, screenHeight - height - 6);
        contentX = x + NAV + 18;
        contentY = y + 76;
        contentWidth = width - NAV - 30;
        contentHeight = Math.max(1, height - 116);
        search.setBounds(x + 12, y + 40, width - 24, 24);
        String query = search.query().strip().toLowerCase(Locale.ROOT);
        if (!query.equals(lastQuery)) { scroll.snap(0); lastQuery = query; }
        totalHeight = 0;
        for (Entry entry : entries) {
            Setting<?> setting = entry.row.setting();
            entry.matches = entry.row.visible() && (query.isEmpty() ? entry.group.equals(selected)
                    : setting.id().contains(query) || setting.label().getString().toLowerCase(Locale.ROOT).contains(query)
                    || groupLabel(entry.group).getString().toLowerCase(Locale.ROOT).contains(query));
            entry.reveal.set(entry.matches ? 1 : 0);
            entry.amount = entry.reveal.value();
            entry.y = totalHeight;
            totalHeight += ROW * entry.amount;
        }
        float maxScroll = Math.max(0, totalHeight - contentHeight);
        scroll.set(clamp(scroll.target(), 0, maxScroll));
        offset = clamp(scroll.value(), 0, maxScroll);
        float slide = (1 - page.value()) * 9;
        for (Entry entry : entries) {
            entry.row.setBounds(contentX + slide, contentY + entry.y - offset, contentWidth - 7);
        }
        indicator.set(groups.indexOf(selected) * 26.0F);
        navigationScroll.set(clamp(navigationScroll.target(), 0, Math.max(0, groups.size() * 26 - contentHeight)));
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        scale = 0.96F + open.value() * 0.04F;
        float mx = localX(mouseX), my = localY(mouseY);
        Render2D.pushScale(ctx, x + width / 2, y + height / 2, scale);
        Render2D.pushOpacity(open.value());
        Render2D.elevation(ctx, x, y, width, height, 18, Theme.SHADOW);
        Render2D.floatingSurface(ctx, x, y, width, height, 18, Theme.OUTLINE_STRONG, Theme.WINDOW, mx, my);
        Text2D.draw(ctx, Text2D.trim(module.name(), (int) width - 72), x + 14, y + 15, Theme.TEXT, false);
        boolean closeHover = contains(mx, my, x + width - 32, y + 7, 24, 24);
        Render2D.roundedRect(ctx, x + width - 32, y + 7, 24, 24, 8,
                Render2D.withAlpha(Theme.DANGER, closeHover ? 0.20F : 0.06F));
        Text2D.drawCentered(ctx, Text.literal("×"), x + width - 20, y + 14, Theme.TEXT_DIM, false);
        search.render(ctx, mx, my);
        Render2D.verticalLine(ctx, x + NAV + 8, contentY, contentHeight, Theme.SEPARATOR);
        Render2D.scissor(ctx, x + 8, contentY, NAV - 4, contentHeight);
        if (search.query().isBlank()) {
            Render2D.roundedRect(ctx, x + 10, contentY + indicator.value() - navigationScroll.value(), NAV - 8, 24, 8,
                    Render2D.withAlpha(Theme.ACCENT, 0.13F));
        }
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            float gy = contentY + i * 26 - navigationScroll.value();
            boolean hovered = contains(mx, my, x + 10, gy, NAV - 8, 24);
            Anim hover = hovers.get(group);
            hover.set(hovered ? 1 : 0);
            Render2D.roundedRect(ctx, x + 10, gy, NAV - 8, 24, 8,
                    Render2D.withAlpha(Theme.ACCENT, hover.value() * 0.08F));
            Text2D.draw(ctx, Text2D.trim(groupLabel(group), (int) NAV - 20), x + 18, gy + 8,
                    group.equals(selected) && search.query().isBlank() ? Theme.ACCENT : Theme.TEXT_DIM, false);
        }
        Render2D.unscissor(ctx);
        Render2D.scissor(ctx, contentX, contentY, contentWidth, contentHeight);
        boolean any = false;
        for (Entry entry : entries) {
            if (entry.amount <= 0.01F) continue;
            float ry = contentY + entry.y - offset;
            if (ry + ROW < contentY || ry > contentY + contentHeight) continue;
            any |= entry.matches;
            Render2D.scissor(ctx, contentX, ry, contentWidth, ROW * entry.amount);
            boolean overContent = contains(mx, my, contentX, contentY, contentWidth, contentHeight);
            Render2D.pushOpacity(entry.amount * page.value());
            entry.row.render(ctx, overContent ? mx : -1000, overContent ? my : -1000, Theme.ACCENT);
            Render2D.popOpacity();
            Render2D.unscissor(ctx);
        }
        if (!any && totalHeight < 1) {
            Text2D.drawCentered(ctx, Text.translatable("ui.yachiyoc.no_settings"),
                    contentX + contentWidth / 2, contentY + 30, Theme.TEXT_MUTED, false);
        }
        Render2D.unscissor(ctx);
        if (totalHeight > contentHeight) {
            float barHeight = Math.max(24, contentHeight * contentHeight / totalHeight);
            float barY = contentY + offset / (totalHeight - contentHeight) * (contentHeight - barHeight);
            Render2D.roundedRect(ctx, x + width - 9, barY, 3, barHeight, 1.5F,
                    Render2D.withAlpha(Theme.ACCENT, draggingScroll ? 0.8F : 0.4F));
        }
        Render2D.horizontalLine(ctx, x + 12, y + height - 37, width - 24, Theme.SEPARATOR);
        Text2D.draw(ctx, Text2D.trim(Text.translatable("ui.yachiyoc.settings_hint"), (int) width - 110),
                x + 14, y + height - 22, Theme.TEXT_MUTED, false);
        boolean resetHover = contains(mx, my, x + width - 88, y + height - 31, 76, 24);
        Render2D.roundedRect(ctx, x + width - 88, y + height - 31, 76, 24, 7,
                Render2D.withAlpha(Theme.DANGER, resetHover ? 0.17F : 0.06F));
        Text2D.drawCentered(ctx, Text.translatable("ui.yachiyoc.reset_module"),
                x + width - 50, y + height - 23, Theme.DANGER, false);
        Render2D.popOpacity();
        Render2D.popScale(ctx);
        if (confirmReset) renderConfirm(ctx, mouseX, mouseY);
    }

    private void renderConfirm(DrawContext ctx, float mouseX, float mouseY) {
        Render2D.fill(ctx, 0, 0, screenWidth, screenHeight, 0x55000000);
        float w = Math.min(330, screenWidth - 20), cx = (screenWidth - w) / 2, cy = (screenHeight - 116) / 2.0F;
        Render2D.floatingSurface(ctx, cx, cy, w, 116, 16, Theme.OUTLINE, Theme.WINDOW, mouseX, mouseY);
        Text2D.drawCentered(ctx, Text2D.trim(Text.translatable("module.yachiyoc.reset_confirm", module.name()), (int) w - 20),
                cx + w / 2, cy + 27, Theme.TEXT, false);
        for (int i = 0; i < 2; i++) {
            float bx = screenWidth / 2.0F - 98 + i * 106;
            Render2D.roundedRect(ctx, bx, cy + 78, 90, 24, 8,
                    i == 0 ? Render2D.withAlpha(Theme.DANGER, 0.18F) : Theme.FIELD);
            Text2D.drawCentered(ctx, Text.translatable(i == 0 ? "module.yachiyoc.confirm" : "module.yachiyoc.cancel"),
                    bx + 45, cy + 86, i == 0 ? Theme.DANGER : Theme.TEXT_DIM, false);
        }
    }

    public boolean mouseClicked(Click original, int screenWidth, int screenHeight) {
        if (closing) return inside(localX(original.x()), localY(original.y()));
        if (confirmReset) {
            if (original.button() == 0 && contains(original.x(), original.y(), screenWidth / 2.0F - 98,
                    (screenHeight - 116) / 2.0F + 78, 90, 24)) resetAll();
            confirmReset = false;
            return true;
        }
        Click click = new Click(localX(original.x()), localY(original.y()), original.buttonInfo());
        if (!inside(click.x(), click.y())) { search.unfocus(); return false; }
        if (click.button() == 0 && contains(click.x(), click.y(), x + width - 32, y + 7, 24, 24)) {
            closeRequested = true;
            return true;
        }
        if (click.button() == 0 && click.y() < y + 35) {
            closeEditors();
            dragging = true;
            dragOffsetX = (float) original.x() - x;
            dragOffsetY = (float) original.y() - y;
            return true;
        }
        if (search.mouseClicked(click)) { closeEditors(); return true; }
        search.unfocus();
        if (click.button() == 0 && contains(click.x(), click.y(), x + width - 88, y + height - 31, 76, 24)) {
            closeEditors();
            confirmReset = true;
            return true;
        }
        if (click.button() == 0 && contains(click.x(), click.y(), x + 10, contentY, NAV - 8, contentHeight)) {
            int index = (int) ((click.y() - contentY + navigationScroll.value()) / 26);
            if (index < groups.size()) {
                closeEditors();
                search.clear();
                selected = groups.get(index);
                scroll.snap(0);
                page.snap(0);
                page.set(1);
                snapEntries();
            }
            return true;
        }
        if (click.button() == 0 && totalHeight > contentHeight
                && contains(click.x(), click.y(), x + width - 14, contentY, 12, contentHeight)) {
            draggingScroll = true;
            updateScroll((float) click.y());
            return true;
        }
        if (contains(click.x(), click.y(), contentX, contentY, contentWidth - 7, contentHeight)) {
            for (Entry entry : entries) {
                if (entry.matches && entry.amount > 0.98F && entry.row.mouseClicked(click)) return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (closing || confirmReset) return false;
        if (dragging) {
            x = clamp((float) click.x() - dragOffsetX, 6, screenWidth - width - 6);
            y = clamp((float) click.y() - dragOffsetY, 6, screenHeight - height - 6);
            return true;
        }
        if (draggingScroll) { updateScroll(localY(click.y())); return true; }
        Click local = new Click(localX(click.x()), localY(click.y()), click.buttonInfo());
        for (Entry entry : entries) {
            if (entry.matches && entry.row.mouseDragged(local, deltaX, deltaY)) return true;
        }
        return false;
    }

    private void updateScroll(float mouseY) {
        float bar = Math.max(24, contentHeight * contentHeight / totalHeight);
        scroll.snap(clamp((mouseY - contentY - bar / 2) / Math.max(1, contentHeight - bar), 0, 1)
                * Math.max(0, totalHeight - contentHeight));
    }

    public boolean mouseReleased(Click click) {
        boolean handled = dragging || draggingScroll || inside(localX(click.x()), localY(click.y()));
        if (dragging) POSITIONS.put(module.id(), new float[] { x, y });
        dragging = draggingScroll = false;
        for (Entry entry : entries) entry.row.mouseReleased(click);
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!inside(localX(mouseX), localY(mouseY))) return false;
        if (contains(localX(mouseX), localY(mouseY), x + 8, contentY, NAV, contentHeight)) {
            navigationScroll.set(clamp(navigationScroll.target() - (float) amount * 32,
                    0, Math.max(0, groups.size() * 26 - contentHeight)));
            return true;
        }
        if (!closing && !confirmReset) scroll.set(clamp(scroll.target() - (float) amount * ROW * 1.5F,
                0, Math.max(0, totalHeight - contentHeight)));
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (closing) return true;
        if (confirmReset) { if (input.key() == GLFW.GLFW_KEY_ESCAPE) confirmReset = false; return true; }
        if (search.focused()) return search.keyPressed(input);
        for (Entry entry : entries) if (entry.matches && entry.row.keyPressed(input)) return true;
        return false;
    }

    public boolean charTyped(CharInput input) { return search.charTyped(input); }

    public boolean blocksPointer(double mouseX, double mouseY) {
        return confirmReset || inside(localX(mouseX), localY(mouseY));
    }

    private void resetAll() {
        for (Entry entry : entries) { entry.row.setting().reset(); entry.row.refresh(); }
        for (String id : new ArrayList<>(module.settingBinds().keySet())) module.setBindById(id, -1);
        YachiyoC.saveAll();
    }

    private void closeEditors() {
        cancelListening();
        ColorPickerPopup.close();
        EnumDropdownPopup.close();
        FontDropdownPopup.close();
        SoundSetting.closeOpen();
        DoubleSetting.stopAllEditing();
        StringSetting.stopAllEditing();
    }

    public void cancelListening() {
        for (Entry entry : entries) { entry.row.cancelListening(); entry.row.mouseReleased(null); }
    }

    private static Text groupLabel(String group) { return Text.translatable("ui.yachiyoc.group." + group); }
    private float localX(double px) { return x + width / 2 + ((float) px - x - width / 2) / scale; }
    private float localY(double py) { return y + height / 2 + ((float) py - y - height / 2) / scale; }
    private boolean inside(double px, double py) { return contains(px, py, x, y, width, height); }
    private static boolean contains(double px, double py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
