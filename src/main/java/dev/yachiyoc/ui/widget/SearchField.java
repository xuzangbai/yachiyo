package dev.yachiyoc.ui.widget;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class SearchField {
    private float x;
    private float y;
    private float width;
    private float height;
    private String query = "";
    private boolean focused;
    private Text placeholder = Text.translatable("ui.yachiyoc.search");

    public SearchField(float x, float y, float width, float height) {
        setBounds(x, y, width, height);
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPlaceholder(Text placeholder) {
        this.placeholder = placeholder;
    }

    public String query() {
        return query;
    }

    public boolean focused() {
        return focused;
    }

    public void focus() {
        this.focused = true;
    }

    public void unfocus() {
        this.focused = false;
    }

    public boolean matches(Module module) {
        if (query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return module.id().contains(needle)
                || module.name().getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    public void render(DrawContext ctx, float mouseX, float mouseY) {
        int border = focused ? 0xB063B8FF : Theme.CARD_BORDER;
        int fill = focused ? 0xCC111722 : 0x9910151D;
        Render2D.roundedBox(ctx, x, y, width, height, Theme.SMALL_RADIUS, border, fill);

        float textY = y + (height - Text2D.height()) / 2.0F + 1.0F;
        if (query.isEmpty()) {
            Text2D.draw(ctx, placeholder, x + 10.0F, textY, Theme.TEXT_MUTED, false);
        } else {
            Text shown = Text2D.trim(Text.literal(query), (int) width - 30);
            Text2D.draw(ctx, shown, x + 10.0F, textY, Theme.TEXT, false);
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float cursorX = x + 10.0F + Text2D.width(Text.literal(query)) + 1.0F;
                Render2D.fill(ctx, cursorX, y + 5.0F, 1.0F, height - 10.0F, Theme.ACCENT);
            }
        }
    }

    public boolean mouseClicked(Click click) {
        boolean inside = click.x() >= x && click.x() <= x + width
                && click.y() >= y && click.y() <= y + height;
        if (inside) {
            focused = true;
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyInput input) {
        if (!focused) {
            return false;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) {
            focused = false;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(CharInput input) {
        if (!focused || !input.isValidChar()) {
            return false;
        }
        String next = new String(Character.toChars(input.codepoint()));
        if (next.equals("§")) {
            return true;
        }
        if (query.length() + next.length() <= 32) {
            query += next;
        }
        return true;
    }
}
