package dev.yachiyoc.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Target filtering/sorting independent of any module entrypoint.
 *
 * <p>距离一律按<b>碰撞箱</b>算：从眼睛量到“箱子上离眼睛最近的一点”，不是量到箱子中心。
 * 实体大多是 0.6×1.8 这种长方体，拿中心点当标的会把贴脸的目标算得老远
 * （手动点得到、光环却判定“超出范围”），宽大的目标（船、劫掠兽、末影龙）偏差更大。
 * 这里用的是服务端校验攻击距离时同一套算法（{@code PlayerEntity#canInteractWithEntityIn}
 * → {@code Box#squaredMagnitude}），所以这里判“在范围内”，服务端也认。
 */
public final class TargetSelector {
    private TargetSelector() {
    }

    public record Filter(double range, Set<String> entityIds, boolean throughWalls, boolean livingOnly) {
    }

    public static Entity findBest(MinecraftClient client, ClientPlayerEntity player, Filter filter) {
        if (client.world == null || filter.entityIds().isEmpty()) {
            return null;
        }
        Vec3d eye = player.getEyePos();
        List<Entity> targets = client.world.getEntitiesByClass(Entity.class,
                player.getBoundingBox().expand(filter.range()),
                entity -> valid(player, eye, entity, filter));
        return targets.stream()
                .min(Comparator.comparingDouble(entity -> reach(eye, entity)))
                .orElse(null);
    }

    /**
     * 眼睛到实体碰撞箱的最近距离。
     *
     * <p>用的是 {@code Box#squaredMagnitude(Vec3d)}，和服务端校验攻击距离
     * （{@code PlayerEntity#canInteractWithEntityIn}）完全同一套算法 ——
     * 所以这里说“在范围内”，服务端也认。
     */
    public static double reach(Vec3d eye, Entity entity) {
        return Math.sqrt(entity.getBoundingBox().squaredMagnitude(eye));
    }

    private static boolean valid(ClientPlayerEntity player, Vec3d eye,
                                 Entity entity, Filter filter) {
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
        if (reach(eye, entity) > filter.range()) {
            return false;
        }
        return filter.throughWalls() || player.canSee(entity);
    }
}
