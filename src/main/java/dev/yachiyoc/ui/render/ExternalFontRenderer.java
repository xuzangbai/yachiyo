package dev.yachiyoc.ui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional external TTF renderer. Only the selected file is read; it is never
 * copied into the mod jar. Glyphs are baked into small dynamic textures and
 * cached, so Chinese text does not require a bundled CJK font.
 */
public final class ExternalFontRenderer {
    public static final int SUPERSAMPLE = 2;

    private final Font font;
    private final FontMetrics metrics;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final int height;

    public ExternalFontRenderer(Path path, float size) throws IOException, java.awt.FontFormatException {
        this.font = Font.createFont(Font.TRUETYPE_FONT, path.toFile())
                .deriveFont(Font.PLAIN, size * SUPERSAMPLE);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        graphics.setFont(font);
        this.metrics = graphics.getFontMetrics();
        graphics.dispose();
        this.height = Math.max(1, (int) Math.ceil(metrics.getHeight() / (float) SUPERSAMPLE));
    }

    public static ExternalFontRenderer load(Path path, float size) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                return null;
            }
            return new ExternalFontRenderer(path, size);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public int height() {
        return height;
    }

    public int width(String text) {
        return Math.max(0, (int) Math.ceil(metrics.stringWidth(text) / (float) SUPERSAMPLE));
    }

    public String trim(String text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int current = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int advance = (int) Math.ceil(metrics.charWidth(cp) / (float) SUPERSAMPLE);
            if (current + advance > maxWidth - 6) {
                break;
            }
            out.appendCodePoint(cp);
            current += advance;
            i += Character.charCount(cp);
        }
        return out + "…";
    }

    public void draw(DrawContext context, Text text, float x, float y, int color, boolean shadow) {
        String value = text.getString();
        int penX = (int) x;
        int top = (int) y;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                penX += (int) Math.ceil(metrics.charWidth(cp) / (float) SUPERSAMPLE);
                continue;
            }
            Glyph glyph = glyph(cp);
            if (glyph == null) {
                penX += (int) Math.ceil(metrics.charWidth(cp) / (float) SUPERSAMPLE);
                continue;
            }
            if (shadow) {
                context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, glyph.id(),
                        penX + 1, top + 1, 0.0F, 0.0F, glyph.drawWidth(), glyph.drawHeight(),
                        glyph.width(), glyph.height(), 0x88000000);
            }
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, glyph.id(),
                    penX, top, 0.0F, 0.0F, glyph.drawWidth(), glyph.drawHeight(),
                    glyph.width(), glyph.height(), color);
            penX += (int) Math.ceil(metrics.charWidth(cp) / (float) SUPERSAMPLE);
        }
    }

    private Glyph glyph(int codePoint) {
        Glyph cached = glyphs.get(codePoint);
        if (cached != null) {
            return cached;
        }
        String text = new String(Character.toChars(codePoint));
        int width = Math.max(1, (int) Math.ceil(metrics.stringWidth(text) / (float) SUPERSAMPLE));
        int bakeWidth = width * SUPERSAMPLE;
        int bakeHeight = Math.max(1, metrics.getHeight());
        BufferedImage image = new BufferedImage(bakeWidth, bakeHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, 0, metrics.getAscent());
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(bakeWidth, bakeHeight, false);
        for (int py = 0; py < bakeHeight; py++) {
            for (int px = 0; px < bakeWidth; px++) {
                nativeImage.setColorArgb(px, py, image.getRGB(px, py));
            }
        }
        Identifier id = Identifier.of("yachiyoc", "dynamic/font/" + Integer.toHexString(codePoint)
                + "_" + Integer.toHexString(System.identityHashCode(this)));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(
                () -> "yachiyoc_external_font", nativeImage);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        Glyph glyph = new Glyph(id, bakeWidth, bakeHeight, width, height);
        if (glyphs.size() > 2048) {
            glyphs.clear();
        }
        glyphs.put(codePoint, glyph);
        return glyph;
    }

    private record Glyph(Identifier id, int width, int height, int drawWidth, int drawHeight) {
    }
}
