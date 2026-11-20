package dev.yachiyoc.leavesmine;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.yachiyoc.mixin.LeavesMineGameRendererAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Adapts the two LeavesHack render events to Fabric and the vanilla buffers. */
final class LeavesMineRender {
    private static final RenderLayer BOX_SIDES = layer("sides", VertexFormat.DrawMode.QUADS);
    private static final RenderLayer BOX_LINES = layer("lines", VertexFormat.DrawMode.DEBUG_LINES);
    private static final int[][] SIDES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3},
            {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private Vec3d cameraPos = Vec3d.ZERO;
    private VertexConsumerProvider consumers;
    private boolean hasProjection;

    private static RenderLayer layer(String name, VertexFormat.DrawMode mode) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of("yachiyoc", "pipeline/leaves_mine_" + name))
                .withVertexFormat(VertexFormats.POSITION_COLOR, mode)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .build());
        return RenderLayer.of("yachiyoc:leaves_mine_" + name, RenderSetup.builder(pipeline).translucent().build());
    }

    void begin(WorldRenderContext context, MinecraftClient client) {
        hasProjection = context.matrices() != null;
        consumers = context.consumers();
        if (!hasProjection) return;
        var renderer = client.gameRenderer;
        var camera = renderer.getCamera();
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        float fov = ((LeavesMineGameRendererAccessor) renderer).yachiyoc$getLeavesMineFov(camera, tickDelta, true);
        cameraPos = camera.getCameraPos();
        view.set(context.matrices().peek().getPositionMatrix());
        projection.set(renderer.getBasicProjectionMatrix(fov));
    }

    void end() {
        consumers = null;
    }

    void box(Box box, int sideColor, int lineColor, ShapeMode mode) {
        if (consumers == null || !hasProjection) return;
        Vec3d[] vertices = {
                relative(box.minX, box.minY, box.minZ), relative(box.maxX, box.minY, box.minZ),
                relative(box.minX, box.minY, box.maxZ), relative(box.maxX, box.minY, box.maxZ),
                relative(box.minX, box.maxY, box.minZ), relative(box.maxX, box.maxY, box.minZ),
                relative(box.minX, box.maxY, box.maxZ), relative(box.maxX, box.maxY, box.maxZ)
        };
        if (mode != ShapeMode.Lines) {
            VertexConsumer sides = consumers.getBuffer(BOX_SIDES);
            for (int[] side : SIDES) {
                for (int index : side) vertex(sides, vertices[index], sideColor);
            }
        }
        if (mode != ShapeMode.Sides) {
            VertexConsumer lines = consumers.getBuffer(BOX_LINES);
            for (int[] edge : EDGES) {
                vertex(lines, vertices[edge[0]], lineColor);
                vertex(lines, vertices[edge[1]], lineColor);
            }
        }
    }

    void label(DrawContext context, MinecraftClient client, Vec3d pos, String text, int color) {
        if (!hasProjection) return;
        Vec3d relative = pos.subtract(cameraPos);
        Vector4f projected = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1);
        view.transform(projected);
        projection.transform(projected);
        if (projected.w <= 0) return;
        float x = (projected.x / projected.w + 1) * context.getScaledWindowWidth() / 2;
        float y = (1 - projected.y / projected.w) * context.getScaledWindowHeight() / 2;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        float scale = (float) (1.5 * MathHelper.clamp(1 - cameraPos.distanceTo(pos) * 0.01, 0.5, Integer.MAX_VALUE));
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            context.drawTextWithShadow(client.textRenderer, text, -client.textRenderer.getWidth(text) / 2,
                    -client.textRenderer.fontHeight / 2, color);
        } finally {
            matrices.popMatrix();
        }
    }

    private Vec3d relative(double x, double y, double z) {
        return new Vec3d(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
    }

    private VertexConsumer vertex(VertexConsumer consumer, Vec3d point, int color) {
        return consumer.vertex(view, (float) point.x, (float) point.y, (float) point.z).color(color);
    }

    enum ShapeMode { Lines, Sides, Both }
}
