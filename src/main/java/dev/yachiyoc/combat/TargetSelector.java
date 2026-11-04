package dev.yachiyoc.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Target filtering/sorting independent of any module entrypoint. */
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
                .min(Comparator.comparingDouble(entity ->
                        eye.squaredDistanceTo(entity.getBoundingBox().getCenter())))
                .orElse(null);
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
        if (eye.squaredDistanceTo(entity.getBoundingBox().getCenter())
                > filter.range() * filter.range()) {
            return false;
        }
        return filter.throughWalls() || player.canSee(entity);
    }
}
