package dev.yachiyoc.module.impl.combat;

import dev.yachiyoc.combat.AttackScheduler;
import dev.yachiyoc.combat.GhostHand;
import dev.yachiyoc.combat.TargetSelector;
import dev.yachiyoc.combat.WeaponCategory;
import dev.yachiyoc.combat.WeaponSwitchMode;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.platform.RegistryKind;
import dev.yachiyoc.rotation.RotationChoice;
import dev.yachiyoc.rotation.RotationManager;
import dev.yachiyoc.rotation.RotationMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;

/** Module entrypoint only; target and attack logic lives in combat/. */
public final class Aura extends Module {
    private static final List<String> DEFAULT_TARGETS = List.of(
            "minecraft:player",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:enderman",
            "minecraft:witch",
            "minecraft:slime",
            "minecraft:blaze",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:ravager");

    private final DoubleSetting range =
            add(new DoubleSetting("aura", "range", 3.0, 1.0, 6.0, 0.1));
    private final RegistryListSetting targets = add(new RegistryListSetting(
            "aura", "targets", RegistryKind.ENTITY_TARGET, DEFAULT_TARGETS));
    private final BoolSetting throughWalls =
            add(new BoolSetting("aura", "through_walls", false));
    private final EnumSetting<RotationChoice> rotation = add(new EnumSetting<>(
            "aura", "rotation", RotationChoice.class, RotationChoice.FOLLOW));
    /** 0 = 跟随武器冷却；>0 = 固定间隔（毫秒）。 */
    private final DoubleSetting attackDelay =
            add(new DoubleSetting("aura", "attack_delay", 0.0, 0.0, 1000.0, 10.0));
    private final BoolSetting autoSwap =
            add(new BoolSetting("aura", "auto_swap", true));
    /** 自动切换的目标武器种类（同类里按伤害挑最好的那把）。 */
    private final EnumSetting<WeaponCategory> weaponType = add(new EnumSetting<>(
            "aura", "weapon_type", WeaponCategory.class, WeaponCategory.SWORD));
    /** 什么时候切过去。 */
    private final EnumSetting<WeaponSwitchMode> switchMode = add(new EnumSetting<>(
            "aura", "switch_mode", WeaponSwitchMode.class, WeaponSwitchMode.SILENT));
    /** 快捷栏没有武器时，允许从背包（9..35）换。 */
    private final BoolSetting swapInventory =
            add(new BoolSetting("aura", "swap_inventory", true));
    /** 按“要用的那把武器”的攻速算冷却，而不是按手上拿的东西（静默切刀时手里是镐子）。 */
    private final BoolSetting weaponCooldown =
            add(new BoolSetting("aura", "weapon_cooldown", true));

    public Aura() {
        super("aura", ModuleCategory.COMBAT, true);

        targets.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.targets.description"));
        rotation.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.rotation.description"));
        attackDelay.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.attack_delay.description"));
        autoSwap.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.auto_swap.description"));
        weaponType.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.weapon_type.description"));
        switchMode.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.switch_mode.description"));
        swapInventory.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.swap_inventory.description"));
        weaponCooldown.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.weapon_cooldown.description"));
        weaponType.visibleWhen(autoSwap::get);
        switchMode.visibleWhen(autoSwap::get);
        swapInventory.visibleWhen(autoSwap::get);
        weaponCooldown.visibleWhen(() -> autoSwap.get() && attackDelay.get() <= 0.0);
        RotationManager.initialize();
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
    }

    @Override
    public void onDisable() {
        RotationManager.clearAim();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            // 只“申请收刀”：静默切刀的那把刀由 GhostHand 在 tick 末尾自己点回背包，
            // 这里不能 forget()，否则刀就永远留在手上了。
            GhostHand.release(player);
        }
    }

    private void tick(MinecraftClient client) {
        // 注意：这里刻意不检查 currentScreen —— 开着背包/箱子/聊天/ClickGUI 时也要继续打。
        // 只在世界被暂停（单机暂停菜单、/tick freeze）时停手，否则请求会一直堆在集成服务端那边。
        if (!enabled() || client.player == null || client.world == null
                || client.interactionManager == null || !client.world.getTickManager().shouldTick()) {
            return;
        }
        ClientPlayerEntity player = client.player;
        aimAndAttack(client, player);
    }

