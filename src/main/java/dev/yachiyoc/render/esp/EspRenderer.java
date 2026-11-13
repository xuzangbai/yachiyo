package dev.yachiyoc.render.esp;

import dev.yachiyoc.render.Render;
import dev.yachiyoc.render.RenderApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * 目标渲染（ESP）门面 —— 业务侧只认这一层。
 *
 * <pre>
 * EspRenderer.render(mode, box, style);   // box 用插值过的箱子，别用会跳的原位置
 * </pre>
 *
 * <p>为什么参数是 {@link Box} 而不是 {@code Entity}：方块 ESP、水晶光环、AoE 预警圈以后都要复用，
 * 它们手里只有坐标。谁想画就自己把箱子递进来。
 *
 * <p>时间轴全部用 {@link System#nanoTime()} 现算，不存状态：进出世界、切目标都不会残留动画。
 */
public final class EspRenderer {
    /** 4 格处一个像素大约对应多少格（用于把"像素粗细"换算成世界宽度）。 */
    private static final float PIXEL = 0.005F;

    /** 雷达外框的亮度（相对 alpha），给辐条一个落脚点。 */
    private static final float RIM_ALPHA = 0.45F;
    /** 圈的两级手绘抖动幅度（相对半径）：低频 3 波 + 高频 5 波，每圈相位不同。 */
    private static final float WOBBLE_LOW = 0.055F;
    private static final float WOBBLE_HIGH = 0.032F;
    /** 辐条角度抖动（度）。 */
    private static final float SPOKE_ANGLE_JITTER = 4.0F;
    /** 辐条沿长度方向采样多少段。 */
    private static final int SPOKE_STEPS = 16;
    /** 单帧最多画多少段，防止站在方块缝里时爆量。 */
    private static final int SONAR_BUDGET = 3000;
    /** 贴面绘制时沿法线抬起的距离，避免和方块/实体本体打架。 */
    private static final float SURFACE_LIFT = 0.004F;
    /** 方块的"面片"按平面归并后，最多处理多少个平面。 */
    private static final int MAX_PLANES = 24;
    /** 球心到方块中心的粗筛：超过 {@code radius + 这个值} 直接跳过（方块对角线一半不到 1.8）。 */
    private static final double BLOCK_PRUNE = 1.8D;

    /** 光环上下往返一个周期的秒数。 */
    private static final float HALO_PERIOD = 1.9F;
    /** 相邻两层拖尾的时间差（秒）。 */
    private static final float HALO_LAG = 0.035F;
    private static final int HALO_SEG = 36;

    private static final Vec3d AXIS_X = new Vec3d(1.0D, 0.0D, 0.0D);
    private static final Vec3d AXIS_Y = new Vec3d(0.0D, 1.0D, 0.0D);
    private static final Vec3d AXIS_Z = new Vec3d(0.0D, 0.0D, 1.0D);
    private static final double TAU = Math.PI * 2.0;

    private EspRenderer() {
    }

    /** 一帧入口。{@code box} 必须是插值后的箱子。 */
    public static void render(EspMode mode, Box box, EspStyle style) {
        RenderApi api = Render.api();
        if (mode == null || style == null || box == null || !api.inWorld()) {
            return;
        }
        long now = System.nanoTime();
        if (mode == EspMode.HALO) {
            halo(api, box, style, now);
        } else {
            sonar(api, box, style, now);
        }
    }

    // ---------------------------------------------------------------- 声纳

    /**
     * 声纳：贴在表面上的<b>雷达蛛网</b>——同心圈 + 从中心发散、冲出最外圈的辐条，圈还带手绘式抖动。
     *
     * <ul>
     *   <li>网格常驻（低亮度），波前扫过的地方亮起来再淡掉，所以是"网一直在、波在上面跑"；</li>
     *   <li>圈：{@code k × 环距} 的半径上画一圈，半径按 {@code sin(3θ)+sin(5θ)} 抖动，每圈相位不同；</li>
     *   <li>辐条：{@code 360/辐条数} 一根，各自角度抖 ±4°、长度冲出最外圈 6%~38%，所以每条都不一样长；</li>
     *   <li>全部落在<b>碰撞箱表面和朝空气的方块面</b>上（球面与这些平面的交），地面上就是一张
     *       以"中心投影点"为圆心的蛛网。</li>
     * </ul>
     */
    private static void sonar(RenderApi api, Box box, EspStyle style, long now) {
        float maxRadius = Math.max(0.3F, style.sonarRadius());
        float spacing = Math.max(0.1F, style.sonarSpacing());
        int spokes = Math.max(3, Math.min(24, style.sonarSpokes()));
        float period = Math.max(0.4F, style.sonarPeriod());
        Vec3d center = box.getCenter();
        float width = width(style, center);
        int baseAlpha = (style.sonarColor() >>> 24) & 0xFF;
        World world = MinecraftClient.getInstance().world;

        // flow = "圈跑完全程"的进度。每一圈的半径都由它<b>实时算</b>出来，所以圈是真的在往外走，
        // 不是原地淡入淡出的一张图：同一时刻有（最大半径 / 环距）道圈正在飞，中心不断补新的。
        float cycleNanos = period * 1.0E9F;
        float flow = (now % (long) cycleNanos) / cycleNanos;

        int budget = SONAR_BUDGET;
        budget -= boxWeb(api, box, center, maxRadius, spacing, spokes, flow,
                style.sonarColor(), baseAlpha, width, budget);
        if (world != null && budget > 0) {
            Map<Long, SurfacePlane> planes = collectPlanes(world, center, maxRadius);
            budget -= blockWeb(api, planes, center, maxRadius, spacing, spokes, flow,
                    style.sonarColor(), baseAlpha, width, budget);
        }
    }

    /** 碰撞箱 6 个面上的蛛网。 */
    private static int boxWeb(RenderApi api, Box box, Vec3d center, double maxRadius, double spacing, int spokes,
                              float flow, int color, int baseAlpha, float width, int budget) {
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        double d = box.maxZ - box.minZ;
        Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d[] normals = {new Vec3d(-1.0D, 0.0D, 0.0D), AXIS_X, new Vec3d(0.0D, -1.0D, 0.0D),
                AXIS_Y, new Vec3d(0.0D, 0.0D, -1.0D), AXIS_Z};
        Vec3d[] origins = {min, new Vec3d(box.maxX, box.minY, box.minZ), min,
                new Vec3d(box.minX, box.maxY, box.minZ), min, new Vec3d(box.minX, box.minY, box.maxZ)};
        Vec3d[] uAxes = {AXIS_Y, AXIS_Y, AXIS_X, AXIS_X, AXIS_X, AXIS_X};
        double[] uLens = {h, h, w, w, w, w};
        Vec3d[] vAxes = {AXIS_Z, AXIS_Z, AXIS_Z, AXIS_Z, AXIS_Y, AXIS_Y};
        double[] vLens = {d, d, d, d, h, h};

        int used = 0;
        for (int i = 0; i < 6 && used < budget; i++) {
            used += drawWeb(api, new RectSurface(center, normals[i], origins[i], uAxes[i], uLens[i],
                            vAxes[i], vLens[i], i),
                    maxRadius, spacing, spokes, flow, color, baseAlpha, width, budget - used);
        }
        return used;
    }

    /** 方块平面上的蛛网（只在"有朝空气的面"的格子里画）。 */
    private static int blockWeb(RenderApi api, Map<Long, SurfacePlane> planes, Vec3d center, double maxRadius,
                                double spacing, int spokes, float flow, int color,
                                int baseAlpha, float width, int budget) {
        int used = 0;
        int planesDrawn = 0;
        for (SurfacePlane plane : planes.values()) {
            if (planesDrawn++ >= MAX_PLANES || used >= budget) {
                break;
            }
            used += drawWeb(api, new TileSurface(center, plane),
                    maxRadius, spacing, spokes, flow, color, baseAlpha, width, budget - used);
        }
        return used;
    }

    /**
     * 在一张平面（碰撞箱的某个面 / 地形的某个平面）上铺蛛网。
     *
     * <p>圈是<b>在动</b>的几何，不是一张淡入淡出的贴图：第 i 圈的半径 = {@code 相位 × 最大半径}，
     * 相位 = {@code flow + i/圈数}，flow 每周期从 0 走到 1 —— 所以同一时刻有 {@code 圈数} 道圈
     * 正在往外跑，间距固定，到边缘淡出，中心不断补新的。球半径 R 落到平面上可见圆半径是 {@code √(R² − d²)}。
     *
     * <p>辐条是静止的（它是圈跑的"跑道"），末端正好停在雷达外框上。
     */
    private static int drawWeb(RenderApi api, WebSurface surface, double maxRadius, double spacing, int spokes,
                               float flow, int color, int baseAlpha, float width, int budget) {
        double d = surface.planeDistance();
        if (d >= maxRadius) {
            return 0;
        }
        double outer = Math.sqrt(maxRadius * maxRadius - d * d);
        if (outer < 0.04D) {
            return 0;
        }
        Vec3d normal = surface.normal();
        Vec3d lift = normal.multiply(SURFACE_LIFT);
        double cu = surface.centerU();
        double cv = surface.centerV();
        int seed = surface.seed();
        int used = 0;

        int samples = (int) Math.max(24, Math.min(96, outer * 32.0D));
        int rings = Math.max(2, (int) Math.floor(maxRadius / spacing));

        // ---- 流动的圈：半径 = 相位 × 最大半径，相位每周期推进一圈，所以圈是真的在往外跑
        for (int i = 0; i < rings && used < budget; i++) {
            double phase = flow + (double) i / rings;
            phase -= Math.floor(phase);
            int alpha = (int) (baseAlpha * ringFade(phase));
            if (alpha < 4) {
                continue;
            }
            int ringColor = withAlpha(color, alpha);
            Vec3d prev = null;
            for (int s = 0; s <= samples; s++) {
                double angle = TAU * s / samples;
                double radius3 = phase * maxRadius * (1.0D + wobbleAt(angle, i, seed));
                if (radius3 <= d) {
                    prev = null;
                    continue;
                }
                double rc = Math.sqrt(radius3 * radius3 - d * d);
                double pu = cu + rc * Math.cos(angle);
                double pv = cv + rc * Math.sin(angle);
                Vec3d point = surface.contains(pu, pv) ? surface.world(pu, pv).add(lift) : null;
                if (prev != null && point != null && used < budget) {
                    api.ribbonOnPlane(prev, point, width, normal, ringColor);
                    used++;
                }
                prev = point;
            }
        }

        // ---- 雷达外框（静止）：给辐条一个落脚点
        int rimAlpha = (int) (baseAlpha * RIM_ALPHA);
        if (rimAlpha >= 4) {
            int rimColor = withAlpha(color, rimAlpha);
            Vec3d prev = null;
            for (int s = 0; s <= samples; s++) {
                double angle = TAU * s / samples;
                double pu = cu + outer * Math.cos(angle);
                double pv = cv + outer * Math.sin(angle);
                Vec3d point = surface.contains(pu, pv) ? surface.world(pu, pv).add(lift) : null;
                if (prev != null && point != null && used < budget) {
                    api.ribbonOnPlane(prev, point, width, normal, rimColor);
                    used++;
                }
                prev = point;
            }
        }

        // ---- 辐条（静止的跑道）：从投影点发散，末端正好停在外框上
        for (int i = 0; i < spokes && used < budget; i++) {
            double jitter = (hash01(i, 5, seed) - 0.5D) * 2.0D * Math.toRadians(SPOKE_ANGLE_JITTER);
            double angle = TAU * i / spokes + jitter;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            Vec3d prev = null;
            for (int j = 0; j <= SPOKE_STEPS; j++) {
                double len = outer * j / SPOKE_STEPS;
                double pu = cu + dx * len;
                double pv = cv + dy * len;
                Vec3d point = surface.contains(pu, pv) ? surface.world(pu, pv).add(lift) : null;
                if (prev != null && point != null && used < budget) {
                    // 越往外越淡一点，和外框接得自然
                    int alpha = (int) (baseAlpha * 0.75D * (1.0D - 0.35D * len / outer));
                    api.ribbonOnPlane(prev, point, width, normal, withAlpha(color, alpha));
                    used++;
                }
                prev = point;
            }
        }
        return used;
    }

    /** 第 index 条圈在给定角度上的抖动系数（同一条圈在飞行途中形状不变）。 */
    private static double wobbleAt(double angle, int index, int seed) {
        return WOBBLE_LOW * Math.sin(3.0D * angle + hash01(index, 11, seed) * TAU)
                + WOBBLE_HIGH * Math.sin(5.0D * angle + hash01(index, 23, seed) * TAU);
    }

    /**
     * 单条圈的亮度：出生时淡入（避免从中心"啪"地冒出来），跑到边缘淡出，
     * 中间保持满亮——所以任何时候都有一队圈在飞，中心不断补新的。
     */
    private static double ringFade(double phase) {
        double in = Math.min(1.0D, phase / 0.10D);
        double out = Math.min(1.0D, (1.0D - phase) / 0.30D);
        double a = Math.min(in, out);
        return a * a * (3.0D - 2.0D * a);
    }

    // ---- 两种平面

    /** 可以铺蛛网的一张平面：只关心"点在不在面片上"和"平面坐标 → 世界坐标"。 */
    private abstract static class WebSurface {
        abstract boolean contains(double u, double v);

        abstract Vec3d world(double u, double v);

        abstract Vec3d normal();

        abstract double planeDistance();

        abstract double centerU();

        abstract double centerV();

        abstract int seed();
    }

    /** 碰撞箱的一个面（矩形）。 */
    private static final class RectSurface extends WebSurface {
        private final Vec3d normal;
        private final Vec3d origin;
        private final Vec3d uAxis;
        private final double uLen;
        private final Vec3d vAxis;
        private final double vLen;
        private final double planeDistance;
        private final double centerU;
        private final double centerV;
        private final int seed;

        private RectSurface(Vec3d center, Vec3d normal, Vec3d origin, Vec3d uAxis, double uLen,
                            Vec3d vAxis, double vLen, int seed) {
            this.normal = normal;
            this.origin = origin;
            this.uAxis = uAxis;
            this.uLen = uLen;
            this.vAxis = vAxis;
            this.vLen = vLen;
            this.seed = seed;
            Vec3d rel = center.subtract(origin);
            this.planeDistance = Math.abs(rel.dotProduct(normal));
            Vec3d onPlane = center.subtract(normal.multiply(rel.dotProduct(normal)));
            Vec3d local = onPlane.subtract(origin);
            this.centerU = local.dotProduct(uAxis);
            this.centerV = local.dotProduct(vAxis);
        }

        @Override
        boolean contains(double u, double v) {
            return u >= 0.0D && u <= uLen && v >= 0.0D && v <= vLen;
        }

        @Override
        Vec3d world(double u, double v) {
            return origin.add(uAxis.multiply(u)).add(vAxis.multiply(v));
        }

        @Override
        Vec3d normal() {
            return normal;
        }

        @Override
        double planeDistance() {
            return planeDistance;
        }

        @Override
        double centerU() {
            return centerU;
        }

        @Override
        double centerV() {
            return centerV;
        }

        @Override
        int seed() {
            return seed;
        }
    }

    /** 地形的一个平面（由若干 1×1 的"朝空气面"格子拼成）。 */
    private static final class TileSurface extends WebSurface {
        private final SurfacePlane plane;
        private final Vec3d normal;
        private final double planeDistance;
        private final double centerU;
        private final double centerV;

        private TileSurface(Vec3d center, SurfacePlane plane) {
            this.plane = plane;
            this.normal = normalOf(plane);
            double axis = switch (plane.axis) {
                case 0 -> center.x;
                case 1 -> center.y;
                default -> center.z;
            };
            this.planeDistance = Math.abs(axis - plane.coord);
            this.centerU = switch (plane.axis) {
                case 0 -> center.z;
                case 1 -> center.x;
                default -> center.x;
            };
            this.centerV = switch (plane.axis) {
                case 0 -> center.y;
                case 1 -> center.z;
                default -> center.y;
            };
        }

        @Override
        boolean contains(double u, double v) {
            return plane.tiles.contains(tileKey((int) Math.floor(u), (int) Math.floor(v)));
        }

        @Override
        Vec3d world(double u, double v) {
            return toWorld(plane.axis, plane.coord, u, v);
        }

        @Override
        Vec3d normal() {
            return normal;
        }

        @Override
        double planeDistance() {
            return planeDistance;
        }

        @Override
        double centerU() {
            return centerU;
        }

        @Override
        double centerV() {
            return centerV;
        }

        @Override
        int seed() {
            return plane.axis * 7 + plane.sign + 3;
        }
    }

    /** 收集周围方块上"朝空气"的面，按所在平面归并。 */
    private static Map<Long, SurfacePlane> collectPlanes(World world, Vec3d center, double radius) {
        Map<Long, SurfacePlane> planes = new HashMap<>();
        int bound = (int) Math.ceil(radius) + 1;
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        double prune = (radius + BLOCK_PRUNE) * (radius + BLOCK_PRUNE);

        for (int x = cx - bound; x <= cx + bound; x++) {
            for (int y = cy - bound; y <= cy + bound; y++) {
                for (int z = cz - bound; z <= cz + bound; z++) {
                    double dx = x + 0.5D - center.x;
                    double dy = y + 0.5D - center.y;
                    double dz = z + 0.5D - center.z;
                    if (dx * dx + dy * dy + dz * dz > prune) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (world.getBlockState(pos).isAir()) {
                        continue;
                    }
                    // 只保留朝空气的一侧：实心面被邻居挡住，画了也看不见
                    if (world.getBlockState(pos.west()).isAir()) {
                        addTile(planes, 0, -1, x, z, y);
                    }
                    if (world.getBlockState(pos.east()).isAir()) {
                        addTile(planes, 0, 1, x + 1, z, y);
                    }
                    if (world.getBlockState(pos.down()).isAir()) {
                        addTile(planes, 1, -1, y, x, z);
                    }
                    if (world.getBlockState(pos.up()).isAir()) {
                        addTile(planes, 1, 1, y + 1, x, z);
                    }
                    if (world.getBlockState(pos.north()).isAir()) {
                        addTile(planes, 2, -1, z, x, y);
                    }
                    if (world.getBlockState(pos.south()).isAir()) {
                        addTile(planes, 2, 1, z + 1, x, y);
                    }
                }
            }
        }
        return planes;
    }

    private static Vec3d normalOf(SurfacePlane plane) {
        return switch (plane.axis) {
            case 0 -> new Vec3d(plane.sign, 0.0D, 0.0D);
            case 1 -> new Vec3d(0.0D, plane.sign, 0.0D);
            default -> new Vec3d(0.0D, 0.0D, plane.sign);
        };
    }

    private static Vec3d toWorld(int axis, double coord, double pu, double pv) {
        return switch (axis) {
            case 0 -> new Vec3d(coord, pv, pu);     // u = Z，v = Y
            case 1 -> new Vec3d(pu, coord, pv);     // u = X，v = Z
            default -> new Vec3d(pu, pv, coord);    // u = X，v = Y
        };
    }

    private static void addTile(Map<Long, SurfacePlane> planes, int axis, int sign, int coord, int uTile, int vTile) {
        long key = (long) axis << 34 | (long) (sign + 1) << 32 | (coord & 0xFFFFFFFFL);
        planes.computeIfAbsent(key, ignored -> new SurfacePlane(axis, sign, coord)).tiles.add(tileKey(uTile, vTile));
    }

    private static long tileKey(int u, int v) {
        return (long) u << 32 ^ (v & 0xFFFFFFFFL);
    }

    /** 每个格子一个稳定的 0..1 哈希，用来错开抖动相位。 */
    private static float hash01(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    // ---------------------------------------------------------------- 光环

    /**
     * 薰衣草光环：在目标身上做正弦上下往复，{@code haloTail} 层越来越淡的旧位置当拖尾。
     *
     * <p>拖尾不存历史：第 k 层就是"t − k·Δ 时刻的位置"，所以上下两个方向都有尾巴，
     * 也不会因为掉帧/切目标而粘一坨东西。
     */
    private static void halo(RenderApi api, Box box, EspStyle style, long now) {
        Vec3d center = box.getCenter();
        float halfHeight = (float) (box.maxY - box.minY) * 0.5F;
        float amplitude = Math.max(0.0F, Math.min(1.0F, style.haloAmplitude())) * halfHeight;
        float radius = (float) Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * 0.5F + 0.16F;
        int tail = Math.max(0, style.haloTail());
        float width = width(style, center);

        float cycleNanos = HALO_PERIOD * 1.0E9F;
        float t = (now % (long) cycleNanos) / cycleNanos;
        float lagPhase = HALO_LAG / HALO_PERIOD;

        for (int k = tail; k >= 0; k--) {
            float phase = t - k * lagPhase;
            float y = (float) (center.y + Math.sin(phase * Math.PI * 2.0) * amplitude);
            float fade = 1.0F - (float) k / (tail + 1.0F);
            int alpha = (int) (((style.haloColor() >>> 24) & 0xFF) * fade * fade);
            if (alpha < 3) {
                continue;
            }
            int color = withAlpha(style.haloColor(), alpha);
            Vec3d ringCenter = new Vec3d(center.x, y, center.z);
            ring(api, ringCenter, radius, color, k == 0 ? width : width * 0.75F, HALO_SEG);
            if (k == 0) {
                // 主环外侧再套一圈淡的，做出"发光"感（固定 0.05 格外扩，跟着线宽走会看不出层次）
                ring(api, ringCenter, radius + 0.05F,
                        withAlpha(color, alpha / 3), width * 0.8F, HALO_SEG);
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 水平圆环（一圈面向相机的四边形带子）。 */
    private static void ring(RenderApi api, Vec3d center, float radius, int color, float width, int segments) {
        if (radius < 0.02F || segments < 3) {
            return;
        }
        Vec3d prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = TAU * i / segments;
            Vec3d point = new Vec3d(
                    center.x + radius * Math.cos(angle),
                    center.y,
                    center.z + radius * Math.sin(angle));
            if (prev != null) {
                api.ribbon(prev, point, width, color);
            }
            prev = point;
        }
    }

    /** 把"像素粗细"按到相机的距离换算成世界宽度：近处 1x，远处逐渐加粗，上限 8x。 */
    private static float width(EspStyle style, Vec3d center) {
        Vec3d camera = Render.api().cameraPos();
        double distance = camera.distanceTo(center);
        float scale = (float) Math.max(1.0D, Math.min(8.0D, distance / 4.0D));
        return Math.max(0.002F, style.thickness() * PIXEL * scale);
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** 一个平面（法线轴 + 该轴坐标）上，哪些 1×1 格子有朝向空气的面。 */
    private static final class SurfacePlane {
        private final int axis;
        private final int sign;
        private final double coord;
        private final java.util.Set<Long> tiles = new java.util.HashSet<>();

        private SurfacePlane(int axis, int sign, int coord) {
            this.axis = axis;
            this.sign = sign;
            this.coord = coord;
        }
    }
}
