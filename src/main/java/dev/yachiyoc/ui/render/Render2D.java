package dev.yachiyoc.ui.render;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class Render2D {
    private Render2D() {
    }

    public static void fill(DrawContext ctx, float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.fill((int) x, (int) y, (int) (x + width), (int) (y + height), color);
    }

    public static void texture(DrawContext ctx, Identifier texture, float x, float y, float width, float height,
                               int textureWidth, int textureHeight) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                (int) x, (int) y, 0.0F, 0.0F,
                (int) width, (int) height,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /**
     * Rounded rectangle with horizontal coverage anti-aliasing on the corner
     * rows. A middle band is filled in one quad, so a full-size window costs
     * roughly 2*radius + 1 draw calls instead of one per row.
     */
    public static void roundedRect(DrawContext ctx, float x, float y, float width, float height,
                                   float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        Identifier mask = UiTextures.rounded();
        if (mask != null && width >= 8.0F && height >= 8.0F && radius >= 1.0F) {
            roundedMask(ctx, mask, x, y, width, height, radius, color);
            return;
        }
        float r = Math.min(radius, Math.min(width, height) / 2.0F);
        int x0 = (int) Math.ceil(x);
        int x1 = (int) Math.floor(x + width);
        int y0 = (int) Math.ceil(y);
        int y1 = (int) Math.floor(y + height);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        int radiusPx = (int) Math.ceil(r);
        int middleTop = y0 + radiusPx;
        int middleBottom = y1 - radiusPx;
        if (middleBottom > middleTop) {
            ctx.fill(x0, middleTop, x1, middleBottom, color);
        }

        for (int row = 0; row < radiusPx; row++) {
            float dy = r - (row + 0.5F);
            float inset = r - (float) Math.sqrt(Math.max(0.0F, r * r - dy * dy));
            int pyTop = y0 + row;
            int pyBottom = y1 - 1 - row;
            fillRoundRow(ctx, x, width, pyTop, inset, color);
            if (pyBottom != pyTop) {
                fillRoundRow(ctx, x, width, pyBottom, inset, color);
            }
        }
    }

    private static final int MASK_SIZE = 64;
    private static final int MASK_RADIUS = 18;
    private static final int MASK_CENTER = MASK_SIZE - MASK_RADIUS * 2;

    /**
     * Nine-slice of the anti-aliased mask. Corners keep their radius, edges and
     * center stretch, and the final color is applied as a vertex tint.
     */
    private static void roundedMask(DrawContext ctx, Identifier mask, float x, float y,
                                    float width, float height, float radius, int color) {
        int r = Math.max(1, (int) Math.round(Math.min(radius, Math.min(width, height) / 2.0F)));
        int w = Math.max(1, (int) Math.round(width));
        int h = Math.max(1, (int) Math.round(height));
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int midW = Math.max(0, w - r * 2);
        int midH = Math.max(0, h - r * 2);

        // corners
        quad(ctx, mask, ix, iy, 0, 0, MASK_RADIUS, MASK_RADIUS, r, r, color);
        quad(ctx, mask, ix + w - r, iy, MASK_SIZE - MASK_RADIUS, 0, MASK_RADIUS, MASK_RADIUS, r, r, color);
        quad(ctx, mask, ix, iy + h - r, 0, MASK_SIZE - MASK_RADIUS, MASK_RADIUS, MASK_RADIUS, r, r, color);
        quad(ctx, mask, ix + w - r, iy + h - r,
                MASK_SIZE - MASK_RADIUS, MASK_SIZE - MASK_RADIUS, MASK_RADIUS, MASK_RADIUS, r, r, color);

        if (midW > 0) {
            quad(ctx, mask, ix + r, iy, MASK_RADIUS, 0, MASK_CENTER, MASK_RADIUS, midW, r, color);
            quad(ctx, mask, ix + r, iy + h - r,
                    MASK_RADIUS, MASK_SIZE - MASK_RADIUS, MASK_CENTER, MASK_RADIUS, midW, r, color);
        }
        if (midH > 0) {
            quad(ctx, mask, ix, iy + r, 0, MASK_RADIUS, MASK_RADIUS, MASK_CENTER, r, midH, color);
            quad(ctx, mask, ix + w - r, iy + r,
                    MASK_SIZE - MASK_RADIUS, MASK_RADIUS, MASK_RADIUS, MASK_CENTER, r, midH, color);
        }
        if (midW > 0 && midH > 0) {
            quad(ctx, mask, ix + r, iy + r, MASK_RADIUS, MASK_RADIUS,
                    MASK_CENTER, MASK_CENTER, midW, midH, color);
        }
    }

    private static void quad(DrawContext ctx, Identifier texture,
                             int destX, int destY, int srcX, int srcY,
                             int srcW, int srcH, int destW, int destH, int color) {
        if (destW <= 0 || destH <= 0) {
            return;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                destX, destY, srcX, srcY,
                destW, destH, srcW, srcH, MASK_SIZE, MASK_SIZE, color);
    }

    private static void fillRoundRow(DrawContext ctx, float x, float width, int y, float inset, int color) {
        float left = x + inset;
        float right = x + width - inset;
        int leftFull = (int) Math.ceil(left);
        int rightFull = (int) Math.floor(right);
        if (rightFull <= leftFull) {
            return;
        }
        ctx.fill(leftFull, y, rightFull, y + 1, color);

        float leftCoverage = 1.0F - (left - (float) Math.floor(left));
        if (leftCoverage > 0.01F && leftCoverage < 0.99F) {
            int edge = (int) Math.floor(left);
            ctx.fill(edge, y, edge + 1, y + 1, withAlpha(color, leftCoverage));
        }

        float rightCoverage = right - (float) Math.floor(right);
        if (rightCoverage > 0.01F && rightCoverage < 0.99F) {
            int edge = (int) Math.floor(right);
            ctx.fill(edge, y, edge + 1, y + 1, withAlpha(color, rightCoverage));
        }
    }

    public static void roundedBox(DrawContext ctx, float x, float y, float width, float height,
                                  float radius, int border, int fill) {
        roundedRect(ctx, x, y, width, height, radius, border);
        roundedRect(ctx, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F,
                Math.max(0.0F, radius - 1.0F), fill);
    }

    public static void horizontalLine(DrawContext ctx, float x, float y, float width, int color) {
        fill(ctx, x, y, width, 1.0F, color);
    }

    public static void verticalLine(DrawContext ctx, float x, float y, float height, int color) {
        fill(ctx, x, y, 1.0F, height, color);
    }

    public static void scissor(DrawContext ctx, float x, float y, float width, float height) {
        ctx.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
    }

    public static void unscissor(DrawContext ctx) {
        ctx.disableScissor();
    }

    public static int withAlpha(int color, float alphaMul) {
        int alpha = (color >>> 24) & 0xFF;
        int next = Math.max(0, Math.min(255, Math.round(alpha * alphaMul)));
        return (next << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int colorA, int colorB, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a = (colorA >>> 24) & 0xFF;
        int r = (colorA >> 16) & 0xFF;
        int g = (colorA >> 8) & 0xFF;
        int b = colorA & 0xFF;
        int a2 = (colorB >>> 24) & 0xFF;
        int r2 = (colorB >> 16) & 0xFF;
        int g2 = (colorB >> 8) & 0xFF;
        int b2 = colorB & 0xFF;
        return ((int) (a + (a2 - a) * clamped) << 24)
                | ((int) (r + (r2 - r) * clamped) << 16)
                | ((int) (g + (g2 - g) * clamped) << 8)
                | (int) (b + (b2 - b) * clamped);
    }
}
