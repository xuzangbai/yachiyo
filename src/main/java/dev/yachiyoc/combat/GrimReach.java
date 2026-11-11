package dev.yachiyoc.combat;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Grim {@code Reach} 的客户端等价实现（1.21.11 / Grim 2.3.74 ~ 现行 master 口径）。
 *
 * <p><b>Grim 到底量什么</b>
 * <pre>
 * min over {当前朝向, (lastYaw, pitch), (lastYaw, lastPitch)} × {1.62, 1.27, 0.4}
 *     of  |眼睛(脚 + 眼高) → 视线射线射入箱子那一点|
 * 打不进箱子 → 不算距离，走独立的 {@code Hitboxes}；最小入射距离 &gt; 3.0 才是 {@code Reach}。
 * </pre>
 *
 * <p><b>高低差为什么必须逐眼高分别算方向</b>
 * 三套候选眼高是 Grim 给"服务端不确定你什么姿势"留的容错，它<b>逐个眼高单独做射线</b>再取最小。
 * 所以你站在高处、目标在低处时：
 * <pre>
 * 目标箱子 y∈[0,1.8]、水平 2.0 格、你脚底 y=3
 *   1.62 眼(4.62) → 最近点 dy=2.82 → 距离 3.46  ← 只算这一只，等于白丢 0.5 格
 *   0.40 眼(3.40) → 最近点 dy=1.60 → 距离 2.56  ← Grim 认的是这个
 * </pre>
 * 方向也是同理：从 1.62 眼算出来的角度，套到 0.4 眼上会从箱子上方擦过去（打不进 → 该候选作废），
 * 于是 Grime 那边只剩 3.46 这一份。{@link #bestAim} 因此对每只候选眼各算一套方向，
 * 再按"入射距离最短"选一组 —— 这既是 Grim 自己的最小值，也是不 TP 能拿到的合法上限。
 */
public final class GrimReach {
    /** {@code Reach.threshold} 默认值。 */
    public static final double THRESHOLD = 0.0005D;

    /** {@code GrimPlayer#getMovementThreshold()}：1.18.2 及以上客户端是 0.0002。 */
    public static final double MOVEMENT_THRESHOLD = 0.0002D;

    /** Grim 测距前把箱子往四周外扩的量。 */
    public static final double BOX_EXPAND = THRESHOLD + MOVEMENT_THRESHOLD;

    /** 射线长度上限：Grim 用 {@code maxReach + 3}，默认 3.0 + 3.0。 */
    public static final double RAY_LENGTH = 6.0D;

    /**
     * 出手门限的余量。
     *
     * <p>只用来吸收 yaw/pitch 的浮点量化（方向 → 角度 → 方向会差一点点），<b>不是</b>"安全带"：
     * SLM 的 {@code combat.att-range} 默认 0，也就是它按 3.0 整打，一点不扣。
     * 之前用 0.0918 当门限扣减，等于白白少打 0.09 格，高低差时更明显。
     */
    public static final double MARGIN = 0.01D;

    /** {@code GrimPlayer.possibleEyeHeights[0]}：站立 / 潜行 / 滑翔三套候选眼高。 */
    private static final double[] EYES = {1.62D, 1.27D, 0.4D};

    private static final double EPS = 1.0E-8D;

    private GrimReach() {
    }

    /**
     * 一组瞄准方案：方向、对应的候选眼高、以及按 Grim 口径量出来的入射距离。
     */
    public record Aim(Vec3d dir, double eyeHeight, double distance) {
    }

    /** 与服务端同一套 yaw/pitch → 单位视线向量（{@code ReachUtils.getLook}，1.13+ 分支）。 */
    public static Vec3d look(float yaw, float pitch) {
        float yawRadians = (float) Math.toRadians(-yaw);
        float pitchRadians = (float) Math.toRadians(pitch);
        float pitchCos = MathHelper.cos(pitchRadians);
        return new Vec3d(MathHelper.sin(yawRadians) * pitchCos,
                -MathHelper.sin(pitchRadians),
                MathHelper.cos(yawRadians) * pitchCos);
    }

    /**
     * 射线与 AABB 的入射距离（等价 Grim 的 {@code ReachUtils.calculateIntercept} 取最近交点后量距离）。
     *
     * @return 入射距离；{@code maxLength} 内打不到箱子时返回 {@link Double#NaN}
     */
    public static double entry(Vec3d eye, Vec3d dir, Box box, double maxLength) {
        double tMin = 0.0D;
        double tMax = maxLength;

        double[] origin = {eye.x, eye.y, eye.z};
        double[] delta = {dir.x, dir.y, dir.z};
        double[] low = {box.minX, box.minY, box.minZ};
        double[] high = {box.maxX, box.maxY, box.maxZ};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-12D) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                    return Double.NaN;
                }
                continue;
            }
            double t1 = (low[axis] - origin[axis]) / delta[axis];
            double t2 = (high[axis] - origin[axis]) / delta[axis];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.NaN;
            }
        }
        return tMin;
    }

    /** 眼睛到箱子最近点（{@code BoxUtils.jy} 同款）。 */
    public static Vec3d closestPoint(Vec3d eye, Box box) {
        return new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ));
    }

    /**
     * 从某一只眼睛出发选方向：先试"指向箱子中心"（要求能在 {@code maxLength} 内打进箱子），
     * 不行再退到"指向箱子最近点"（射线终点正好落在箱面上，入射距离 == 最近点距离，不存在放大）。
     */
    public static Vec3d directionFor(Vec3d eye, Box box, double maxLength) {
        Vec3d toCenter = box.getCenter().subtract(eye);
        if (toCenter.lengthSquared() > EPS) {
            Vec3d dir = toCenter.normalize();
            if (!Double.isNaN(entry(eye, dir, box, maxLength))) {
                return dir;
            }
        }
        Vec3d toClosest = closestPoint(eye, box).subtract(eye);
        if (toClosest.lengthSquared() > EPS) {
            return toClosest.normalize();
        }
        return toCenter.lengthSquared() > EPS ? toCenter.normalize() : new Vec3d(0.0D, 0.0D, 1.0D);
    }

    /**
     * 逐候选眼高算方向 / 距离，取入射距离最短的那一组。
     *
     * @param feet 脚底位置（服务端记住的那个）
     * @param box  量距离用的箱子，建议用 {@link #withHistory} 把上一 tick 的位置并进来
     * @return {@code null} 表示三只眼睛都打不进箱子（Grim 会走 {@code Hitboxes}，此时不要出刀）
     */
    public static Aim bestAim(Vec3d feet, Box box, double range) {
        Box expanded = box.expand(BOX_EXPAND);
        Aim best = null;
        for (double eyeHeight : EYES) {
            Vec3d eye = new Vec3d(feet.x, feet.y + eyeHeight, feet.z);
            Vec3d dir = directionFor(eye, expanded, range);
            double distance = entry(eye, dir, expanded, RAY_LENGTH);
            if (Double.isNaN(distance)) {
                continue;
            }
            distance = Math.max(0.0D, distance);
            if (best == null || distance < best.distance()) {
                best = new Aim(dir, eyeHeight, distance);
            }
        }
        return best;
    }

    /**
     * 复刻 Grim 的插值并集：把目标"上一 tick 的位置"也并进来。
     *
     * <p>Grim 量的是 {@code getPossibleHitboxCombined()}，覆盖旧位置→新位置之间全部可能位置。
     * 目标在垂直方向的位移最大（跳/落 0.4~1.5 格一 tick），高低差对拼时这一步差得最多；
     * 并集只会让箱子更大、入射更早，所以按并集过的关，Grim 那侧一定也过。
     *
     * @param perTickDelta 目标一 tick 的位移，传 {@code entity.getVelocity()} 即可
     */
    public static Box withHistory(Box box, Vec3d perTickDelta) {
        if (perTickDelta == null) {
            return box;
        }
        double dx = perTickDelta.x;
        double dy = perTickDelta.y;
        double dz = perTickDelta.z;
        if (dx * dx + dy * dy + dz * dz > 4.0D || dx * dx + dy * dy + dz * dz < EPS) {
            // 传送/瞬移量或者原地不动：不并，免得把方向带歪
            return box;
        }
        return box.union(box.offset(-dx, -dy, -dz));
    }

    /**
     * 只量距离、不选方案（调试用）：固定一个方向，取三只候选眼里最小的入射距离。
     *
     * @return {@code Double.MAX_VALUE} 表示这个方向从三只眼睛都打不进箱子
     */
    public static double minDistance(Vec3d feet, Vec3d dir, Box box) {
        Box expanded = box.expand(BOX_EXPAND);
        double best = Double.MAX_VALUE;
        for (double eyeHeight : EYES) {
            double t = entry(new Vec3d(feet.x, feet.y + eyeHeight, feet.z), dir, expanded, RAY_LENGTH);
            if (Double.isNaN(t)) {
                continue;
            }
            if (t <= 0.0D) {
                return 0.0D;
            }
            if (t < best) {
                best = t;
            }
        }
        return best;
    }
}
