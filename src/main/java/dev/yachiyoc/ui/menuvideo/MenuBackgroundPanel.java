package dev.yachiyoc.ui.menuvideo;

import dev.yachiyoc.YachiyoC;

import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Render2D.MenuScene;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A disclosure tab and its drawer, attached to the current lobby screen. */
public final class MenuBackgroundPanel extends ClickableWidget {
    private static final String KEY = "ui.yachiyoc.menu_background.";
    private static final int ROW_HEIGHT = 28;
    private static final long LEAVE_DELAY = 120_000_000L;
    private final int screenWidth, screenHeight;
    private final Anim reveal = new Anim(0);
    private final List<ClickableWidget> controls = new ArrayList<>();
    private VolumeSlider draggedVolume;
    private List<MenuVideoLibrary.Entry> library;
    private List<OrderedText> folderHint = List.of();
    private int panelX, panelY, panelWidth, panelHeight, rows, scroll;
    private int keyboardIndex = -1;
    private boolean keyboardOpen, dismissed, videoTab;
    private long lastInside;
    private Text error;

    private MenuBackgroundPanel(int width, int height) {
        super(width - 12, height / 2 - 13, 12, 26, Text.translatable(KEY + "title"));
        screenWidth = width;
        screenHeight = height;
    }

    static void install(Screen screen, int width, int height) {
        var panel = new MenuBackgroundPanel(width, height);
        Screens.getButtons(screen).add(panel);
        ScreenEvents.afterRender(screen).register((current, context, x, y, delta) -> panel.renderDrawer(context, x, y, delta));
        ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> !panel.handleClick(click));
        ScreenMouseEvents.allowMouseDrag(screen).register((current, click, x, y) -> !panel.handleVolumeDrag(click, x, y));
        ScreenMouseEvents.allowMouseRelease(screen).register((current, click) -> !panel.handleVolumeRelease(click));
        ScreenMouseEvents.allowMouseScroll(screen).register((current, x, y, horizontal, vertical) -> !panel.handleScroll(x, y, vertical));
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> !panel.handleDrawerKeys(input));
        ScreenEvents.remove(screen).register(current -> panel.commitVolume());
    }

    public static boolean acceptFileDrop(Screen screen, List<Path> paths) {
        for (var child : screen.children()) {
            if (child instanceof MenuBackgroundPanel panel && panel.reveal.target() > 0 && MenuBackground.videosSupported()) {
                panel.dropFiles(paths);
                return true;
            }
        }
        return false;
    }

    private void layout() {
        var renderer = MinecraftClient.getInstance().textRenderer;
        panelWidth = Math.min(Math.min(250, screenWidth - 28), Math.max(184, screenWidth * 3 / 7));
        panelX = getX() - panelWidth - 4;
        boolean showVolume = videoTab && library.stream().anyMatch(entry -> entry.audio() != null);
        folderHint = videoTab ? renderer.wrapLines(Text.translatable(KEY + "folder_hint"), panelWidth - 24) : List.of();
        int fixedHeight = 130 + folderHint.size() * (renderer.fontHeight + 2) + (showVolume ? 22 : 0);
        int availableRows = Math.clamp((screenHeight - fixedHeight - 12) / ROW_HEIGHT, 1, 8);
        rows = Math.min(library.size(), availableRows);
        MenuScene[] scenes = MenuScene.values();
        int sceneRows = (scenes.length + 1) / 2;
        panelHeight = videoTab ? fixedHeight + rows * ROW_HEIGHT : 106 + 24 * sceneRows;
        panelY = Math.max(6, (screenHeight - panelHeight) / 2);
        scroll = Math.clamp(scroll, 0, Math.max(0, library.size() - rows));
        controls.clear();
        int x = panelX + 12;
        int contentWidth = panelWidth - 24;
        int half = (contentWidth - 6) / 2;
        button(x, panelY + 45, half, 19, Text.translatable(KEY + "procedural"), null,
                () -> !videoTab, () -> showTab(false));
        button(x + half + 6, panelY + 45, contentWidth - half - 6, 19, Text.translatable(KEY + "videos"), null,
                () -> videoTab, () -> showTab(true)).active = MenuBackground.videosSupported();

        int firstRow = panelY + 72;
        if (videoTab) {
            for (int i = 0; i < rows; i++) {
                var entry = library.get(scroll + i);
                String file = entry.clips().getLast().getFileName().toString();
                Text detail = entry.id().equals(entry.clips().getLast().toString()) ? Text.translatable(KEY + "local_video") : Text.literal(file);
                button(x, firstRow + ROW_HEIGHT * i, contentWidth, 24, Text.literal(entry.name()), detail,
                        () -> MenuBackground.isSelected(entry), () -> select(entry));
            }
            int footer = firstRow + rows * ROW_HEIGHT + 3;
            if (showVolume) {
                controls.add(new VolumeSlider(x, footer, contentWidth));
                footer += 22;
            }
            button(x, footer, half, 18, Text.translatable(KEY + "folder"), null, () -> false, this::openFolder);
            button(x + half + 6, footer, contentWidth - half - 6, 18, Text.translatable(KEY + "refresh"),
                    null, () -> false, this::refresh);
            button(x, footer + 22, contentWidth - 60, 18, Text.translatable(KEY + "choose"), null,
                    () -> false, this::chooseVideo);
            button(x + contentWidth - 54, footer + 22, 54, 18, Text.translatable("gui.done"), null, () -> false, this::dismiss);
        } else {
            for (int i = 0; i < scenes.length; i++) {
                MenuScene scene = scenes[i];
                int column = i % 2;
                var tile = button(x + column * (half + 6), firstRow + (i / 2) * 24, half, 20,
                        Text.translatable(KEY + "scene." + scene.id), null,
                        () -> MenuBackground.isSelected(null) && MenuBackground.scene() == scene,
                        () -> { error = null; MenuBackground.selectScene(scene); });
                tile.swatch = scene.color;
            }
            button(x + contentWidth - 54, firstRow + sceneRows * 24 + 4, 54, 18,
                    Text.translatable("gui.done"), null, () -> false, this::dismiss);
        }
        if (keyboardOpen) {
            if (keyboardIndex >= 0 && keyboardIndex < controls.size() && controls.get(keyboardIndex).active) {
                focusControl(keyboardIndex);
            } else {
                moveFocus(1);
            }
        }
    }

    private Control button(int x, int y, int width, int height, Text label, Text detail,
                           BooleanSupplier selected, Runnable action) {
        var control = new Control(x, y, width, height, label, detail, selected, action);
        controls.add(control);
        return control;
    }

    private void select(MenuVideoLibrary.Entry entry) {
        error = null;
        MenuBackground.select(entry);
    }

    private void showTab(boolean videos) {
        if (videoTab == videos) return;
        error = null;
        videoTab = videos;
        keyboardIndex = videos ? 1 : 0;
        layout();
    }

    private void refresh() {
        error = null;
        library = MenuBackground.videos();
        layout();
    }

    private void openFolder() {
        try {
            Util.getOperatingSystem().open(Files.createDirectories(MenuVideoLibrary.folder()));
        } catch (IOException | SecurityException e) {
            error = Text.translatable(KEY + "folder_failed");
        }
    }

    private void chooseVideo() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(MenuVideoLibrary.EXTENSIONS.size());
            for (String extension : MenuVideoLibrary.EXTENSIONS) patterns.put(stack.UTF8("*." + extension));
            patterns.flip();
            Path folder = Files.createDirectories(MenuVideoLibrary.folder());
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(Text.translatable(KEY + "choose").getString(),
                    folder.toString() + java.io.File.separator, patterns, "Video", false);
            if (chosen != null) dropFiles(List.of(Path.of(chosen)));
        } catch (IOException | InvalidPathException | LinkageError e) {
            error = Text.translatable(KEY + "picker_failed");
        }
    }

    private void dropFiles(List<Path> paths) {
        var file = paths.stream().filter(MenuVideoLibrary::isVideo).findFirst();
        if (file.isPresent()) {
            videoTab = true;
            select(MenuVideoLibrary.Entry.file(file.get()));
            refresh();
        } else {
            error = Text.translatable(KEY + "drop_invalid");
        }
    }

    private boolean containsDrawer(double x, double y, int shift) {
        return library != null && x >= panelX + shift && x < screenWidth && y >= panelY && y < panelY + panelHeight;
    }

    private void open() {
        if (library == null) {
            videoTab = !MenuBackground.isSelected(null);
            library = MenuBackground.videos();
            layout();
        }
        lastInside = System.nanoTime();
        reveal.set(1);
    }

    private void dismiss() {
        commitVolume();
        keyboardOpen = false;
        dismissed = true;
        focusControl(-1);
        reveal.set(0);
    }

    private int drawerShift() {
        return Math.round(16 * (1 - reveal.value()));
    }

    private boolean handleClick(Click click) {
        if (isMouseOver(click.x(), click.y())) return false;
        int shift = drawerShift();
        if (reveal.value() <= 0.02f || !containsDrawer(click.x(), click.y(), shift)) {
            if (keyboardOpen) dismiss();
            return false;
        }
        if (reveal.target() == 1) {
            var shifted = new Click(click.x() - shift, click.y(), click.buttonInfo());
            for (ClickableWidget control : List.copyOf(controls)) {
                if (control.mouseClicked(shifted, false)) {
                    if (control instanceof VolumeSlider slider) draggedVolume = slider;
                    break;
                }
            }
        }
        return true;
    }

    private boolean handleVolumeDrag(Click click, double x, double y) {
        if (draggedVolume == null) return false;
        draggedVolume.mouseDragged(new Click(click.x() - drawerShift(), click.y(), click.buttonInfo()), x, y);
        return true;
    }

    private boolean handleVolumeRelease(Click click) {
        if (draggedVolume == null || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        draggedVolume.onRelease(click);
        commitVolume();
        return true;
    }

    private void commitVolume() {
        if (draggedVolume == null) return;
        draggedVolume = null;
        YachiyoC.config().save();
    }

    private boolean handleScroll(double x, double y, double amount) {
        if (draggedVolume != null) return true;
        if (reveal.value() <= 0.02f || !containsDrawer(x, y, drawerShift())) return false;
        if (videoTab && reveal.target() == 1 && amount != 0 && library.size() > rows) {
            scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, library.size() - rows);
            layout();
        }
        return true;
    }

    private void focusControl(int index) {
        keyboardIndex = index;
        for (int i = 0; i < controls.size(); i++) controls.get(i).setFocused(i == index);
    }

    private void moveFocus(int direction) {
        int index = keyboardIndex;
        for (int i = 0; i < controls.size(); i++) {
            index = Math.floorMod(index + direction, controls.size());
            if (controls.get(index).active) {
                focusControl(index);
                return;
            }
        }
    }

    private boolean handleDrawerKeys(KeyInput input) {
        if (reveal.target() == 0) return false;
        if (input.isEscape()) {
            dismiss();
            return true;
        }
        if (!keyboardOpen) return false;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            moveFocus(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_TAB && input.hasShift() ? -1 : 1);
            return true;
        }
        if (keyboardIndex < 0) return false;
        var control = controls.get(keyboardIndex);
        float before = MenuBackground.companionVolume();
        boolean handled = control.keyPressed(input);
        if (control instanceof VolumeSlider && before != MenuBackground.companionVolume()) YachiyoC.config().save();
        return handled;
    }

    @Override public void onClick(Click click, boolean doubled) {
        dismissed = false;
        open();
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (!input.isEnterOrSpace()) return false;
        keyboardOpen = true;
        dismissed = false;
        open();
        if (keyboardIndex < 0) moveFocus(1);
        return true;
    }

    @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean highlighted = isSelected() || reveal.target() > 0;
        Render2D.roundedBox(context, getX(), getY(), width + 5, height, 5,
                highlighted ? 0xA68EABC9 : 0x53758BA2, highlighted ? 0xB9263448 : 0x63202B3B);
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(renderer, reveal.target() > 0 ? "›" : "‹",
                getX() + width / 2, getY() + (height - renderer.fontHeight) / 2,
                highlighted ? 0xFFF1F7FF : 0xB8EBF3FF);
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    private void renderDrawer(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean onTab = isMouseOver(mouseX, mouseY);
        boolean inside = containsDrawer(mouseX, mouseY, drawerShift());
        if (!onTab) dismissed = false;
        if (draggedVolume != null || !dismissed && (onTab || reveal.target() > 0 && inside)) open();
        else if (!keyboardOpen && System.nanoTime() - lastInside > LEAVE_DELAY) reveal.set(0);

        float opacity = reveal.value();
        if (opacity < 0.02f) return;
        int shift = drawerShift();
        context.createNewRootLayer();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(shift, 0);
        Render2D.pushOpacity(opacity);
        try {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            Render2D.elevation(context, panelX, panelY, panelWidth, panelHeight, 12, 0x60000000);
            Render2D.floatingSurface(context, panelX, panelY, panelWidth, panelHeight, 12,
                    0x80627492, 0xE9151D2B, mouseX - shift, mouseY);
            int x = panelX + 12;
            context.drawTextWithShadow(textRenderer, getMessage(), x, panelY + 12, Render2D.applyOpacity(0xFFF5F8FE));
            Text status = error == null ? MenuBackground.status() : error;
            context.drawTextWithShadow(textRenderer, fit(status, panelWidth - 24), x, panelY + 29,
                    Render2D.applyOpacity(error == null ? 0xFFB9CBE5 : 0xFFFFCAB8));
            if (videoTab && library.size() > rows) {
                float ratio = (float) rows / library.size();
                int barHeight = Math.max(7, Math.round(rows * ROW_HEIGHT * ratio));
                int start = panelY + 72 + Math.round((rows * ROW_HEIGHT - barHeight)
                        * (float) scroll / (library.size() - rows));
                Render2D.roundedRect(context, panelX + panelWidth - 8, start, 2, barHeight, 1, 0xFF7799C8);
            }
            for (ClickableWidget control : controls) control.render(context, mouseX - shift, mouseY, delta);
            int hintY = panelY + panelHeight - 7 - folderHint.size() * (textRenderer.fontHeight + 2);
            for (OrderedText line : folderHint) {
                context.drawTextWithShadow(textRenderer, line, x, hintY, Render2D.applyOpacity(0xFFB9CBE5));
                hintY += textRenderer.fontHeight + 2;
            }
        } finally {
            Render2D.popOpacity();
            context.getMatrices().popMatrix();
        }
    }

    static String fit(Text text, int width) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        String label = text.getString();
        return renderer.getWidth(label) <= width ? label : renderer.trimToWidth(label, Math.max(0, width - 8)) + "…";
    }

    private static final class VolumeSlider extends SliderWidget {
        VolumeSlider(int x, int y, int width) {
            super(x, y, width, 18, Text.empty(), MenuBackground.companionVolume());
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Text.translatable(KEY + "volume", Math.round(value * 100) + "%"));
        }

        @Override protected void applyValue() {
            YachiyoC.config().data().menuBackgroundVolume = (float) value;
        }

        @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int edge = isSelected() ? 0xFF88B3F0 : 0x65495D7F;
            Render2D.roundedBox(context, getX(), getY(), width, height, 6, edge, 0xCB212D42);
            int trackWidth = width - 8;
            Render2D.roundedRect(context, getX() + 4, getY() + height - 4, trackWidth, 2, 1, 0x80536C90);
            if (value > 0) Render2D.roundedRect(context, getX() + 4, getY() + height - 4,
                    (float) (trackWidth * value), 2, 1, 0xFFA7C9FA);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawCenteredTextWithShadow(renderer, fit(getMessage(), width - 8),
                    getX() + width / 2, getY() + 3, Render2D.applyOpacity(0xFFF2F6FE));
        }
    }

    static final class Control extends ClickableWidget {
        private int swatch;
        private final Text detail;
        private final BooleanSupplier selected;
        private final Runnable action;
        private final Anim hover = new Anim(0);

        Control(int x, int y, int width, int height, Text label, Text detail, BooleanSupplier selected, Runnable action) {
            super(x, y, width, height, label);
            this.detail = detail;
            this.selected = selected;
            this.action = action;
        }

        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean current = selected.getAsBoolean();
            hover.set(active && isSelected() ? 1 : 0);
            int fill = current ? 0xD4385176 : 0xCB212D42;
            int edge = current ? 0xFF88B3F0 : 0x65495D7F;
            fill = Render2D.mix(fill, 0xF047648F, hover.value());
            if (!active) fill = Render2D.withAlpha(fill, 0.5f);
            Render2D.roundedBox(context, getX(), getY(), getWidth(), getHeight(), 6, edge, fill);
            var renderer = MinecraftClient.getInstance().textRenderer;
            int labelColor = Render2D.applyOpacity(active ? 0xFFF2F6FE : 0xFF8292AB);
            if (swatch != 0) {
                Render2D.roundedRect(context, getX() + 5, getY() + 5, 3, getHeight() - 10, 1.5F, swatch);
                context.drawTextWithShadow(renderer, fit(getMessage(), getWidth() - 15), getX() + 11,
                        getY() + (getHeight() - renderer.fontHeight) / 2, labelColor);
            } else if (detail == null) {
                context.drawCenteredTextWithShadow(renderer, fit(getMessage(), getWidth() - 8),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, labelColor);
            } else {
                int x = getX() + 9;
                context.drawTextWithShadow(renderer, fit(getMessage(), getWidth() - 23), x, getY() + 4, labelColor);
                context.drawTextWithShadow(renderer, fit(detail, getWidth() - 23), x, getY() + 14,
                        Render2D.applyOpacity(0xFFACBFDD));
                if (current) Render2D.roundedRect(context, getX() + getWidth() - 7, getY() + 5, 2, getHeight() - 10, 1, 0xFFA7C9FA);
            }
        }

        @Override public void onClick(Click click, boolean doubled) { action.run(); }

        @Override public boolean keyPressed(KeyInput input) {
            if (!active || !visible || !input.isEnterOrSpace()) return false;
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            action.run();
            return true;
        }

        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
