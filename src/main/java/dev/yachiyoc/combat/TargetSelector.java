package dev.yachiyoc.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Target filtering/sorting independent of any module entrypoint.
 *
 * <p>距离一律按<b>碰撞箱</b>算：量到“箱子上离眼睛最近的一点”，不是量到箱子中心。
 * 实体大多是 0.6×1.8 这种长方体，拿中心点当标的会把贴脸的目标算得老远
 * （手动点得到、光环却判定“超出范围”），宽大的目标（船、劫掠兽、末影龙）偏差更大。
 *
 * <p><b>量距离时用多只候选眼高，任意一只够得着就算在范围内</b>（SLM 的
 * {@code TargetSelector#isWithinAttackRange} 就是 {@code anyMatch} 这个口径，
 * 配置项叫 {@code use-grim-expand-eye-height}）：
 * 我们自己的眼睛固定在“脚 + 1.62”，站在目标上方/下方时这段高度差会全算进距离里 ——
 * 头顶 4 格时眼到箱子 3.83 格、明明两个箱子挨着，光环却先把自己判出射程。
 * 换 1.27 / 0.4 这些更低的眼高再量一遍就能覆盖到，平行对拼的判定不变。
 *
 * <p>判定用的仍是服务端校验攻击距离的同一套算法（{@code Box#squaredMagnitude}），
 * 只是把“从哪只眼睛量”放宽了 —— 服务端自己也是从它的 {@code getEyePos()} 量。
 */
public final class TargetSelector {
    /** 站立 / 走路时的候选眼高（相对脚底）。 */
    private static final double[] STANDING_EYES = {1.62D, 1.27D, 0.4D};
    /** 滑翔 / 游泳 / 激流时真实眼高掉到 0.4，候选顺序跟着换。 */
    private static final double[] GLIDING_EYES = {0.4D, 1.62D, 1.27D};

    private TargetSelector() {
    }

    /**
     * @param grimEyeHeight 是否按 Grim 的三只候选眼高判定（关掉 = 只认真实眼高，和瞄准/出手门限保持一致）
     */
    public record Filter(double range, Set<String> entityIds, boolean throughWalls, boolean livingOnly,
                         boolean grimEyeHeight) {
    }

    public static Entity findBest(MinecraftClient client, ClientPlayerEntity player, Filter filter) {
        if (client.world == null || filter.entityIds().isEmpty()) {
            return null;
        }
        List<Entity> targets = client.world.getEntitiesByClass(Entity.class,
                player.getBoundingBox().expand(filter.range()),
                entity -> valid(player, entity, filter));
        return targets.stream()
                .min(Comparator.comparingDouble(entity -> nearest(player, entity, filter.grimEyeHeight())))
                .orElse(null);
    }

    /**
     * 候选眼高里离实体碰撞箱最近的那个距离 —— 选目标 / 判定射程都用它。
     *
     * @see #eyeHeights(ClientPlayerEntity, boolean)
     */
    public static double nearest(ClientPlayerEntity player, Entity entity) {
        return nearest(player, entity, true);
    }

    public static double nearest(ClientPlayerEntity player, Entity entity, boolean expandEyes) {
        return nearest(player, entity.getBoundingBox(), expandEyes);
    }

    public static double nearest(ClientPlayerEntity player, Box box) {
        return nearest(player, box, true);
    }

    public static double nearest(ClientPlayerEntity player, Box box, boolean expandEyes) {
        Vec3d feet = player.getEntityPos();
        double best = Double.MAX_VALUE;
        for (double height : eyeHeights(player, expandEyes)) {
            double distance = Math.sqrt(box.squaredMagnitude(feet.add(0.0D, height, 0.0D)));
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /**
     * 候选眼高。
     *
     * @param expand {@code true}：1.62 / 1.27 / 0.4（× 玩家 scale）+ 站立眼高 + 当前姿势眼高
     *               （潜行 1.27、游泳 0.4 …），和 Grim 的 {@code getPossibleEyeHeights()} 对齐；
     *               {@code false}：只有真实眼高，退回单眼口径
     */
    private static double[] eyeHeights(ClientPlayerEntity player, boolean expand) {
        if (!expand) {
            return new double[]{player.getEyeHeight(player.getPose())};
        }
        double[] base = player.isGliding() || player.isSwimming() || player.isUsingRiptide()
                ? GLIDING_EYES
                : STANDING_EYES;
        double scale = player.getScale();
        double[] heights = new double[base.length + 2];
        for (int i = 0; i < base.length; i++) {
            heights[i] = base[i] * scale;
        }
        heights[base.length] = player.getStandingEyeHeight();
        heights[base.length + 1] = player.getEyeHeight(player.getPose());
        return heights;
    }

    /**
     * 三个口径的距离，<b>只给调试日志用</b>（不改任何判定）：
     * <ul>
     *   <li>{@code eye} —— 真实眼睛（脚 + 1.62）到箱子最近点，原版判定用的就是这个</li>
     *   <li>{@code bestEye} —— 候选眼高里最近的那个（1.27 / 0.4 …）</li>
     *   <li>{@code boxes} —— 两个碰撞箱之间的距离（比眼距小一大截）</li>
     * </ul>
     */
    public static String describeDistances(ClientPlayerEntity player, Entity entity) {
        Box box = entity.getBoundingBox();
        return String.format("eye=%.2f bestEye=%.2f boxes=%.2f",
                reach(player.getEyePos(), entity),
                nearest(player, box),
                Math.sqrt(player.getBoundingBox().squaredMagnitude(box)));
    }

    /**
     * 眼睛到实体碰撞箱的最近距离（单眼版本，保留给“已知从哪只眼睛量”的调用方）。
     *
     * <p>用的是 {@code Box#squaredMagnitude(Vec3d)}，和服务端校验攻击距离
     * （{@code PlayerEntity#canInteractWithEntityIn}）完全同一套算法。
     */
    public static double reach(Vec3d eye, Entity entity) {
        return Math.sqrt(entity.getBoundingBox().squaredMagnitude(eye));
    }

    private static boolean valid(ClientPlayerEntity player, Entity entity, Filter filter) {
        if (entity == player || entity.isRemoved()) {
            return false;
        }
        if (filter.livingOnly() && !(entity instanceof net.minecraft.entity.LivingEntity)) {
            return false;
        }
        if (entity instanceof net.minecraft.entity.LivingEntity living
                && (living.isDead() || living.getHealth() <= 0.0F)) {
            return false;
        }
        String id = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        if (!filter.entityIds().contains(id)) {
            return false;
        }
        if (nearest(player, entity, filter.grimEyeHeight()) > filter.range()) {
            return false;
        }
        return filter.throughWalls() || player.canSee(entity);
    }
}
