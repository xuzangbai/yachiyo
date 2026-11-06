package dev.yachiyoc.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

/**
 * 冷却 / 延迟与出手派发。
 *
 * <p>出手节奏：
 * <ul>
 *   <li>{@code delayMs <= 0}：跟随武器冷却（原版 1.9+ 手感）；</li>
 *   <li>{@code delayMs > 0}：固定间隔（低版本 / 1.8 服务器没有冷却，靠这个限速）。</li>
 * </ul>
 *
 * <p>冷却有两种算法：
 * <ul>
 *   <li>{@link #canAttack(ClientPlayerEntity, double)}：原版口径，按<b>当前手持物</b>的
 *       {@code ATTACK_SPEED} 算（即 {@code getAttackCooldownProgress}）；</li>
 *   <li>{@link #canAttack(ClientPlayerEntity, double, ItemStack)}：按<b>要用的那把武器</b>算。
 *       静默切刀时手里拿的是镐子、真正出手的是刀，用前者会算错（刀慢就白白多等，刀快又打不满）。</li>
 * </ul>
 *
 * <p>两种出手方式：
 * <ul>
 *   <li>{@link #attack}：用当前手持物出手（换手由 {@link GhostHand} 事先做好），会再判一次冷却；</li>
 *   <li>{@link #attackNow}：调用方已经判过冷却，这里只出手；</li>
 *   <li>{@link #attackSilent}：本地手持不动，只给服务端发「换武器 → 攻击 → 换回来」，
 *       命中那一刻服务端认为你拿的是武器（附魔生效）。</li>
 * </ul>
 */
public final class AttackScheduler {
    /** 还没有记录过出手时的 tick 占位值。 */
    private static final long UNKNOWN_TICK = Long.MIN_VALUE / 2;

    private static long lastAttackMs;
    private static long lastAttackTick = UNKNOWN_TICK;

    private AttackScheduler() {
    }

    /** 原版口径：按当前手持物判冷却。 */
    public static boolean canAttack(ClientPlayerEntity player, double delayMs) {
        if (player.isUsingItem()) {
            return false;
        }
        if (delayMs > 0.0) {
            return System.currentTimeMillis() - lastAttackMs >= (long) delayMs;
        }
        return player.getAttackCooldownProgress(0.5F) >= 0.9F;
    }

