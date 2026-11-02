package dev.yachiyoc.ui.render;

import dev.yachiyoc.platform.FontManagerAccessor;
import dev.yachiyoc.platform.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

public final class Text2D {
    private static FontChoice currentFont = FontChoice.VANILLA;
    private static TextRenderer customRenderer;
    private static ExternalFontRenderer externalRenderer;

    private Text2D() {
    }

    public static void setFont(FontChoice choice) {
        currentFont = choice == null ? FontChoice.VANILLA : choice;
        customRenderer = null;
        externalRenderer = null;
    }

    public static void setExternalFont(java.nio.file.Path path) {
        currentFont = FontChoice.EXTERNAL;
        customRenderer = null;
        externalRenderer = ExternalFontRenderer.load(path, 9.5F);
    }

    private static TextRenderer renderer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentFont == FontChoice.VANILLA || currentFont == FontChoice.EXTERNAL) {
            return client.textRenderer;
        }
        if (customRenderer != null) {
            return customRenderer;
        }
        FontManager manager = ((MinecraftClientAccessor) client).yachiyoc$fontManager();
        FontStorage storage = ((FontManagerAccessor) manager).yachiyoc$getStorage(currentFont.fontId());
        if (storage == null) {
            return client.textRenderer;
        }
        customRenderer = new TextRenderer(new StorageGlyphs(storage));
        return customRenderer;
    }

    private static boolean external() {
        return currentFont == FontChoice.EXTERNAL && externalRenderer != null;
    }

    public static int width(Text text) {
        if (external()) {
            return externalRenderer.width(text.getString());
        }
        return renderer().getWidth(text);
    }

    public static int height() {
        if (external()) {
            return externalRenderer.height();
        }
        return renderer().fontHeight;
    }

    public static void draw(DrawContext ctx, Text text, float x, float y, int color, boolean shadow) {
        if (external()) {
            externalRenderer.draw(ctx, text, x, y, color, shadow);
            return;
        }
        ctx.drawText(renderer(), text, (int) x, (int) y, color, shadow);
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
        if (external()) {
            externalRenderer.draw(ctx, text, 0.0F, 0.0F, color, shadow);
        } else {
            ctx.drawText(renderer(), text, 0, 0, color, shadow);
        }
        ctx.getMatrices().popMatrix();
    }

    public static Text trim(Text text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        if (external()) {
            return Text.literal(externalRenderer.trim(text.getString(), maxWidth));
        }
        StringVisitable trimmed = renderer().trimToWidth(text, maxWidth - 6);
        return Text.literal(trimmed.getString() + "…");
    }

    private record StorageGlyphs(FontStorage storage) implements TextRenderer.GlyphsProvider {
        @Override
        public GlyphProvider getGlyphs(StyleSpriteSource source) {
            return storage.getGlyphs(false);
        }

        @Override
        public EffectGlyph getRectangleGlyph() {
            return storage.getRectangleBakedGlyph();
        }
    }
}
