package dev.yachiyoc.ui;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.hud.HudEditorScreen;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.SoundSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.platform.Keybinds;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.UiTextures;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import dev.yachiyoc.ui.widget.CategoryTab;
import dev.yachiyoc.ui.widget.HudRailButton;
import dev.yachiyoc.ui.widget.ModuleCard;
import dev.yachiyoc.ui.widget.SearchField;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickGUI: rounded window, logo top-left, vertical category rail on the right,
 * horizontal module cards in the center.
 */
public final class ClickGuiScreen extends Screen {
    private final List<CategoryTab> tabs = new ArrayList<>();
    private final Map<ModuleCategory, List<ModuleCard>> cardsByCategory = new LinkedHashMap<>();
    private final List<ModuleCard> visibleCards = new ArrayList<>();
    private final HudRailButton hudRail = new HudRailButton();
    private final Anim open = new Anim(0.0F, 300L, Easing.OUT_QUINT);
    private final Anim switchAnim = new Anim(1.0F, 260L, Easing.OUT_QUINT);
    private final Anim railY = new Anim(0.0F, 200L, Easing.OUT_QUINT);
    private final Anim railHeight = new Anim(0.0F, 200L, Easing.OUT_QUINT);
    private final Anim closeHover = new Anim(0.0F, 120L, Easing.OUT_CUBIC);

    private SearchField search;
    private ModuleCategory selected = ModuleCategory.COMBAT;
    private float contentScroll;
    private float sidebarScroll;

    private float windowX;
    private float windowY;
    private float windowWidth;
    private float windowHeight;
    private float contentX;
    private float contentY;
    private float contentWidth;
    private float contentHeight;
    private float cardsY;
    private float cardsHeight;
    private float sidebarX;
    private float sidebarY;
    private float sidebarWidth;
    private float sidebarHeight;

    public ClickGuiScreen() {
        super(Text.literal("YachiyoC"));
    }

