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
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.UiStyle;

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
    private static final RenderPipeline GLASS = effectPipeline("liquid_glass");
    private static final RenderPipeline COLOR_PLANE = effectPipeline("color_plane");
    private static final RenderPipeline HUE_BAR = effectPipeline("hue_bar");
    private static final RenderPipeline ALPHA_BAR = effectPipeline("alpha_bar");
    private static final RenderPipeline MENU_VIDEO = effectPipeline("menu_video");
    public enum MenuScene {
        AURORA("aurora", 0xFF78E1B7),
        CAUSTICS("caustics", 0xFF46D6DD),
        STAR_ORBITS("star_orbits", 0xFFACA0F5),
        MOLTEN_GOLD("molten_gold", 0xFFFFBE66),
        OCEAN_WAVES("ocean_waves", 0xFF82BDD1),
        TIDAL_WALTZ("tidal_waltz", 0xFFC59CCA),
        HOME_FLOW("home_flow", 0xFFE5AAD8);

        public final String id;
        public final int color;
        private final RenderPipeline pipeline;

        MenuScene(String id, int color) {
            this.id = id;
            this.color = color;
            this.pipeline = effectPipeline(id.equals("home_flow") ? id : "menu_" + id);
        }

        public static MenuScene fromId(String id) {
            for (MenuScene scene : values()) if (scene.id.equals(id)) return scene;
            return TIDAL_WALTZ;
        }
    }
    private static final float[] OPACITY_STACK = new float[16];
    private static int opacityDepth;
    private static float opacity = 1;

    public static void pushOpacity(float amount) {
        OPACITY_STACK[opacityDepth++] = opacity;
        opacity *= Math.clamp(amount, 0, 1);
    }

    public static void popOpacity() {
        opacity = OPACITY_STACK[--opacityDepth];
    }

    public static int applyOpacity(int color) {
        return withAlpha(color, opacity);
    }

    private static RenderPipeline effectPipeline(String name) {
        return RenderPipeline.builder()
                .withLocation(Identifier.of("yachiyoc", "pipeline/" + name))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader(Identifier.of("yachiyoc", "core/" + name))
                .withSampler("Sampler0")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false).withCull(false).build();
    }

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

    /** Keep blur behind the UI so text and controls stay sharp. */
    public static void background(DrawContext ctx, int width, int height, float appear) {
        float glass = UiStyle.glassAmount();
        if (glass > 0.01F) {
            ctx.applyBlur();
        }
        ctx.fillGradient(0, 0, width, height,
                withAlpha(Theme.OVERLAY_TOP, appear * (1.0F - glass * 0.65F)),
                withAlpha(Theme.OVERLAY_BOTTOM, appear * (1.0F - glass * 0.65F)));
    }

    /** Translucent tinted surface with a moving specular highlight and soft rim. */
    public static void surface(DrawContext ctx, float x, float y, float width, float height,
                               float radius, int border, int color, float mouseX, float mouseY) {
        surface(ctx, x, y, width, height, radius, border, color, mouseX, mouseY, 0.42F);
    }

    public static void floatingSurface(DrawContext ctx, float x, float y, float width, float height,
                                       float radius, int border, int color, float mouseX, float mouseY) {
        surface(ctx, x, y, width, height, radius, border, color, mouseX, mouseY, 0.14F);
    }

    private static void surface(DrawContext ctx, float x, float y, float width, float height,
                                float radius, int border, int color, float mouseX, float mouseY, float transmission) {
        float glass = UiStyle.glassAmount();
        if (glass <= 0.002F) {
            roundedBox(ctx, x, y, width, height, radius, border, color);
            return;
        }
        roundedRect(ctx, x, y, width, height, radius, withAlpha(color, 1.0F - glass * transmission));
        int px = Math.round(Math.clamp((mouseX - x) / Math.max(1.0F, width), 0.0F, 1.0F) * 255.0F);
        int py = Math.round(Math.clamp((mouseY - y) / Math.max(1.0F, height), 0.0F, 1.0F) * 255.0F);
        int r = Math.round(Math.clamp(radius / Math.max(1.0F, Math.min(width, height)), 0.0F, 0.5F) * 255.0F);
        effect(ctx, GLASS, x, y, width, height, (Math.round(glass * opacity * 255.0F) << 24) | (px << 16) | (py << 8) | r);
    }

    private static void effect(DrawContext ctx, RenderPipeline pipeline, float x, float y,
                               float width, float height, int controls) {
        Identifier white = UiTextures.white();
        if (white != null && width > 0 && height > 0) {
            ctx.drawTexture(pipeline, white, (int) x, (int) y, 0, 0,
                    (int) width, (int) height, 1, 1, 1, 1, controls);
        }
    }

    public static void menuShader(DrawContext context, MenuScene scene, long timeNanos) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        fill(context, 0, 0, width, height, 0xFF10152D);
        int millis = (int) ((timeNanos / 1_000_000L) & 0xffffff);
        effect(context, scene.pipeline, 0, 0, width, height, 0xff000000 | millis);
    }

    public static void homeFlow(DrawContext context, int width, int height, float anchor, long elapsedNanos) {
        fill(context, 0, 0, width, height, Theme.HOME_BACKGROUND);
        int phase = (int) (elapsedNanos / 2_000_000L) & 0xffff;
        int center = Math.round(Math.clamp(anchor, 0, 1) * 255);
        effect(context, MenuScene.HOME_FLOW.pipeline, 0, 0, width, height, 0xff000000 | (phase << 8) | center);
    }

    public static void menuVideo(DrawContext context, Identifier texture, int sourceWidth, int sourceHeight,
                                 int textureWidth, int textureHeight, int aspectWidth, int aspectHeight,
                                 int rotation, int matrix, boolean fullRange) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        double aspect = (double) sourceWidth / sourceHeight;
        if (aspectWidth > 0 && aspectHeight > 0) aspect *= (double) aspectWidth / aspectHeight;
        int turns = Math.floorMod(rotation <= 3 ? rotation : rotation / 90, 4);
        if ((turns & 1) != 0) aspect = 1 / aspect;
        int width = Math.max(screenWidth, (int) Math.ceil(screenHeight * aspect));
        int height = Math.max(screenHeight, (int) Math.ceil(screenWidth / aspect));
        int controls = 0xff000000 | (matrix << 16) | (fullRange ? 0x100 : 0) | (turns << 2) | (textureWidth * 4 - sourceWidth);
        context.drawTexture(MENU_VIDEO, texture, (screenWidth - width) / 2, (screenHeight - height) / 2, 0, 0,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight, controls);
        fill(context, 0, 0, screenWidth, screenHeight, 0x25000000);
    }

    public static void colorPlane(DrawContext ctx, float x, float y, float width, float height, int hueColor) {
        effect(ctx, COLOR_PLANE, x, y, width, height, applyOpacity(0xFF000000 | (hueColor & 0xFFFFFF)));
    }

    public static void hueBar(DrawContext ctx, float x, float y, float width, float height) {
        effect(ctx, HUE_BAR, x, y, width, height, applyOpacity(0xFFFFFFFF));
    }

    public static void alphaBar(DrawContext ctx, float x, float y, float width, float height, int color) {
        checkerboard(ctx, x, y, width, height, 4.0F);
        effect(ctx, ALPHA_BAR, x, y, width, height, applyOpacity(0xFF000000 | (color & 0xFFFFFF)));
    }

    public static void checkerboard(DrawContext ctx, float x, float y, float width, float height, float cell) {
        scissor(ctx, x, y, width, height);
        for (int row = 0; row < Math.ceil(height / cell); row++) {
            for (int column = 0; column < Math.ceil(width / cell); column++) {
                fill(ctx, x + column * cell, y + row * cell, cell, cell,
                        (row + column) % 2 == 0 ? 0xFFE4DFEC : 0xFFB8B2C4);
            }
        }
        unscissor(ctx);
    }

    public static void fill(DrawContext ctx, float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.fill((int) x, (int) y, (int) (x + width), (int) (y + height), applyOpacity(color));
    }

    public static void texture(DrawContext ctx, Identifier texture, float x, float y, float width, float height,
                               int textureWidth, int textureHeight) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                (int) x, (int) y, 0.0F, 0.0F,
                (int) width, (int) height,
                textureWidth, textureHeight, textureWidth, textureHeight, applyOpacity(0xFFFFFFFF));
    }

    /** Atlas region with independent screen and source sizes, also available during startup. */
    public static void textureRegion(DrawContext ctx, Identifier texture, float x, float y, float width, float height,
                                     int u, int v, int regionWidth, int regionHeight, int atlasWidth, int atlasHeight) {
        if (texture == null || width <= 0 || height <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), u, v,
                Math.max(1, Math.round(width)), Math.max(1, Math.round(height)), regionWidth, regionHeight,
                atlasWidth, atlasHeight, applyOpacity(0xFFFFFFFF));
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
                textureWidth, textureHeight, textureWidth, textureHeight, applyOpacity(0xFFFFFFFF));
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
        color = applyOpacity(color);
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
