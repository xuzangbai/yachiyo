package dev.yachiyoc.ui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

public final class Text2D {
    private Text2D() {
    }

    public static int width(Text text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text);
    }

    public static int height() {
        return MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    public static void draw(DrawContext ctx, Text text, float x, float y, int color, boolean shadow) {
        ctx.drawText(MinecraftClient.getInstance().textRenderer, text, (int) x, (int) y, color, shadow);
    }

    public static void drawRight(DrawContext ctx, Text text, float rightX, float y, int color, boolean shadow) {
        draw(ctx, text, rightX - width(text), y, color, shadow);
    }

    public static void drawCentered(DrawContext ctx, Text text, float centerX, float y, int color, boolean shadow) {
        draw(ctx, text, centerX - width(text) / 2.0F, y, color, shadow);
    }

    public static void drawScaled(DrawContext ctx, Text text, float x, float y, float scale, int color, boolean shadow) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, text, 0, 0, color, shadow);
        ctx.getMatrices().popMatrix();
    }

    public static Text trim(Text text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        StringVisitable trimmed = MinecraftClient.getInstance().textRenderer.trimToWidth(text, maxWidth - 6);
        return Text.literal(trimmed.getString() + "…");
    }
}
