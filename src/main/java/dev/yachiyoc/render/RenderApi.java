package dev.yachiyoc.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 世界（3D）渲染接口，**业务代码只认这一层**。
 *
 * <p>存在的唯一目的：把「会随版本变的东西」全部关在 {@code render.v1_21_11} 里。
 * 这个接口的参数只用 {@link Box} / {@link Vec3d} / {@link Text} / int / float 这类跨版本稳定的类型，
 * 绝不出现 {@code RenderLayer}、{@code RenderPipelines}、{@code VertexConsumer}、{@code MatrixStack}。
 *
 * <p>升级 MC 时要做的事：新建 {@code render.v1_22_x.RenderApiImpl} 照检查单调通，业务侧零改动。
 */
public interface RenderApi {
    /** 一帧开始：由世界渲染钩子调用，把当前帧的相机与缓冲交给实现。 */
    void beginWorld(MatrixStack matrices, VertexConsumerProvider consumers, Matrix4f projection,
                    Matrix4f positionMatrix, float tickDelta);

    /** 一帧结束：把攒下来的顶点提交掉。 */
    void endWorld();

    /** 画一个方框。 */
    void box(Box box, int sideColor, int lineColor, ShapeMode mode);

    /** 画一条线。 */
    void line(Vec3d from, Vec3d to, int color, float width);

    /** 画一个四边形（四个角按顺序给）。 */
    void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color);

    /**
     * 在世界上贴一段正对镜头的文字。
     *
     * @param pos        文字中心的世界坐标
     * @param scale      缩放（1.0 ≈ 原版名牌大小）
     * @param color      ARGB
     * @param shadow     要不要阴影
     * @param throughWalls 关掉深度测试（穿墙可见）
     */
    void text(Text text, Vec3d pos, float scale, int color, boolean shadow, boolean throughWalls);

    /** 文字在世界里的宽度（已按 scale 换算，单位是方块）。 */
    float textWidth(Text text, float scale);

    /** 在世界上贴一块正对镜头的小方片（血条、底板都用它）。 */
    void billboardRect(Vec3d center, float width, float height, int color);

    /** 在世界上画一个物品（3D 模型，和物品栏里一样）。{@code scale} 单位是方块。 */
    void item(net.minecraft.item.ItemStack stack, Vec3d pos, float scale);

    /** 把"相机右/上方向"的偏移换算成世界坐标，用来把多个 billboard 排成一行。 */
    Vec3d billboardOffset(Vec3d origin, float right, float up);

    /** 同上，额外带一个朝向相机的前向偏移：给叠在一起的 billboard 分层，避免深度打架。 */
    Vec3d billboardOffset(Vec3d origin, float right, float up, float forward);
    /** 在世界上贴一张正对镜头的贴图（药水图标之类）。 */
    void billboardImage(net.minecraft.util.Identifier texture, Vec3d center, float width, float height);

    /** 世界坐标 → 屏幕坐标（GUI 坐标）。点在相机背后时返回 {@code null}。 */
    Vec3d project(Vec3d world);

    /** 本帧的相机位置。 */
    Vec3d cameraPos();

    /** 本帧的部分 tick（插值用）。 */
    float tickDelta();

    /** 本帧是否处在世界渲染阶段。 */
    boolean inWorld();
}