    @Override
    protected void init() {
        tabs.clear();
        cardsByCategory.clear();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<Module> modules = YachiyoC.modules().byCategory(category);
            tabs.add(new CategoryTab(category, modules.size()));
            List<ModuleCard> cards = new ArrayList<>();
            for (Module module : modules) {
                cards.add(new ModuleCard(module));
            }
            cardsByCategory.put(category, cards);
        }
        search = new SearchField(0.0F, 0.0F, 100.0F, Theme.SEARCH_HEIGHT);
        open.snap(0.0F);
        open.set(1.0F);
        switchAnim.snap(1.0F);
        selectCategory(selected, false);
        relayout();
    }

    private void selectCategory(ModuleCategory category, boolean animate) {
        this.selected = category;
        this.contentScroll = 0.0F;
        visibleCards.clear();
        visibleCards.addAll(cardsByCategory.getOrDefault(category, List.of()));
        for (CategoryTab tab : tabs) {
            tab.setActive(tab.category() == category);
        }
        if (animate) {
            switchAnim.snap(0.0F);
            switchAnim.set(1.0F);
        }
    }

    private void relayout() {
        float openOffset = (1.0F - open.value()) * 24.0F;
        windowWidth = Math.min(width - 32.0F, 760.0F);
        windowHeight = Math.min(height - 32.0F, 440.0F);
        windowX = (width - windowWidth) / 2.0F;
        windowY = (height - windowHeight) / 2.0F + openOffset;

        float padding = Theme.PADDING;
        float headerHeight = Theme.HEADER_HEIGHT;
        float footerHeight = Theme.FOOTER_HEIGHT;
        contentX = windowX + padding;
        contentY = windowY + headerHeight;
        float contentBottom = windowY + windowHeight - footerHeight - 8.0F;
        contentHeight = contentBottom - contentY;

        sidebarWidth = Theme.SIDEBAR_WIDTH;
        sidebarX = windowX + windowWidth - padding - sidebarWidth;
        sidebarY = contentY;
        sidebarHeight = contentHeight;
        contentWidth = sidebarX - contentX - Theme.GAP;

        cardsY = contentY + 38.0F;
        cardsHeight = contentBottom - cardsY;

        search.setBounds(contentX + contentWidth - 176.0F, contentY + 4.0F, 172.0F, Theme.SEARCH_HEIGHT);

        float tabTop = sidebarY + 24.0F;
        int railItems = tabs.size() + 1;
        float tabTotal = railItems * (Theme.TAB_HEIGHT + Theme.TAB_GAP) - Theme.TAB_GAP;
        float tabMaxScroll = Math.min(0.0F, (sidebarY + sidebarHeight - 6.0F) - (tabTop + tabTotal));
        sidebarScroll = Math.min(0.0F, Math.max(tabMaxScroll, sidebarScroll));

        float tabY = tabTop + sidebarScroll;
        float tabX = sidebarX + 8.0F;
        float tabWidth = sidebarWidth - 16.0F;
        for (CategoryTab tab : tabs) {
            tab.setBounds(tabX, tabY, tabWidth, Theme.TAB_HEIGHT);
            if (tab.category() == selected) {
                railY.set(tabY);
                railHeight.set(Theme.TAB_HEIGHT);
            }
            tabY += Theme.TAB_HEIGHT + Theme.TAB_GAP;
            if (tab.category() == ModuleCategory.RENDER) {
                hudRail.setBounds(tabX, tabY, tabWidth, Theme.TAB_HEIGHT);
                tabY += Theme.TAB_HEIGHT + Theme.TAB_GAP;
            }
        }

        List<ModuleCard> filtered = visibleCards.stream().filter(card -> search.matches(card.module())).toList();
        float total = 0.0F;
        for (ModuleCard card : filtered) {
            total += card.height() + Theme.CARD_GAP;
        }
        if (total > 0.0F) {
            total -= Theme.CARD_GAP;
        }
        float maxScroll = Math.min(0.0F, cardsHeight - total);
        contentScroll = Math.min(0.0F, Math.max(maxScroll, contentScroll));

        float cardY = cardsY + contentScroll;
        for (ModuleCard card : filtered) {
            card.setBounds(contentX, cardY, contentWidth);
            cardY += card.height() + Theme.CARD_GAP;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float amount = open.value();
        ctx.fillGradient(0, 0, width, height,
                Render2D.withAlpha(Theme.OVERLAY_TOP, amount),
                Render2D.withAlpha(Theme.OVERLAY_BOTTOM, amount));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        relayout();
        List<ModuleCard> filtered = visibleCards.stream().filter(card -> search.matches(card.module())).toList();

        // window
        Render2D.roundedRect(ctx, windowX + 2.0F, windowY + 5.0F, windowWidth, windowHeight,
                Theme.WINDOW_RADIUS, 0x66000000);
        Render2D.roundedBox(ctx, windowX, windowY, windowWidth, windowHeight,
                Theme.WINDOW_RADIUS, Theme.WINDOW_BORDER, Theme.WINDOW);

        // header logo
        float logoHeight = 36.0F;
        float logoWidth = 117.0F;
        Identifier logo = UiTextures.logo();
        if (logo != null) {
            Render2D.texture(ctx, logo, windowX + Theme.PADDING, windowY + 16.0F,
                    logoWidth, logoHeight, 720, 221);
        }
        Text2D.draw(ctx, Text.literal("v" + YachiyoC.version()),
                windowX + Theme.PADDING + logoWidth + 10.0F, windowY + 30.0F,
                Theme.TEXT_MUTED, false);
        Render2D.horizontalLine(ctx, windowX + Theme.PADDING, windowY + Theme.HEADER_HEIGHT - 1.0F,
                windowWidth - Theme.PADDING * 2.0F, 0x1AFFFFFF);

        float closeSize = 22.0F;
        float closeX = windowX + windowWidth - Theme.PADDING - closeSize;
        float closeY = windowY + 18.0F;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeSize
                && mouseY >= closeY && mouseY <= closeY + closeSize;
        closeHover.set(closeHovered ? 1.0F : 0.0F);
        Render2D.roundedRect(ctx, closeX, closeY, closeSize, closeSize, 7.0F,
                Render2D.withAlpha(0xFFFF5A5A, 0.10F + closeHover.value() * 0.18F));
        int crossColor = closeHovered ? 0xFFFF9A9A : Theme.TEXT_MUTED;
        int cx = (int) closeX + 7;
        int cy = (int) closeY + 7;
        for (int i = 0; i < 8; i++) {
            Render2D.fill(ctx, cx + i, cy + i, 1.0F, 1.0F, crossColor);
            Render2D.fill(ctx, cx + 7 - i, cy + i, 1.0F, 1.0F, crossColor);
        }

        // content background
        Render2D.roundedRect(ctx, contentX - 6.0F, contentY - 4.0F,
                contentWidth + 12.0F, contentHeight + 8.0F, Theme.PANEL_RADIUS, Theme.CONTENT);

        Text categoryName = selected.label();
        Text2D.draw(ctx, categoryName, contentX + 2.0F, contentY + 10.0F, Theme.TEXT, true);
        Text2D.draw(ctx, Text.literal(" / " + visibleCards.size()),
                contentX + 8.0F + Text2D.width(categoryName), contentY + 10.0F,
                Theme.TEXT_MUTED, false);
        Render2D.horizontalLine(ctx, contentX + 2.0F, contentY + 30.0F,
                Math.min(60.0F, contentWidth - 190.0F), Theme.ACCENT);
        search.render(ctx, mouseX, mouseY);

        // module cards
        float switchOffset = (1.0F - switchAnim.value()) * 16.0F;
        Render2D.scissor(ctx, contentX - 4.0F, cardsY - 2.0F, contentWidth + 8.0F, cardsHeight + 4.0F);
        if (filtered.isEmpty()) {
            Text2D.drawCentered(ctx, Text.translatable("ui.yachiyoc.no_results"),
                    contentX + contentWidth / 2.0F, cardsY + 40.0F, Theme.TEXT_MUTED, false);
        } else {
            float cardY = cardsY + contentScroll;
            for (ModuleCard card : filtered) {
                // shift, then restore, so scissor stays in screen space
                card.setBounds(contentX + switchOffset, cardY, contentWidth);
                card.render(ctx, mouseX, mouseY, selected.accent());
                cardY += card.height() + Theme.CARD_GAP;
            }
        }
        Render2D.unscissor(ctx);

        // right category rail
        Render2D.roundedRect(ctx, sidebarX, sidebarY, sidebarWidth, sidebarHeight,
                Theme.PANEL_RADIUS, Theme.SIDEBAR);
        Text2D.draw(ctx, Text.translatable("ui.yachiyoc.categories"),
                sidebarX + 12.0F, sidebarY + 8.0F, Theme.TEXT_MUTED, false);
        Render2D.scissor(ctx, sidebarX + 2.0F, sidebarY + 20.0F,
                sidebarWidth - 4.0F, sidebarHeight - 24.0F);
        Render2D.roundedRect(ctx, sidebarX + 5.0F, railY.value(), 3.0F, railHeight.value(),
                1.5F, selected.accent());
        for (CategoryTab tab : tabs) {
            tab.render(ctx, mouseX, mouseY, tab.category().accent());
        }
        hudRail.render(ctx, mouseX, mouseY);
        Render2D.unscissor(ctx);

        // footer
        Render2D.horizontalLine(ctx, windowX + Theme.PADDING, windowY + windowHeight - 24.0F,
                windowWidth - Theme.PADDING * 2.0F, 0x1AFFFFFF);
        Text openText = Text.translatable("ui.yachiyoc.open",
                Keybinds.label(), YachiyoC.prefix() + "clickgui").formatted(Formatting.DARK_GRAY);
        Text2D.draw(ctx, openText, windowX + Theme.PADDING, windowY + windowHeight - 17.0F,
                Theme.TEXT_MUTED, false);
        Text count = Text.translatable("ui.yachiyoc.modules", YachiyoC.modules().count())
                .formatted(Formatting.DARK_GRAY);
        Text2D.drawRight(ctx, count, windowX + windowWidth - Theme.PADDING,
                windowY + windowHeight - 17.0F, Theme.TEXT_MUTED, false);
        SoundSetting.renderOpen(ctx, mouseX, mouseY, width, height);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (SoundSetting.mouseClickedOpen(click, width, height)) {
            return true;
        }
        StringSetting.stopAllEditing();
        float closeSize = 22.0F;
        float closeX = windowX + windowWidth - Theme.PADDING - closeSize;
        float closeY = windowY + 18.0F;
        if (click.x() >= closeX && click.x() <= closeX + closeSize
                && click.y() >= closeY && click.y() <= closeY + closeSize) {
            close();
            return true;
        }
        for (List<ModuleCard> cards : cardsByCategory.values()) {
            for (ModuleCard card : cards) {
                card.cancelListening();
            }
        }
        if (search.mouseClicked(click)) {
            return true;
        }
        search.unfocus();

        if (click.x() >= sidebarX && click.x() <= sidebarX + sidebarWidth
                && click.y() >= sidebarY && click.y() <= sidebarY + sidebarHeight) {
            for (CategoryTab tab : tabs) {
                if (tab.mouseClicked(click)) {
                    selectCategory(tab.category(), true);
                    return true;
                }
            }
            if (hudRail.mouseClicked(click)) {
                YachiyoC.config().save();
                this.client.setScreen(new HudEditorScreen());
                return true;
            }
        }

        if (click.x() >= contentX && click.x() <= contentX + contentWidth
                && click.y() >= cardsY && click.y() <= cardsY + cardsHeight) {
            for (int i = visibleCards.size() - 1; i >= 0; i--) {
                ModuleCard card = visibleCards.get(i);
                if (search.matches(card.module()) && card.mouseClicked(click)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        for (ModuleCard card : visibleCards) {
            if (card.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (ModuleCard card : visibleCards) {
            card.mouseReleased(click);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
                && mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight) {
            sidebarScroll -= (float) verticalAmount * 20.0F;
            relayout();
            return true;
        }
        if (mouseX >= contentX && mouseX <= contentX + contentWidth
                && mouseY >= cardsY && mouseY <= cardsY + cardsHeight) {
            contentScroll -= (float) verticalAmount * 26.0F;
            relayout();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        StringSetting editing = StringSetting.active();
        if (editing != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) {
                editing.stopEditing();
            } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                editing.backspace();
            }
            return true;
        }
        if (search.focused() && search.keyPressed(input)) {
            return true;
        }
        for (ModuleCard card : visibleCards) {
            if (card.keyPressed(input)) {
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == Keybinds.code()) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        StringSetting editing = StringSetting.active();
        if (editing != null) {
            editing.append((char) input.codepoint());
            return true;
        }
        return search.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        SoundSetting.closeOpen();
        StringSetting.stopAllEditing();
        saveLayout();
        super.removed();
    }

    @Override
    public void close() {
        saveLayout();
        super.close();
    }

    private void saveLayout() {
        // Layout is fully responsive now; only module state/settings need saving.
        YachiyoC.config().save();
    }
}

