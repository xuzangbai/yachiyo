package dev.yachiyoc.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

/** Cooldown and attack dispatch for combat modules. */
public final class AttackScheduler {
    private AttackScheduler() {
    }

    public static boolean canAttack(ClientPlayerEntity player) {
        return !player.isUsingItem() && player.getAttackCooldownProgress(0.5F) >= 0.9F;
    }

    public static boolean attack(MinecraftClient client, ClientPlayerEntity player,
                                 Entity target) {
        if (client.interactionManager == null || target == null || !canAttack(player)
                || target.isRemoved()
                || (target instanceof LivingEntity living
                && (living.isDead() || living.getHealth() <= 0.0F))) {
            return false;
        }
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }
}
