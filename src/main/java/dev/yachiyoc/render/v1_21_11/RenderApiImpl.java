package dev.yachiyoc.render.v1_21_11;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.yachiyoc.render.RenderApi;
import dev.yachiyoc.render.ShapeMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ItemCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import org.joml.Vector3f;import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * 1.21.11 的 {@link RenderApi} 实现 —— **整个工程里唯一允许 import
 * {@code RenderLayer} / {@code RenderLayers} / {@code VertexConsumer} / {@code MatrixStack} 的地方**
 * （2D 那边对应的是 {@code Render2D} / {@code Text2D}）。
 *
 * <p>几何走原版提供的层（{@code RenderLayers.LINES} / {@code debugQuads()}），文字走
 * {@code TextRenderer#draw}；穿墙另有一条自己注册的 {@code position_color} 管线
 * （{@link #THROUGH_WALL_QUADS}），由 {@link #throughWalls(boolean)} 切换。
 */
public final class RenderApiImpl implements RenderApi {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int FULL_LIGHT = 0x00F000F0;

    /**
     * 穿墙用的层：和 {@code debugQuads} 同一个 {@code position_color} 着色器与顶点格式，
     * 只把深度测试关掉。
     *
     * <p>两个坑（照 SLM 的 {@code Render_v1_21_11} 抄的）：
     * <ol>
     *   <li>管线必须走 {@link RenderPipelines#register}，没注册的管线不会编译着色器，画出来是空的；</li>
     *   <li>必须基于 {@link RenderPipelines#POSITION_COLOR_SNIPPET} 建，那个 snippet 里带着
     *       变换/投影 uniform 和顶点格式，从零 build 出来的管线即使编译过也没有矩阵，一样看不见。</li>
     * </ol>
     */
    private static final RenderPipeline THROUGH_WALL_PIPELINE =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("yachiyoc", "pipeline/esp_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final RenderLayer THROUGH_WALL_QUADS = RenderLayer.of(
            "yachiyoc:esp_through_walls",
            RenderSetup.builder(THROUGH_WALL_PIPELINE).translucent().build());

    private static final OrderedRenderCommandQueueImpl ITEM_QUEUE = new OrderedRenderCommandQueueImpl();
    private static final ItemCommandRenderer ITEM_RENDERER = new ItemCommandRenderer();
    private MatrixStack matrices;
    private VertexConsumerProvider consumers;
    private Camera camera;
    private Matrix4f projection;
    private float tickDelta;
    private boolean inWorld;
    private boolean throughWalls;

    @Override
    public void throughWalls(boolean enabled) {
        this.throughWalls = enabled;
    }

    @Override
    public boolean throughWalls() {
        return throughWalls;
    }

    /** 画四边形时该用哪一层：穿墙模式换掉深度测试，其余完全一样。 */
    private RenderLayer quads() {
        return throughWalls ? THROUGH_WALL_QUADS : RenderLayers.debugQuads();
    }

    @Override
    public void beginWorld(MatrixStack matrices, VertexConsumerProvider consumers, Matrix4f projection,
                           Matrix4f positionMatrix, float tickDelta) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.projection = projection;
        this.camera = MC.gameRenderer == null ? null : MC.gameRenderer.getCamera();
        this.tickDelta = tickDelta;
        this.inWorld = matrices != null && consumers != null && camera != null && camera.isReady();
    }

    @Override
    public void endWorld() {
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
        matrices = null;
        consumers = null;
        camera = null;
        inWorld = false;
    }

    @Override
    public boolean inWorld() {
        return inWorld;
    }

    @Override
    public float tickDelta() {
        return tickDelta;
    }

    @Override
    public Vec3d cameraPos() {
        return camera == null ? Vec3d.ZERO : camera.getCameraPos();
    }

    @Override
    public void box(Box box, int sideColor, int lineColor, ShapeMode mode) {
        if (!inWorld || mode == null) {
            return;
        }
        if (mode != ShapeMode.LINES) {
            sides(box, sideColor);
        }
        if (mode != ShapeMode.SIDES) {
            edges(box, lineColor);
        }
    }

    @Override
    public void line(Vec3d from, Vec3d to, int color, float width) {
        if (!inWorld) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cam = cameraPos();
        float x1 = (float) (from.x - cam.x);
        float y1 = (float) (from.y - cam.y);
        float z1 = (float) (from.z - cam.z);
        float x2 = (float) (to.x - cam.x);
        float y2 = (float) (to.y - cam.y);
        float z2 = (float) (to.z - cam.z);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        buffer.vertex(matrix, x1, y1, z1).color(color).normal(nx, ny, nz).lineWidth(width);
        buffer.vertex(matrix, x2, y2, z2).color(color).normal(nx, ny, nz).lineWidth(width);
    }

    @Override
    public void ribbon(Vec3d from, Vec3d to, float width, int color) {
        if (!inWorld || width <= 0.0F) {
            return;
        }
        Vec3d forward = to.subtract(from);
        double length = forward.length();
        if (length < 1.0E-5D) {
            return;
        }
        Vec3d dir = forward.multiply(1.0D / length);
        // 面向相机的方向 = 段方向 × 到相机的方向；两者平行时（正对着看）退化，那就换一个轴向
        Vec3d side = dir.crossProduct(cameraPos().subtract(from));
        if (side.lengthSquared() < 1.0E-10D) {
            side = dir.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSquared() < 1.0E-10D) {
            return;
        }
        side = side.normalize().multiply(width * 0.5D);
        strip(from, to, side, color);
    }

    /** 以 {@code side}（半宽向量）为宽度方向，把一段线画成一个四边形。 */
    private void strip(Vec3d from, Vec3d to, Vec3d side, int color) {
        quad(from.add(side), from.subtract(side), to.subtract(side), to.add(side), color);
    }

    @Override
    public void ribbonOnPlane(Vec3d from, Vec3d to, float width, Vec3d normal, int color) {
        if (!inWorld || width <= 0.0F || normal == null) {
            return;
        }
        Vec3d forward = to.subtract(from);
        double length = forward.length();
        if (length < 1.0E-5D) {
            return;
        }
        // 宽度沿"段方向 × 法线"铺，这样带子始终躺在平面内
        Vec3d side = forward.multiply(1.0D / length).crossProduct(normal);
        if (side.lengthSquared() < 1.0E-10D) {
            return;
        }
        strip(from, to, side.normalize().multiply(width * 0.5D), color);
    }

    @Override
    public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        if (!inWorld) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(quads());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cam = cameraPos();
        vertex(buffer, matrix, cam, a, color);
        vertex(buffer, matrix, cam, b, color);
        vertex(buffer, matrix, cam, c, color);
        vertex(buffer, matrix, cam, d, color);
    }

    @Override
    public void text(Text text, Vec3d pos, float scale, int color, boolean shadow, boolean throughWalls) {
        if (!inWorld || text == null || MC.textRenderer == null) {
            return;
        }
        Matrix4f matrix = billboardMatrix(pos, scale);
        TextRenderer.TextLayerType layer = throughWalls
                ? TextRenderer.TextLayerType.SEE_THROUGH
                : TextRenderer.TextLayerType.NORMAL;
        float width = MC.textRenderer.getWidth(text);
        MC.textRenderer.draw(text, -width / 2.0F, 0.0F, color, shadow, matrix, consumers, layer, 0, FULL_LIGHT);
    }

    @Override
    public float textWidth(Text text, float scale) {
        if (text == null || MC.textRenderer == null) {
            return 0.0F;
        }
        return MC.textRenderer.getWidth(text) * 0.025F * scale;
    }

    @Override
    public void billboardImage(Identifier texture, Vec3d center, float width, float height) {
        if (!inWorld || texture == null) {
            return;
        }
        Matrix4f matrix = worldBillboard(center);
        VertexConsumer buffer = consumers.getBuffer(RenderLayers.entityTranslucent(texture, false));
        float halfW = width / 2.0F;
        float halfH = height / 2.0F;
        // entity* 层的顶点格式是 POSITION_TEXTURE_COLOR_NORMAL，overlay/light/normal 必须齐全，
        // 少一个就会在 flush 时抛 "Missing elements in vertex: UV1, UV2, Normal"
        buffer.vertex(matrix, -halfW, -halfH, 0.0F).texture(0.0F, 0.0F).color(0xFFFFFFFF)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0.0F, 0.0F, 1.0F);
        buffer.vertex(matrix, -halfW, halfH, 0.0F).texture(0.0F, 1.0F).color(0xFFFFFFFF)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0.0F, 0.0F, 1.0F);
        buffer.vertex(matrix, halfW, halfH, 0.0F).texture(1.0F, 1.0F).color(0xFFFFFFFF)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0.0F, 0.0F, 1.0F);
        buffer.vertex(matrix, halfW, -halfH, 0.0F).texture(1.0F, 0.0F).color(0xFFFFFFFF)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(0.0F, 0.0F, 1.0F);
    }

    @Override
    public void item(ItemStack stack, Vec3d pos, float scale) {
        if (!inWorld || stack == null || stack.isEmpty() || MC.world == null) {
            return;
        }
        MatrixStack stackMatrix = new MatrixStack();
        Vec3d cam = cameraPos();
        stackMatrix.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z));
        stackMatrix.multiply(camera.getRotation());
        // 物品模型本来就是 Y 向上，**不能**像文字那样翻 Y，否则图标会上下颠倒
        stackMatrix.scale(scale, scale, scale);

        ItemRenderState state = new ItemRenderState();
        MC.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.FIXED, MC.world, null, 0);
        state.render(stackMatrix, ITEM_QUEUE, 0x00F000F0, OverlayTexture.DEFAULT_UV, 0);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            OutlineVertexConsumerProvider outline = MC.getBufferBuilders().getOutlineVertexConsumers();
            for (BatchingRenderCommandQueue queue : ITEM_QUEUE.getBatchingQueues().values()) {
                ITEM_RENDERER.render(queue, immediate, outline);
            }
        }
        ITEM_QUEUE.clear();
    }

    @Override
    public Vec3d billboardOffset(Vec3d origin, float right, float up) {
        return billboardOffset(origin, right, up, 0.0F);
    }

    @Override
    public Vec3d billboardOffset(Vec3d origin, float right, float up, float forward) {
        if (camera == null) {
            return origin;
        }
        Vector3f rightAxis = camera.getRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f upAxis = camera.getRotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f outAxis = camera.getRotation().transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return origin.add(
                rightAxis.x * right + upAxis.x * up + outAxis.x * forward,
                rightAxis.y * right + upAxis.y * up + outAxis.y * forward,
                rightAxis.z * right + upAxis.z * up + outAxis.z * forward);
    }
    @Override
    public void billboardRect(Vec3d center, float width, float height, int color) {
        if (!inWorld) {
            return;
        }
        Matrix4f matrix = worldBillboard(center);
        VertexConsumer buffer = consumers.getBuffer(quads());
        float halfW = width / 2.0F;
        float halfH = height / 2.0F;
        buffer.vertex(matrix, -halfW, -halfH, 0.0F).color(color);
        buffer.vertex(matrix, -halfW, halfH, 0.0F).color(color);
        buffer.vertex(matrix, halfW, halfH, 0.0F).color(color);
        buffer.vertex(matrix, halfW, -halfH, 0.0F).color(color);
    }

    @Override
    public Vec3d project(Vec3d world) {
        if (camera == null || projection == null) {
            return null;
        }
        Vec3d cam = cameraPos();
        Vector4f point = new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y),
                (float) (world.z - cam.z), 1.0F);
        new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf())).transform(point);
        projection.transform(point);
        if (point.w <= 0.05F) {
            return null;
        }
        return new Vec3d(point.x / point.w * 0.5D + 0.5D, 1.0D - (point.y / point.w * 0.5D + 0.5D), 0.0D);
    }

    /**
     * 让一块平面正对相机的矩阵：{@code T · C · S}。
     *
     * <p>关键点：{@code camera.getRotation()} 是**相机→世界**（view 矩阵用的是它的共轭），
     * 所以这里要用它本身来抵消 view 的旋转；用共轭会得到"多转一次"的结果，
     * 表现就是文字镜像、整体歪掉。
     *
     * <p>顺序：先缩放（相机局部空间）→ 转到世界朝向 → 按世界坐标平移。
     */
    private Matrix4f billboardMatrix(Vec3d pos, float scale) {
        Vec3d cam = cameraPos();
        float s = 0.025F * scale;
        Matrix4f matrix = new Matrix4f().translation(
                (float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z));
        matrix.rotate(camera.getRotation());
        matrix.scale(s, -s, s);
        return matrix;
    }

    /**
     * 正对相机的矩阵，**单位就是方块**（不带文字那套 0.025 缩放）。
     *
     * <p>底板、贴图图标用这个：传进来的宽高直接就是"几格"。
     * 之前它们误用了文字矩阵，导致 1.75 格被又乘了一次 0.025 → 只有 0.04 格，
     * 所以图标小到看不见。
     */
    private Matrix4f worldBillboard(Vec3d pos) {
        Vec3d cam = cameraPos();
        Matrix4f matrix = new Matrix4f().translation(
                (float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z));
        matrix.rotate(camera.getRotation());
        matrix.scale(1.0F, -1.0F, 1.0F);
        return matrix;
    }

    private void sides(Box box, int color) {
        VertexConsumer buffer = consumers.getBuffer(quads());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cam = cameraPos();
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        // 六个面
        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z1), color);

        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z2), color);

        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z1), color);

        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z2), color);

        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y2, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x1, y1, z2), color);

        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z1), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y1, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z2), color);
        vertex(buffer, matrix, cam, new Vec3d(x2, y2, z1), color);
    }

    private void edges(Box box, int color) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        line(new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), color, 1.5F);
        line(new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), color, 1.5F);
        line(new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), color, 1.5F);
        line(new Vec3d(x1, y1, z2), new Vec3d(x1, y1, z1), color, 1.5F);

        line(new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), color, 1.5F);
        line(new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), color, 1.5F);
        line(new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), color, 1.5F);
        line(new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), color, 1.5F);

        line(new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), color, 1.5F);
        line(new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), color, 1.5F);
        line(new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), color, 1.5F);
        line(new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), color, 1.5F);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3d cam, Vec3d point, int color) {
        buffer.vertex(matrix, (float) (point.x - cam.x), (float) (point.y - cam.y),
                (float) (point.z - cam.z)).color(color);
    }
}
