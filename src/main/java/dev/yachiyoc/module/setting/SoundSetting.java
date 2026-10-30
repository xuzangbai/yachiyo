package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.yachiyoc.platform.SoundRegistry;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Sound file picker. The options are the ogg filenames found in
 * assets/yachiyoc/textures/sounds, so new sounds show up automatically.
 */
public final class SoundSetting extends Setting<String> {
    private static SoundSetting open;
    private static float anchorX;
    private static float anchorY;

    public SoundSetting(String owner, String id, String defaultValue) {
        super(owner, id, defaultValue);
        if (get() == null || get().isBlank()) {
            set(SoundRegistry.names().getFirst());
        }
    }

    public List<String> options() {
        return SoundRegistry.names();
    }

    public void toggleOpen() {
        open = open == this ? null : this;
    }

    public void close() {
        if (open == this) {
            open = null;
        }
    }

    public void setAnchor(float x, float y) {
        anchorX = x;
        anchorY = y;
    }

    public static boolean hasOpen() {
        return open != null;
    }

    public static void closeOpen() {
        open = null;
    }

    public static void renderOpen(DrawContext context, float mouseX, float mouseY,
                                  int screenWidth, int screenHeight) {
        if (open == null) {
            return;
        }
        List<String> options = open.options();
        float width = 190.0F;
        float rowHeight = 18.0F;
        float height = options.size() * rowHeight + 8.0F;
        float x = Math.max(4.0F, Math.min(anchorX, screenWidth - width - 4.0F));
        float y = Math.max(4.0F, Math.min(anchorY + 2.0F, screenHeight - height - 4.0F));

        Render2D.roundedBox(context, x, y, width, height, 8.0F, Theme.CARD_BORDER, 0xF4161B24);
        float rowY = y + 4.0F;
        for (String option : options) {
            boolean hovered = mouseX >= x + 4 && mouseX <= x + width - 4
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                Render2D.roundedRect(context, x + 4.0F, rowY, width - 8.0F, rowHeight, 4.0F,
                        Render2D.withAlpha(Theme.ACCENT, 0.20F));
            }
            if (option.equals(open.get())) {
                Render2D.roundedRect(context, x + 6.0F, rowY + 5.0F, 2.0F, 8.0F, 1.0F, Theme.ACCENT);
            }
            Text2D.draw(context, Text.literal(option), x + 13.0F, rowY + 5.0F,
                    Theme.TEXT_DIM, false);
            rowY += rowHeight;
        }
    }

    public static boolean mouseClickedOpen(Click click, int screenWidth, int screenHeight) {
        if (open == null) {
            return false;
        }
        List<String> options = open.options();
        float width = 190.0F;
        float rowHeight = 18.0F;
        float height = options.size() * rowHeight + 8.0F;
        float x = Math.max(4.0F, Math.min(anchorX, screenWidth - width - 4.0F));
        float y = Math.max(4.0F, Math.min(anchorY + 2.0F, screenHeight - height - 4.0F));

        float rowY = y + 4.0F;
        for (String option : options) {
            if (click.x() >= x + 4 && click.x() <= x + width - 4
                    && click.y() >= rowY && click.y() <= rowY + rowHeight) {
                open.set(option);
                open.close();
                return true;
            }
            rowY += rowHeight;
        }
        open.close();
        return true;
    }

    @Override
    public String displayValue() {
        return get() == null ? "" : get();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsString());
        }
    }
}
