package dev.yachiyoc.ui.widget;

import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Small popup list used by enum settings (font, trigger mode, etc.).
 */
public final class EnumDropdownPopup {
    private static final float ROW_HEIGHT = 18.0F;
    private static EnumSetting<?> setting;
    private static float x;
    private static float y;
    private static float width;

    private EnumDropdownPopup() {
    }

    public static boolean open() {
        return setting != null;
    }

    public static void toggle(EnumSetting<?> value, float anchorX, float anchorY,
                              float width, int screenWidth, int screenHeight) {
        if (setting == value) {
            close();
            return;
        }
        setting = value;
        EnumDropdownPopup.width = width;
        x = clamp(anchorX, 6.0F, screenWidth - width - 6.0F);
        float height = height();
        y = clamp(anchorY, 6.0F, screenHeight - height - 6.0F);
    }

    public static void close() {
        setting = null;
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        if (setting == null) {
            return;
        }
        Object[] values = setting.values();
        float height = height();
        Render2D.roundedBox(context, x, y, width, height, 7.0F, Theme.CARD_BORDER, Theme.WINDOW);
        for (int i = 0; i < values.length; i++) {
            float rowY = y + 4.0F + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x + 4 && mouseX <= x + width - 4
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            boolean selected = values[i] == setting.get();
            if (hovered || selected) {
                Render2D.roundedRect(context, x + 4.0F, rowY, width - 8.0F, ROW_HEIGHT,
                        4.0F, Render2D.withAlpha(Theme.ACCENT, selected ? 0.24F : 0.14F));
            }
            Text2D.draw(context, Text.literal(label(setting, values[i])), x + 10.0F,
                    rowY + 5.0F, selected ? Theme.ACCENT : Theme.TEXT, false);
        }
    }

    public static boolean mouseClicked(Click click) {
        if (setting == null) {
            return false;
        }
        Object[] values = setting.values();
        float height = height();
        if (click.x() >= x && click.x() <= x + width && click.y() >= y && click.y() <= y + height) {
            int index = (int) ((click.y() - y - 4.0F) / ROW_HEIGHT);
            if (index >= 0 && index < values.length) {
                choose(setting, values[index]);
            }
            close();
            return true;
        }
        close();
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void choose(EnumSetting setting, Object value) {
        setting.set((Enum) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String label(EnumSetting setting, Object value) {
        return setting.displayValue((Enum) value);
    }

    private static float height() {
        return setting.values().length * ROW_HEIGHT + 8.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
