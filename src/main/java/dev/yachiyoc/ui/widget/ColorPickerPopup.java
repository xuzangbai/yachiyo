package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.setting.ColorSetting;
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Smooth HSV picker with alpha preview, editable ARGB and recent colors. */
public final class ColorPickerPopup {
    private static final float WIDTH = 232, HEIGHT = 230;
    private static final Anim open = new Anim(0, 170L, Easing.OUT_CUBIC);
    private static final List<Integer> recent = new ArrayList<>();
    private static ColorSetting setting;
    private static float x, y, hue, saturation, brightness;
    private static int alpha, dragging = -1;
    private static boolean closing, editingHex, replaceHex, invalidHex;
    private static String hex = "";
    private static String shownHex = "";
    private static int shownColor;
    private static boolean hasShownColor;
    private static Text hexText = Text.empty();
    private static float dragX, dragY, scale = 1;

    private ColorPickerPopup() { }

    public static boolean isOpen() { return setting != null; }

    public static void toggle(ColorSetting color, float anchorX, float anchorY, int screenWidth, int screenHeight) {
        if (setting == color && !closing) { close(); return; }
        if (setting != null) saveColor();
        setting = color;
        closing = editingHex = invalidHex = false;
        dragging = -1;
        readColor(color.get());
        x = Math.max(6, Math.min(anchorX, screenWidth - WIDTH - 6));
        y = Math.max(6, Math.min(anchorY, screenHeight - HEIGHT - 6));
        open.snap(0);
        open.set(1);
    }

    private static void readColor(int argb) {
        alpha = argb >>> 24;
        float[] hsb = Color.RGBtoHSB((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, null);
        // Preserve the chosen hue when a neutral color is entered.
        if (hsb[1] > 0.0001F) hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        hex = String.format(Locale.ROOT, "%08X", argb);
    }

    public static void close() {
        if (setting == null || closing) return;
        if (editingHex) commitHex();
        saveColor();
        dragging = -1;
        editingHex = false;
        closing = true;
        open.set(0, 130L, Easing.OUT_CUBIC);
    }

    public static void dismiss() {
        if (setting != null) saveColor();
        setting = null;
        dragging = -1;
        closing = editingHex = false;
    }

    private static void saveColor() {
        int color = setting.get();
        recent.remove(Integer.valueOf(color));
        recent.addFirst(color);
        if (recent.size() > 7) recent.removeLast();
        YachiyoC.saveAll();
    }

    public static void render(DrawContext ctx, float mouseX, float mouseY) {
        if (setting == null) return;
        if (closing && open.value() <= 0.002F) { setting = null; closing = false; return; }
        scale = 0.95F + 0.05F * open.value();
        float mx = localX(mouseX), my = localY(mouseY);
        Render2D.pushScale(ctx, x + WIDTH / 2, y + HEIGHT / 2, scale);
        Render2D.pushOpacity(open.value());
        Render2D.elevation(ctx, x, y, WIDTH, HEIGHT, 14, Theme.SHADOW);
        Render2D.floatingSurface(ctx, x, y, WIDTH, HEIGHT, 14, Theme.OUTLINE_STRONG, Theme.WINDOW, mx, my);
        Text2D.draw(ctx, Text.translatable("colorpicker.yachiyoc.title"), x + 12, y + 10, Theme.TEXT, false);
        Text2D.drawCentered(ctx, Text.literal("×"), x + WIDTH - 15, y + 10, Theme.TEXT_DIM, false);
        Render2D.colorPlane(ctx, x + 12, y + 28, WIDTH - 24, 100, Color.HSBtoRGB(hue, 1, 1));
        float cx = x + 12 + saturation * (WIDTH - 24);
        float cy = y + 28 + (1 - brightness) * 100;
        Render2D.roundedRect(ctx, cx - 4, cy - 4, 8, 8, 4, 0xFF25212E);
        Render2D.roundedRect(ctx, cx - 3, cy - 3, 6, 6, 3, 0xFFFFFFFF);
        Render2D.roundedRect(ctx, cx - 1, cy - 1, 2, 2, 1, 0xFF000000 | (argb() & 0xFFFFFF));
        Render2D.hueBar(ctx, x + 12, y + 140, WIDTH - 24, 9);
        Render2D.alphaBar(ctx, x + 12, y + 160, WIDTH - 24, 9, argb());
        marker(ctx, x + 12 + hue * (WIDTH - 24), y + 140);
        marker(ctx, x + 12 + alpha / 255.0F * (WIDTH - 24), y + 160);
        Render2D.checkerboard(ctx, x + 12, y + 181, 24, 20, 5);
        Render2D.fill(ctx, x + 12, y + 181, 24, 20, argb());
        Render2D.roundedBox(ctx, x + 44, y + 179, WIDTH - 56, 24, 7,
                invalidHex ? Theme.DANGER : editingHex ? Theme.ACCENT : Theme.SEARCH_BORDER, Theme.FIELD);
        int color = argb();
        if (!hasShownColor || shownColor != color) {
            shownColor = color;
            hasShownColor = true;
            shownHex = String.format(Locale.ROOT, "%08X", color);
            hexText = Text.literal("#" + shownHex);
        }
        Text2D.draw(ctx, editingHex ? Text.literal("#" + hex + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : ""))
                : hexText, x + 52, y + 187, invalidHex ? Theme.DANGER : Theme.TEXT, false);
        for (int i = 0; i < recent.size(); i++) {
            float rx = x + 12 + i * 29;
            Render2D.checkerboard(ctx, rx, y + 211, 22, 10, 5);
            Render2D.fill(ctx, rx, y + 211, 22, 10, recent.get(i));
        }
        Render2D.popOpacity();
        Render2D.popScale(ctx);
    }

