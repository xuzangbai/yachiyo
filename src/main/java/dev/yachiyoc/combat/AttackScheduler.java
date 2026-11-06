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
    private static long lastAttackMs;

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
        if (!Double.isFinite(speed) || speed <= 0.0D) {
            return player.getAttackCooldownProgress(0.5F) >= 0.9F;
        }
        // 原版最慢的重锤是 0.6；比这还慢多半是自定义物品/服务端的怪属性，别让它把光环卡死
        speed = Math.max(speed, 0.6D);
        double perTick = 20.0D / speed;
        if (!Double.isFinite(perTick) || perTick <= 0.0D) {
            return player.getAttackCooldownProgress(0.5F) >= 0.9F;
        }
        return elapsedTicks(player) / perTick >= 0.9D;
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
    }

    /**
     * 距离上一次出手过了几个 tick。
     *
     * <p>原版进度没被 clamp 时，{@code progress * perTick} 就是“距上次出手的 tick 数 + 0.5”，
     * 直接用它（这是服务端那份数据的镜像，最准）；被 clamp 到 1 之后原版不再给更多信息，
     * 就用墙钟（每 tick 50ms）补。
     *
     * <p>两边都是单调递增的量，所以这个判定**不会卡死** —— 之前用 {@code player.age} 自己记，
     * 结果客户端那个计数没有跟着走，打出一刀以后永远判定“冷却没好”。
     */
    private static double elapsedTicks(ClientPlayerEntity player) {
        double wall = lastAttackMs <= 0L
                ? Double.MAX_VALUE / 4.0D
                : Math.max(0D, (System.currentTimeMillis() - lastAttackMs) / 50.0D);
        float progress = player.getAttackCooldownProgress(0.5F);
        if (progress > 0.0F && progress < 1.0F) {
            return Math.min(progress * player.getAttackCooldownProgressPerTick(), wall);
        }
        return wall;
    }

    /** 距上次出手多少毫秒（给日志用）。 */
    public static long millisSinceLastAttack() {
        return lastAttackMs <= 0L ? -1L : System.currentTimeMillis() - lastAttackMs;
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