    private void aimAndAttack(MinecraftClient client, ClientPlayerEntity player) {
        boolean swap = autoSwap.get();
        WeaponSwitchMode mode = switchMode.get();
        Entity target = TargetSelector.findBest(client, player,
                new TargetSelector.Filter(range.get(), Set.copyOf(targets.selected()),
                        throughWalls.get(), false));
        if (target == null) {
            RotationManager.clearAim();
            // 没有目标了才把手切回去（“攻击后切回”模式的语义：范围里还有人就一直拿着刀）
            if (swap) {
                GhostHand.release(player);
            }
            return;
        }
        if (swap && mode == WeaponSwitchMode.SWAP_BACK) {
            // 不管冷却好没好，只要范围内还有目标就一直拿着武器
            GhostHand.hold(player, weaponType.get(), swapInventory.get());
        }

        Vec3d eye = player.getEyePos();
        Vec3d point = target.getBoundingBox().getCenter();
        float[] angles = RotationManager.anglesTo(point.x - eye.x, point.y - eye.y, point.z - eye.z);
        double delay = attackDelay.get();
        if (!cooldownReady(player, delay)) {
            return;
        }
        RotationMode rotationMode = RotationManager.resolve(rotation.get());
        RotationManager.run(rotationMode, angles[0], angles[1], () -> {
            if (!swap) {
                AttackScheduler.attack(client, player, target, delay);
                return;
            }
            switch (mode) {
                case SILENT -> attackSilent(client, player, target);
                case PER_ATTACK -> {
                    GhostHand.hold(player, weaponType.get(), swapInventory.get());
                    AttackScheduler.attackNow(client, player, target);
                }
                case SWAP_BACK -> AttackScheduler.attackNow(client, player, target);
            }
        });
    }

    /**
     * 出手冷却判定。
     *
     * <p>默认按<b>要用的那把武器</b>的 {@code ATTACK_SPEED} 算（{@code 按武器冷却} 开关，默认开）：
     * 静默切刀时手里拿的是镐子、真正出手的是背包里换进来的刀，按手上物品算会让节奏跟着镐子走
     * —— 刀比镐子快就白白少打，比镐子慢就一刀一刀等。
     *
     * <p>`攻击延迟` 大于 0 时永远走固定间隔，这个开关不起作用（UI 里也会隐藏）。
     */
    private boolean cooldownReady(ClientPlayerEntity player, double delay) {
        if (!weaponCooldown.get() || !autoSwap.get() || delay > 0.0) {
            return AttackScheduler.canAttack(player, delay);
        }
        ItemStack weapon = GhostHand.findWeaponStack(player, weaponType.get(), swapInventory.get());
        return AttackScheduler.canAttack(player, delay, weapon);
    }

    /**
     * 静默出手。
     *
     * <p>和 SLM 的 {@code Attack#attackWithSettings} 一致：
     * <pre>
     * Runnable restore = GhostHand.swapInSilent(...);   // 点一下，把背包里的刀换进手
     * AttackScheduler.attackNow(...);                   // 立刻出手（冷却已在换手前判过）
     * if (restore != null) restore.run();               // 同一个 tick 点回去
     * </pre>
     * 点击在本地是预测生效的，所以“刀在手上”只存在于这一个 tick 的中间态，一帧都渲染不到；
     * 服务端按包序处理，先应用换手再收攻击，那一刀用的就是刀。
     *
     * <p>冷却在换手<b>之前</b>判（进到这里之前 {@link #cooldownReady} 已经按要用的那把武器算过）：
     * 换进来的刀会改 {@code ATTACK_SPEED} 属性，换完再判一次就变成“刀拿在手上等 cd”。
     */
    private void attackSilent(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        int hotbar = GhostHand.findHotbarSlot(player, weaponType.get());
        if (hotbar >= 0) {
            // 快捷栏本来就有刀：只给服务端发切手包，本地手持完全不动
            AttackScheduler.attackSilent(client, player, target, hotbar);
            return;
        }
        Runnable restore = swapInventory.get() && !GhostHand.silentBusy(player)
                ? GhostHand.swapInSilent(player, weaponType.get())
                : null;
        if (restore == null) {
            // 背包里没有这一类武器（或上一轮换手还没收干净）：就用现在的手打
            AttackScheduler.attackNow(client, player, target);
            return;
        }
        try {
            AttackScheduler.attackNow(client, player, target);
        } finally {
            // 打没打中都要换回去：换手这件事不能留在手上过 tick
            restore.run();
        }
    }
}
