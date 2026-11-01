package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.awt.Color;

/**
 * Inline color palette opened by right-clicking a color swatch. It behaves like
 * Alien's PickerButton: a small SV square plus hue/alpha bars attached to the
 * row, not a full-screen color editor.
 */
public final class ColorPickerPopup {
    private static final float WIDTH = 180.0F;
    private static final float HEIGHT = 156.0F;
    private static ColorSetting setting;
    private static float x;
    private static float y;
    private static float hue;
    private static float saturation;
    private static float brightness;
    private static int alpha;
    private static int dragging = -1;

    private ColorPickerPopup() {
    }

    public static boolean isOpen() {
        return setting != null;
    }

    public static void toggle(ColorSetting color, float anchorX, float anchorY,
                              int screenWidth, int screenHeight) {
        if (setting == color) {
            close();
            return;
        }
        setting = color;
        int argb = color.get();
        alpha = (argb >>> 24) & 0xFF;
        float[] hsb = Color.RGBtoHSB((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        x = Math.max(4.0F, Math.min(anchorX, screenWidth - WIDTH - 4.0F));
        y = Math.max(4.0F, Math.min(anchorY, screenHeight - HEIGHT - 4.0F));
    }

    public static void close() {
        setting = null;
        dragging = -1;
    }

    public static void render(DrawContext context, float mouseX, float mouseY) {
        if (setting == null) {
            return;
        }

        Render2D.roundedBox(context, x, y, WIDTH, HEIGHT, 8.0F, Theme.CARD_BORDER, Theme.WINDOW);
        Text2D.draw(context, Text.translatable("colorpicker.yachiyoc.title"), x + 8.0F, y + 6.0F,
                Theme.TEXT, false);
        Text2D.draw(context, Text.literal(String.format("#%08X", argb())),
                x + WIDTH - 8.0F - Text2D.width(Text.literal(String.format("#%08X", argb()))),
                y + 6.0F, Theme.TEXT_MUTED, false);

        float svX = x + 10.0F;
        float svY = y + 22.0F;
        float svW = 100.0F;
        float svH = 68.0F;
        for (int iy = 0; iy < 17; iy++) {
            for (int ix = 0; ix < 25; ix++) {
                float s = ix / 24.0F;
                float v = 1.0F - iy / 16.0F;
                Render2D.fill(context, svX + ix * svW / 25.0F, svY + iy * svH / 17.0F,
                        svW / 25.0F + 0.5F, svH / 17.0F + 0.5F, hsbColor(s, v, 255));
            }
        }
        Render2D.roundedBox(context, svX, svY, svW, svH, 3.0F,
                Render2D.withAlpha(Theme.TEXT, 0.25F), 0x00000000);

        float cursorX = svX + saturation * (svW - 1.0F);
        float cursorY = svY + (1.0F - brightness) * (svH - 1.0F);
        Render2D.roundedBox(context, cursorX - 3.0F, cursorY - 3.0F, 6.0F, 6.0F, 3.0F,
                0xFFFFFFFF, 0x00000000);

        float barX = x + 10.0F;
        float hueY = y + 100.0F;
        float alphaY = y + 118.0F;
        float barW = 100.0F;
        for (int i = 0; i < 40; i++) {
            float ratio = i / 39.0F;
            Render2D.fill(context, barX + ratio * barW, hueY, barW / 40.0F + 0.5F, 8.0F,
                    (255 << 24) | (Color.HSBtoRGB(ratio, 1.0F, 1.0F) & 0xFFFFFF));
        }
        for (int i = 0; i < 40; i++) {
            float ratio = i / 39.0F;
            int a = Math.round(ratio * 255.0F);
            Render2D.fill(context, barX + ratio * barW, alphaY, barW / 40.0F + 0.5F, 8.0F,
                    (a << 24) | (argb() & 0xFFFFFF));
        }
        Render2D.roundedBox(context, barX, hueY, barW, 8.0F, 3.0F,
                Render2D.withAlpha(Theme.TEXT, 0.2F), 0x00000000);
        Render2D.roundedBox(context, barX, alphaY, barW, 8.0F, 3.0F,
                Render2D.withAlpha(Theme.TEXT, 0.2F), 0x00000000);

        Render2D.roundedRect(context, barX + hue * barW - 1.5F, hueY - 2.0F, 3.0F, 12.0F,
                1.5F, Theme.TEXT);
        Render2D.roundedRect(context, barX + (alpha / 255.0F) * barW - 1.5F, alphaY - 2.0F,
                3.0F, 12.0F, 1.5F, Theme.TEXT);

        Text2D.draw(context, Text.literal("H"), x + 116.0F, y + 100.0F, Theme.TEXT_DIM, false);
        Text2D.draw(context, Text.literal("A"), x + 116.0F, y + 118.0F, Theme.TEXT_DIM, false);
        Text2D.draw(context, Text.translatable("colorpicker.yachiyoc.hint"),
                x + 8.0F, y + 136.0F, Theme.TEXT_MUTED, false);
    }

    public static boolean mouseClicked(Click click) {
        if (setting == null) {
            return false;
        }
        if (click.x() < x || click.x() > x + WIDTH || click.y() < y || click.y() > y + HEIGHT) {
            close();
            return true;
        }
        float svX = x + 10.0F;
        float svY = y + 22.0F;
        float svW = 100.0F;
        float svH = 68.0F;
        if (click.x() >= svX && click.x() <= svX + svW
                && click.y() >= svY && click.y() <= svY + svH) {
            dragging = 0;
            updateSaturationBrightness((float) click.x(), (float) click.y());
            return true;
        }
        if (click.x() >= svX && click.x() <= svX + svW
                && click.y() >= y + 98.0F && click.y() <= y + 112.0F) {
            dragging = 1;
            updateHue((float) click.x());
            return true;
        }
        if (click.x() >= svX && click.x() <= svX + svW
                && click.y() >= y + 116.0F && click.y() <= y + 130.0F) {
            dragging = 2;
            updateAlpha((float) click.x());
            return true;
        }
        return true;
    }

    public static boolean mouseDragged(Click click) {
        if (setting == null || dragging < 0) {
            return false;
        }
        if (dragging == 0) {
            updateSaturationBrightness((float) click.x(), (float) click.y());
        } else if (dragging == 1) {
            updateHue((float) click.x());
        } else {
            updateAlpha((float) click.x());
        }
        return true;
    }

    public static boolean mouseReleased(Click click) {
        if (setting == null || dragging < 0) {
            return false;
        }
        dragging = -1;
        YachiyoC.config().save();
        return true;
    }

    private static void updateSaturationBrightness(float mouseX, float mouseY) {
        float svX = x + 10.0F;
        float svY = y + 22.0F;
        saturation = Math.max(0.0F, Math.min(1.0F, (mouseX - svX) / 100.0F));
        brightness = Math.max(0.0F, Math.min(1.0F, 1.0F - (mouseY - svY) / 68.0F));
        apply();
    }

    private static void updateHue(float mouseX) {
        hue = Math.max(0.0F, Math.min(1.0F, (mouseX - (x + 10.0F)) / 100.0F));
        apply();
    }

    private static void updateAlpha(float mouseX) {
        alpha = Math.round(Math.max(0.0F, Math.min(1.0F, (mouseX - (x + 10.0F)) / 100.0F)) * 255.0F);
        apply();
    }

    private static void apply() {
        setting.set(argb());
    }

    private static int argb() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    private static int hsbColor(float s, float v, int a) {
        return (a << 24) | (Color.HSBtoRGB(hue, s, v) & 0xFFFFFF);
    }
}
