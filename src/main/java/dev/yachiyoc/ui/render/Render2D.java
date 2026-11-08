package dev.yachiyoc.ui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class Render2D {
    private static final RenderPipeline LOGO_INVERT = RenderPipeline.builder()
            .withLocation(Identifier.of("yachiyoc", "pipeline/logo_invert"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.INVERT)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderPipeline LOGO_GLOW = RenderPipeline.builder()
            .withLocation(Identifier.of("yachiyoc", "pipeline/logo_glow"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.of("yachiyoc", "core/yachiyo_hud"))
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /**
     * 圆角角部专用管线：顶点着色器用原版的 position_tex_color，片元换成 {@code rounded_corner}，
     * 在屏幕空间里按 SDF 做 1 像素抗锯齿。构建失败时 {@code ROUNDED_CORNER == null}，
     * {@link #roundedRect} 会退化成直角填充，不会崩。
     */
    private static final RenderPipeline ROUNDED_CORNER = buildRoundedCorner();

    private static RenderPipeline buildRoundedCorner() {
        try {
            return RenderPipeline.builder()
                    .withLocation(Identifier.of("yachiyoc", "pipeline/rounded_corner"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader(Identifier.of("yachiyoc", "core/rounded_corner"))
                    .withSampler("Sampler0")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

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
     * Difference/invert blend: the source is combined with whatever is already
     * in the GUI framebuffer. A white logo mask therefore becomes an inverted
     * silhouette of the current background.
     */
    public static void invertTexture(DrawContext ctx, Identifier texture, float x, float y,
                                     float width, float height, int textureWidth, int textureHeight) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.drawTexture(LOGO_INVERT, texture,
                (int) x, (int) y, 0.0F, 0.0F,
                (int) width, (int) height,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /**
     * Icy glow shader for the HUD logo. The animated phase and glow/outline
     * strengths are packed into the vertex color channels because a custom
     * scalar uniform is not exposed by DrawContext.
     */
    public static void glowTexture(DrawContext ctx, Identifier texture, float x, float y,
                                   float width, float height, int textureWidth, int textureHeight,
                                   int packedControls) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.drawTexture(LOGO_GLOW, texture,
                (int) x, (int) y, 0.0F, 0.0F,
                (int) width, (int) height,
                textureWidth, textureHeight, textureWidth, textureHeight,
                packedControls);
    }

    /**
     * 圆角矩形（连续曲率）：中间带 + 直边用 fill，四个角交给片元着色器算 SDF。
     *
     * <p>为什么必须上着色器：GUI 是按 guiScale 放大的，在 Java 里逐"GUI 像素"拼角，
     * 一个 GUI 像素在屏幕上就是 2~3 个像素，大半径的弧线必然一圈台阶；
     * 用贴图遮罩又只能在"缩小采样（锯齿）"和"线性过滤（发糊）"之间取舍。
     * 这里改成四个 r×r 的 quad + {@code rounded_corner.fsh}：片元里按超椭圆方程算到边界的距离，
     * 再用 {@code fwidth} 做**屏幕空间** 1 像素过渡 —— 缩放、半径都不影响锐度。
     */
    public static void roundedRect(DrawContext ctx, float x, float y, float width, float height,
                                   float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) {
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
        int ir = Math.round(r);
        ir = Math.min(ir, Math.min(x1 - x0, y1 - y0) / 2);
        Identifier white = UiTextures.white();
        if (ir < 1 || ROUNDED_CORNER == null || white == null) {
            ctx.fill(x0, y0, x1, y1, color);
            return;
        }

        // 中间竖带（覆盖上下直边） + 左右两条直边
        ctx.fill(x0 + ir, y0, x1 - ir, y1, color);
        ctx.fill(x0, y0 + ir, x0 + ir, y1 - ir, color);
        ctx.fill(x1 - ir, y0 + ir, x1, y1 - ir, color);

        cornerQuad(ctx, white, x0, y0, ir, color, false, false);
        cornerQuad(ctx, white, x1 - ir, y0, ir, color, true, false);
        cornerQuad(ctx, white, x0, y1 - ir, ir, color, false, true);
        cornerQuad(ctx, white, x1 - ir, y1 - ir, ir, color, true, true);
    }

    /** 一个角：r×r 的 quad，用负的 uWidth/vHeight 镜像 UV，让着色器按"圆心在 (1,1)"统一处理。 */
    private static void cornerQuad(DrawContext ctx, Identifier white, int x, int y, int size,
                                   int color, boolean flipX, boolean flipY) {
        float u = flipX ? 1.0F : 0.0F;
        float v = flipY ? 1.0F : 0.0F;
        int uWidth = flipX ? -1 : 1;
        int vHeight = flipY ? -1 : 1;
        ctx.drawTexture(ROUNDED_CORNER, white, x, y, u, v, size, size,
                uWidth, vHeight, 1, 1, color);
    }

    /**
     * 连续曲率指数，必须和 {@code rounded_corner.fsh} 里的 {@code POWER} 一致：
     * 2.0 = 正圆，3.0 = 现在这样，4~5 = 越来越接近方角。
     */
    public static final float SQUIRCLE_POWER = 3.0F;
    public static void roundedBox(DrawContext ctx, float x, float y, float width, float height,
                                  float radius, int border, int fill) {
        roundedRect(ctx, x, y, width, height, radius, border);
        roundedRect(ctx, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F,
                Math.max(0.0F, radius - 1.0F), fill);
    }

    /**
     * 以 {@code (centerX, centerY)} 为锚点缩放整段绘制（弹窗/悬浮窗出现动画用），
     * 记得在画完以后调用 {@link #popScale}。
     */
    public static void pushScale(DrawContext ctx, float centerX, float centerY, float scale) {
        org.joml.Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(scale, scale);
        matrices.translate(-centerX, -centerY);
    }

    public static void popScale(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    /** Soft layered elevation shadow. */    public static void elevation(DrawContext ctx, float x, float y, float width, float height,
                                 float radius, int color) {
        for (int i = 4; i >= 1; i--) {
            float grow = i * 1.8F;
            roundedRect(ctx, x - grow, y + grow * 0.7F, width + grow * 2.0F, height + grow * 1.4F,
                    radius + grow, withAlpha(color, 0.16F / i));
        }
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