    private static void marker(DrawContext ctx, float px, float py) {
        Render2D.roundedBox(ctx, px - 2, py - 2, 4, 13, 2, 0xFFFFFFFF, 0xFF3B3155);
    }

    public static boolean mouseClicked(Click original) {
        if (setting == null) return false;
        if (closing) return true;
        float mx = localX(original.x()), my = localY(original.y());
        if (!inside(mx, my, x, y, WIDTH, HEIGHT)) { close(); return true; }
        if (original.button() != 0) return true;
        if (inside(mx, my, x + WIDTH - 28, y + 3, 25, 22)) { close(); return true; }
        if (inside(mx, my, x + 44, y + 179, WIDTH - 56, 24)) {
            editingHex = replaceHex = true;
            invalidHex = false;
            hex = shownHex;
            return true;
        }
        if (editingHex) { commitHex(); editingHex = false; }
        if (inside(mx, my, x + 12, y + 28, WIDTH - 24, 100)) dragging = 0;
        else if (inside(mx, my, x + 9, y + 136, WIDTH - 18, 17)) dragging = 1;
        else if (inside(mx, my, x + 9, y + 156, WIDTH - 18, 17)) dragging = 2;
        else if (inside(mx, my, x, y, WIDTH - 30, 24)) {
            dragging = 3;
            dragX = (float) original.x() - x;
            dragY = (float) original.y() - y;
        } else if (my >= y + 207 && my <= y + 225) {
            int index = (int) ((mx - x - 12) / 29);
            if (mx >= x + 12 && index >= 0 && index < recent.size()) {
                readColor(recent.get(index));
                apply();
                saveColor();
            }
        }
        if (dragging >= 0 && dragging < 3) update(mx, my);
        return true;
    }

    public static boolean mouseDragged(Click click) {
        if (setting == null || dragging < 0 || closing) return false;
        if (dragging == 3) {
            var window = net.minecraft.client.MinecraftClient.getInstance().getWindow();
            x = Math.clamp((float) click.x() - dragX, 6, Math.max(6, window.getScaledWidth() - WIDTH - 6));
            y = Math.clamp((float) click.y() - dragY, 6, Math.max(6, window.getScaledHeight() - HEIGHT - 6));
        } else update(localX(click.x()), localY(click.y()));
        return true;
    }

    public static boolean mouseReleased(Click click) {
        if (setting == null || dragging < 0) return false;
        dragging = -1;
        saveColor();
        return true;
    }

    private static void update(float mouseX, float mouseY) {
        float ratio = Math.clamp((mouseX - x - 12) / (WIDTH - 24), 0, 1);
        if (dragging == 0) {
            saturation = ratio;
            brightness = Math.clamp(1 - (mouseY - y - 28) / 100, 0, 1);
        } else if (dragging == 1) hue = ratio;
        else if (dragging == 2) alpha = Math.round(ratio * 255);
        apply();
    }

    public static boolean keyPressed(KeyInput input) {
        if (setting == null) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (editingHex) { editingHex = false; invalidHex = false; }
            else close();
        } else if (editingHex && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            if (commitHex()) { editingHex = false; saveColor(); }
        } else if (editingHex && input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            hex = replaceHex || hex.isEmpty() ? "" : hex.substring(0, hex.length() - 1);
            replaceHex = invalidHex = false;
        } else if (editingHex && input.key() == GLFW.GLFW_KEY_A && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            replaceHex = true;
        }
        return true;
    }

    public static boolean charTyped(CharInput input) {
        if (setting == null) return false;
        int c = input.codepoint();
        if (editingHex && ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            if (replaceHex) { hex = ""; replaceHex = false; }
            if (hex.length() < 8) hex += Character.toUpperCase((char) c);
            invalidHex = false;
        }
        return true;
    }

    private static boolean commitHex() {
        if (hex.length() != 6 && hex.length() != 8) { invalidHex = true; return false; }
        try {
            int color = (int) Long.parseLong(hex, 16);
            if (hex.length() == 6) color |= 0xFF000000;
            readColor(color);
            apply();
            invalidHex = false;
            return true;
        } catch (NumberFormatException ignored) { invalidHex = true; return false; }
    }

    private static void apply() { setting.set(argb()); }
    private static int argb() { return (alpha << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF); }
    private static float localX(double px) { return x + WIDTH / 2 + ((float) px - x - WIDTH / 2) / scale; }
    private static float localY(double py) { return y + HEIGHT / 2 + ((float) py - y - HEIGHT / 2) / scale; }
    private static boolean inside(float px, float py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}
