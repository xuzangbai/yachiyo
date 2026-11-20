package dev.yachiyoc.ui.home;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.mixin.ClickableWidgetAccessor;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.UiStyle;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import dev.yachiyoc.ui.render.UiTextures;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class YachiyoHomeScreen extends Screen {
    private static final Text SHOW_CLASSIC = Text.translatable("ui.yachiyoc.home.classic");
    private static final Text SHOW_YACHIYO = Text.translatable("ui.yachiyoc.home.yachiyo");
    private static final Text CREDITS = Text.translatable("ui.yachiyoc.home.credits");
    private static final int MARGIN = 12;
    private static final int FOOTER_HEIGHT = 20;
    private static final int PANEL_PADDING = 10;
    private static final int GAP = 5;

    private final TitleScreen classic;
    private final Anim panelAppear = new Anim(0, 430, Easing.OUT_QUART);
    private final Anim titleAppear = new Anim(0, 620, Easing.OUT_QUART);
    private final long openedAt = System.nanoTime();
    private final Text version = Text.literal("Minecraft " + SharedConstants.getGameVersion().name());
    private Identifier titleTexture;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int dividerY;
    private int heroX;
    private int heroY;
    private int titleWidth;
    private boolean showVersion;

    private YachiyoHomeScreen(TitleScreen classic) {
        super(Text.literal("YACHIYO"));
        this.classic = classic;
        panelAppear.set(1);
        titleAppear.set(1);
    }

    public static Screen route(Screen screen) {
        if (YachiyoC.config() == null) return screen;
        if (YachiyoC.config().data().vanillaHomeScreen) {
            return screen instanceof YachiyoHomeScreen home ? home.classic : screen;
        }
        // Leave title subclasses owned by other mods in control of their own layout.
        if (screen != null && screen.getClass() == TitleScreen.class) {
            return new YachiyoHomeScreen((TitleScreen) screen);
        }
        return screen;
    }

    public static void registerScreenEntry() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen.getClass() != TitleScreen.class || client.currentScreen != screen) return;
            int buttonWidth = Math.min(width - 12, client.textRenderer.getWidth(SHOW_YACHIYO) + 16);
            Screens.getButtons(screen).add(ButtonWidget.builder(SHOW_YACHIYO,
                    button -> switchHome(client, screen, false)).dimensions(6, 6, buttonWidth, 20).build());
        });
    }

    public static void toggleLayout() {
        setClassicLayout(!YachiyoC.config().data().vanillaHomeScreen);
    }

    private static void setClassicLayout(boolean useClassic) {
        YachiyoC.config().data().vanillaHomeScreen = useClassic;
        YachiyoC.config().save();
    }

    private static void switchHome(MinecraftClient client, Screen classic, boolean useClassic) {
        setClassicLayout(useClassic);
        client.setScreen(classic);
    }

    @Override
    protected void init() {
        titleTexture = UiTextures.homeTitle();
        // Keep the native callbacks, including demo-world handling and multiplayer eligibility.
        classic.init(width, height);
        List<ButtonWidget> play = new ArrayList<>();
        List<ButtonWidget> utility = new ArrayList<>();
        for (var child : classic.children()) {
            if (!(child instanceof ButtonWidget button) || !button.visible) continue;
            if (utilityOrder(button) >= 0) utility.add(button);
            else play.add(button);
        }
        utility.sort(Comparator.comparingInt(YachiyoHomeScreen::utilityOrder));

        boolean wide = width >= 470 && height >= 185 && width >= height * 1.32F;
        int innerWidth = Math.max(1, width - MARGIN * 2);
        int contentWidth = Math.min(690, innerWidth);
        panelWidth = Math.min(wide ? Math.clamp(contentWidth * 39 / 100, 206, 268) : 264, innerWidth);
        panelX = wide ? (width + contentWidth) / 2 - panelWidth : (width - panelWidth) / 2;
        heroX = wide ? (width - contentWidth) / 2 + (contentWidth - panelWidth - 16) / 2 : width / 2;

        int playRows = 1 + play.size() / 2;
        int utilityRows = (utility.size() + 1) / 2;
        int preferredHeight = PANEL_PADDING * 2 + (play.isEmpty() ? 0 : 40 + (playRows - 1) * (29 + GAP))
                + (utility.isEmpty() ? 0 : 12 + utilityRows * (24 + GAP) - GAP);
        int heroSpace = wide ? 0 : Math.clamp(height / 4, 24, 118);
        int contentHeight = Math.max(1, height - FOOTER_HEIGHT - MARGIN * 2 - heroSpace);
        float density = Math.clamp((float) contentHeight / preferredHeight, 0.05F, 1);
        int padding = scaled(PANEL_PADDING, density);
        int gap = scaled(GAP, density);
        int primaryHeight = scaled(40, density);
        int playHeight = scaled(29, density);
        int utilityHeight = scaled(24, density);
        int sectionGap = scaled(12, density);
        panelHeight = padding * 2 + (play.isEmpty() ? 0 : primaryHeight + (playRows - 1) * (playHeight + gap))
                + (utility.isEmpty() ? 0 : sectionGap + utilityRows * (utilityHeight + gap) - gap);

        panelY = MARGIN + heroSpace + Math.max(0, (contentHeight - panelHeight) / 2);
        heroY = wide ? panelY + panelHeight / 2 - 5 : Math.max(1, (panelY + MARGIN - 4) / 2);
        int logoSpace = wide ? contentWidth - panelWidth - 30 : innerWidth;
        titleWidth = Math.min(Math.min(368, logoSpace), wide ? height * 2 : Math.max(1, (panelY - 10) * 3));

        int x = panelX + padding;
        int y = panelY + padding;
        int rowWidth = panelWidth - padding * 2;
        if (!play.isEmpty()) {
            addAction(play.getFirst(), x, y, rowWidth, primaryHeight, HomeButton.Style.PRIMARY);
            y += primaryHeight + gap;
            y = addRows(play.subList(1, play.size()), x, y, rowWidth, playHeight, gap, HomeButton.Style.PLAY);
            y -= gap;
        }
        dividerY = y + sectionGap / 2;
        addRows(utility, x, y + sectionGap, rowWidth, utilityHeight, gap, HomeButton.Style.UTILITY);

        int switchWidth = Math.min(innerWidth, Math.max(98, Text2D.width(SHOW_CLASSIC) + 24));
        addDrawableChild(new HomeButton(width - MARGIN - switchWidth, height - 24, switchWidth, FOOTER_HEIGHT,
                SHOW_CLASSIC, HomeButton.Style.SWITCH, panelAppear, input -> switchHome(client, classic, true)));
        showVersion = Text2D.width(version) + switchWidth + MARGIN * 3 <= width;
    }

    private static int scaled(int value, float density) {
        return Math.max(1, (int) (value * density));
    }

    private static int utilityOrder(ButtonWidget button) {
        if (button.getMessage().getContent() instanceof TranslatableTextContent translated) {
            return switch (translated.getKey()) {
                case "menu.options" -> 0;
                case "options.language" -> 1;
                case "options.accessibility" -> 2;
                case "title.credits" -> 3;
                case "menu.quit" -> 4;
                default -> -1;
            };
        }
        return -1;
    }

    private int addRows(List<ButtonWidget> buttons, int x, int y, int width, int height, int gap, HomeButton.Style style) {
        int leftWidth = (width - gap) / 2;
        for (int i = 0; i < buttons.size(); i += 2) {
            boolean paired = i + 1 < buttons.size();
            addAction(buttons.get(i), x, y, paired ? leftWidth : width, height, style);
            if (paired) addAction(buttons.get(i + 1), x + leftWidth + gap, y, width - leftWidth - gap, height, style);
            y += height + gap;
        }
        return y;
    }

    private void addAction(ButtonWidget original, int x, int y, int width, int height, HomeButton.Style style) {
        Text label = original.getMessage().getContent() instanceof TranslatableTextContent translated
                && translated.getKey().equals("title.credits") ? CREDITS : original.getMessage();
        HomeButton button = new HomeButton(x, y, width, height, label, style, panelAppear, original::onPress);
        button.active = original.active;
        var tooltip = ((ClickableWidgetAccessor) original).yachiyoc$tooltipState().getTooltip();
        if (tooltip != null) button.setTooltip(tooltip);
        addDrawableChild(button);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        long elapsed = UiStyle.animationsEnabled() ? System.nanoTime() - openedAt : 0;
        Render2D.homeFlow(context, width, height, heroX / (float) Math.max(1, width), elapsed);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float titleProgress = titleAppear.value();
        Render2D.pushOpacity(titleProgress);
        if (titleTexture == null) {
            Text2D.drawCentered(context, getTitle(), heroX, heroY - 4, Theme.SURFACE_HIGH, true);
        } else {
            float titleHeight = titleWidth / 3.0F;
            Render2D.texture(context, titleTexture, heroX - titleWidth / 2.0F,
                    heroY - titleHeight / 2 + (1 - titleProgress) * 9, titleWidth, titleHeight, 2172, 724);
        }
        Render2D.popOpacity();

        Render2D.pushOpacity(panelAppear.value());
        Render2D.elevation(context, panelX, panelY, panelWidth, panelHeight, 16, Theme.SHADOW);
        Render2D.roundedBox(context, panelX, panelY, panelWidth, panelHeight, 16,
                Theme.HOME_PANEL_BORDER, Theme.HOME_PANEL);
        Render2D.horizontalLine(context, panelX + 12, dividerY, panelWidth - 24, Theme.HOME_PANEL_BORDER);
        if (showVersion) Text2D.draw(context, version, MARGIN, height - 17, Theme.OUTLINE, true);
        Render2D.popOpacity();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