    /**
     * 按 {@code weapon} 这把武器自己的冷却判能不能出手（不管手里现在拿的是什么）。
     *
     * <p>换手之后手里物品变了，玩家的 {@code ATTACK_SPEED} 属性跟着变
     * （剑 1.6 / 斧 1.0 / 重锤 0.6），原版 {@code getAttackCooldownProgress} 算的就是换完之后那份，
     * 所以这里自己记“上次出手是哪个 tick”，再按武器的攻速还原进度：
     * <pre>
     * progress = (距今 tick 数 + 0.5) / (20 / 武器攻速)
     * </pre>
     * 原版那份没被 clamp 时再取两者较小的值 —— 这样玩家手动点出来的那一下也会被算进去。
     *
     * @param weapon 打算用来出手的武器；为空时退化成 {@link #canAttack(ClientPlayerEntity, double)}
     */
    public static boolean canAttack(ClientPlayerEntity player, double delayMs, ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return canAttack(player, delayMs);
        }
        if (player.isUsingItem()) {
            return false;
        }
        if (delayMs > 0.0) {
            return System.currentTimeMillis() - lastAttackMs >= (long) delayMs;
        }
        double speed = attackSpeedOf(player, weapon);
        if (speed <= 0.0) {
            return player.getAttackCooldownProgress(0.5F) >= 0.9F;
        }
        double elapsed = elapsedTicks(player) + 0.5D;
        float heldPerTick = player.getAttackCooldownProgressPerTick();
        float heldProgress = player.getAttackCooldownProgress(0.5F);
        if (heldProgress < 1.0F) {
            // 没被 clamp 时 progress * perTick 就是“距上次出手的 tick 数 + 0.5”
            elapsed = Math.min(elapsed, heldProgress * heldPerTick);
        }
        double perTick = 20.0D / speed;
        return elapsed / perTick >= 0.9D;
    }

    /** 这把武器拿在手上时的每秒攻击次数（{@code ATTACK_SPEED}，含物品自身的属性修正）。 */
    public static double attackSpeedOf(ClientPlayerEntity player, ItemStack stack) {
        double base = player.getAttributeBaseValue(EntityAttributes.ATTACK_SPEED);
        if (stack == null || stack.isEmpty()) {
            return player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
        }
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            // 没有属性组件（木棍、空手之类）：拿在手上就是玩家的基础攻速
            return base;
        }
        return modifiers.applyOperations(EntityAttributes.ATTACK_SPEED, base, EquipmentSlot.MAINHAND);
    }

    /** 用当前手持物出手（会再判一次冷却）。 */
    public static boolean attack(MinecraftClient client, ClientPlayerEntity player,
                                 Entity target, double delayMs) {
        if (!canAttack(player, delayMs) || !canHit(client, target)) {
            return false;
        }
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        markAttacked(player);
        return true;
    }

    /**
     * 已经判过冷却的出手：只做目标有效性检查，不再看 {@code attackCooldown}。
     *
     * <p>静默切刀要用它 —— 换手之后手里的武器变了，玩家的 {@code ATTACK_SPEED} 属性跟着变，
     * 这时候再判一次 {@code getAttackCooldownProgress} 就会把“可以打的刀”判成没冷却好，
     * 表现就是刀拿在手上干等 cd。SLM 的 {@code attackWithCritic} 同样不判冷却，
     * 节奏交给光环自己的 delay 逻辑。
     */
    public static boolean attackNow(MinecraftClient client, ClientPlayerEntity player,
                                    Entity target) {
        if (!canHit(client, target)) {
            return false;
        }
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        markAttacked(player);
        return true;
    }

    /**
     * 静默出手：本地手持完全不动，只发换手包围住这次攻击。
     *
     * <p>冷却由调用方在换手<b>之前</b>判好（见 {@link #canAttack(ClientPlayerEntity, double, ItemStack)}），
     * 这里不再判 —— 换完手里已经是武器了，再判会按武器的攻速错判。
     *
     * @param weaponSlot 目标武器所在快捷栏槽位；-1 或等于当前槽位时退化成普通出手
     */
    public static boolean attackSilent(MinecraftClient client, ClientPlayerEntity player,
                                       Entity target, int weaponSlot) {
        PlayerInventory inventory = player.getInventory();
        if (weaponSlot < 0 || weaponSlot == inventory.getSelectedSlot()) {
            return attackNow(client, player, target);
        }
        if (!canHit(client, target)) {
            return false;
        }
        int original = inventory.getSelectedSlot();
        sendSelectedSlot(player, weaponSlot);
        sendAttack(player, target);
        player.attack(target);
        player.swingHand(Hand.MAIN_HAND);
        sendSelectedSlot(player, original);
        markAttacked(player);
        return true;
    }

    private static boolean canHit(MinecraftClient client, Entity target) {
        return client.interactionManager != null && target != null && !target.isRemoved()
                && !(target instanceof LivingEntity living
                && (living.isDead() || living.getHealth() <= 0.0F));
    }

    private static void markAttacked(ClientPlayerEntity player) {
        lastAttackMs = System.currentTimeMillis();
        lastAttackTick = player.getEntityWorld() == null ? UNKNOWN_TICK : player.getEntityWorld().getTime();
    }

    /** 距离本模块上一次出手过了几个 tick（没有记录时给一个很大的数）。 */
    private static double elapsedTicks(ClientPlayerEntity player) {
        if (lastAttackTick == UNKNOWN_TICK || player.getEntityWorld() == null) {
            return Double.MAX_VALUE / 4.0D;
        }
        return Math.max(0L, player.getEntityWorld().getTime() - lastAttackTick);
    }

    private static void sendSelectedSlot(ClientPlayerEntity player, int slot) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private static void sendAttack(ClientPlayerEntity player, Entity target) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, player.isSneaking()));
        }
    }
}
