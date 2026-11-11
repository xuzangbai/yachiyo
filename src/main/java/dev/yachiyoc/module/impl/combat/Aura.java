package dev.yachiyoc.module.impl.combat;

import dev.yachiyoc.combat.AttackScheduler;
import dev.yachiyoc.combat.GhostHand;
import dev.yachiyoc.combat.GrimReach;
import dev.yachiyoc.combat.TargetSelector;
import dev.yachiyoc.combat.WeaponCategory;
import dev.yachiyoc.combat.WeaponSwitchMode;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.net.ServerMovementState;
import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.platform.RegistryKind;
import dev.yachiyoc.rotation.RotationChoice;
import dev.yachiyoc.rotation.RotationManager;
import dev.yachiyoc.rotation.RotationMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
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
    /**
     * Grim 眼高判定：开 = 按它可能假设的三只候选眼（1.62 / 1.27 / 0.4）各算一遍取最优；
     * 关 = 只认真实眼高，瞄准方向和距离门限一起退回单眼口径。
     */
    private final BoolSetting grimEyeHeight =
            add(new BoolSetting("aura", "grim_eye_height", true));
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
    /** 吃东西 / 喝药 / 举盾时不打，并且把手换回去。 */
    private final BoolSetting pauseEating =
            add(new BoolSetting("aura", "pause_eating", true));
    /** 切锤：下落途中把锤换到手上打重击（打完换回原武器）。 */
    private final BoolSetting maceSwap =
            add(new BoolSetting("aura", "mace_swap", false));
    /** 掉落多少格才切锤；0 = 只要有锤就每一刀都切。 */
    private final DoubleSetting maceFall =
            add(new DoubleSetting("aura", "mace_fall", 1.5, 0.0, 30.0, 0.5));
    /** 伪造下落（服务端会以为你在高处掉下来）——只建议单人 / 无反作弊服。 */
    private final BoolSetting maceSpoof =
            add(new BoolSetting("aura", "mace_spoof", false));
    /** 切锤固定用静默换手，不受“切换模式”影响。 */
    private final BoolSetting maceSilent =
            add(new BoolSetting("aura", "mace_silent", true));
    /** 伪造的下落格数。 */
    private final DoubleSetting maceSpoofHeight =
            add(new DoubleSetting("aura", "mace_spoof_height", 25.0, 3.0, 100.0, 1.0));

    public Aura() {
        super("aura", ModuleCategory.COMBAT, true);

        targets.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.targets.description"));
        rotation.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.rotation.description"));
        grimEyeHeight.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.grim_eye_height.description"));
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
        pauseEating.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.pause_eating.description"));
        maceSwap.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.mace_swap.description"));
        maceFall.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.mace_fall.description"));
        maceSpoof.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.mace_spoof.description"));
        maceSilent.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.mace_silent.description"));
        maceSpoofHeight.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.mace_spoof_height.description"));
        weaponType.visibleWhen(autoSwap::get);
        switchMode.visibleWhen(autoSwap::get);
        swapInventory.visibleWhen(autoSwap::get);
        weaponCooldown.visibleWhen(() -> autoSwap.get() && attackDelay.get() <= 0.0);
        maceSwap.visibleWhen(autoSwap::get);
        maceFall.visibleWhen(() -> autoSwap.get() && maceSwap.get() && !maceSpoof.get());
        maceSpoof.visibleWhen(() -> autoSwap.get() && maceSwap.get());
        maceSilent.visibleWhen(() -> autoSwap.get() && maceSwap.get());
        maceSpoofHeight.visibleWhen(() -> autoSwap.get() && maceSwap.get() && maceSpoof.get());
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
        if (pauseEating.get() && isConsuming(player)) {
            // 吃 / 喝（只认消耗品）：整个光环停手 —— 不锁定目标、不转头、不出手，
            // 也不动玩家的手（这里刻意不 release：快捷栏换手的收尾会把选中格拨回去，
            // 那样会把正在吃的食物换掉，等于打断进食）。
            RotationManager.clearAim();
            logGate("paused (eating)");
            return;
        }
        Entity target = TargetSelector.findBest(client, player,
                new TargetSelector.Filter(range.get(), Set.copyOf(targets.selected()),
                        throughWalls.get(), false, grimEyeHeight.get()));
        if (target == null) {
            RotationManager.clearAim();
            logNearestDistances(client, player);
            // 没有目标了才把手切回去（“攻击后切回”模式的语义：范围里还有人就一直拿着刀）
            if (swap) {
                GhostHand.release(player);
            }
            return;
        }
        Debug.logThrottled("aura", 2000L, "target %s %s (range %.2f)",
                target.getType(), TargetSelector.describeDistances(player, target), range.get());
        // 切锤：满足条件时这一 tick 用锤，否则用配置的武器种类
        WeaponCategory category = maceMode(player) ? WeaponCategory.MACE : weaponType.get();
        // 切锤固定走静默换手（换上去 → 打 → 同一 tick 换回），不受“切换模式”影响
        boolean silentMace = category == WeaponCategory.MACE && maceSilent.get();
        if (swap && mode == WeaponSwitchMode.SWAP_BACK && !silentMace) {
            // 不管冷却好没好，只要范围内还有目标就一直拿着武器
            GhostHand.hold(player, category, swapInventory.get(), true);
        }

        RotationMode rotationMode = RotationManager.resolve(rotation.get());
        // 这两档是"本 tick 把角度写进实体、下一 tick 才出刀"：出刀那一刻你的脚底已经又走了一 tick，
        // 垂直速度（跳/落）动辄 0.4~1.5 格，比水平 0.28 大得多 —— 高低差对拼时 Reach 就是这么爆的。
        // 所以按"下一 tick 的位置"来量、来瞄。
        boolean deferredAttack = rotationMode == RotationMode.SILENT_MOVEMENT
                || rotationMode == RotationMode.DELAY_MOVEMENT;
        Vec3d feet = deferredAttack ? player.getEntityPos().add(player.getVelocity()) : player.getEntityPos();
        // 量距离 / 选方向都用「插值并集」箱：Grim 量的是 getPossibleHitboxCombined()，
        // 覆盖目标上一 tick 的位置。垂直位移最大（跳/落 0.4~1.5 格一 tick），
        // 高低差对拼时差的就是这一段 —— 不并进来，门限会平白保守一截。
        Box targetBox = target.getBoundingBox();
        if (deferredAttack) {
            targetBox = targetBox.offset(target.getVelocity());
        }
        Box box = GrimReach.withHistory(targetBox, target.getVelocity());
        // 逐候选眼高（1.62 / 1.27 / 0.4）各算一套方向，取入射距离最短的那一组。
        // 目标是"我们在高处、目标在低处"时，0.4 那只眼能比 1.62 少算 0.5 格以上。
        // 关掉「Grim 眼高」就只传真实眼高 —— 方向从真实眼算、距离也只按真实眼量，退回单眼口径。
        double[] eyes = grimEyeHeight.get()
                ? GrimReach.GRIM_EYES
                : new double[]{player.getEyeHeight(player.getPose())};
        GrimReach.Aim aim = GrimReach.bestAim(feet, box, range.get(), eyes);
        if (aim == null) {
            logGate(grimEyeHeight.get()
                    ? "wait — 三只候选眼都打不进箱子（会吃 Hitboxes）"
                    : "wait — 真实眼打不进箱子（会吃 Hitboxes）");
            return;
        }
        float[] angles = RotationManager.anglesTo(aim.dir().x, aim.dir().y, aim.dir().z);
        double delay = attackDelay.get();
        if (!cooldownReady(player, delay)) {
            return;
        }
        // 门限按 range 整打（SLM 的 combat.att-range 默认 0 = 按 3.0 整打，一点不扣），
        // 只留 0.01 吸收 yaw/pitch 的浮点量化。
        if (!(aim.distance() <= range.get() - GrimReach.MARGIN)) {
            logGate("wait — grimReach=%.3f (limit %.3f, eye=%.2f, eyes=%s, yaw=%.1f pitch=%.1f, deferred=%s)",
                    aim.distance(), range.get() - GrimReach.MARGIN,
                    aim.eyeHeight(), grimEyeHeight.get() ? "grim" : "single",
                    angles[0], angles[1], deferredAttack);
            return;
        }
        RotationManager.run(rotationMode, angles[0], angles[1], () -> {
            // 出手前先把「位置 + 瞄准角」立刻发给服务端，再出刀。
            // SLM 的 LegacySnapRotManager#snapAt、Hachimi 的 MovementTracker#Mj 都是这一步：
            // 少了它，服务端手里的朝向/位置还是上一 tick 的，判距离时会算大（实测 3.16~3.42），
            // 于是 Reach 报超距、伤害也被吞。
            RotationManager.snapAim(player, angles[0], angles[1]);
            if (!swap) {
                AttackScheduler.attack(client, player, target, delay);
                return;
            }
            if (silentMace) {
                attackSilent(client, player, target, category);
                return;
            }
            switch (mode) {
                case SILENT -> attackSilent(client, player, target, category);
                case PER_ATTACK -> {
                    // 每次攻击切换：只负责切过去，之后不管换回原快捷栏
                    GhostHand.hold(player, category, swapInventory.get(), false);
                    applyMaceSpoof(player, category);
                    AttackScheduler.attackNow(client, player, target);
                }
                case SWAP_BACK -> {
                    applyMaceSpoof(player, category);
                    AttackScheduler.attackNow(client, player, target);
                }
            }
        });
    }

    /**
     * 没选到目标时，把“最近的生物三个口径的距离”打出来（只在 {@code --debug aura true} 时）。
     *
     * <p>{@code eye} = 真实眼睛(脚+1.62) → 箱子最近点；{@code bestEye} = 候选眼高里最近的；
     * {@code boxes} = 两个碰撞箱之间。哪个数超过 {@code 范围}，就是哪个口径在卡我们。
     */
    private void logNearestDistances(MinecraftClient client, ClientPlayerEntity player) {
        if (!Debug.enabled("aura") || client.world == null) {
            return;
        }
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : client.world.getEntities()) {
            if (entity == player || entity.isRemoved()
                    || !(entity instanceof net.minecraft.entity.LivingEntity living)
                    || living.isDead() || living.getHealth() <= 0.0F) {
                continue;
            }
            double distance = TargetSelector.nearest(player, entity);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        if (nearest != null) {
            Debug.logThrottled("aura", 2000L, "no target — nearest %s %s (range %.2f)",
                    nearest.getType(), TargetSelector.describeDistances(player, nearest), range.get());
        }
    }

    /**
     * 这一 tick 要不要切锤。
     *
     * <p>条件：开了切锤、有锤可用、<b>不是滑翔状态</b>（原版滑翔时没有重击加成），
     * 并且服务端视角的下落距离够 {@code 切锤下落高度}（默认 0 = 每一刀都切）。
     * 开了伪造下落之后不再看真实下落。
     */
    private boolean maceMode(ClientPlayerEntity player) {
        if (!maceSwap.get() || player.isGliding()) {
            return false;
        }
        if (!maceSpoof.get() && ServerMovementState.fallDistance() < maceFall.get()) {
            return false;
        }
        return !GhostHand.findWeaponStack(player, WeaponCategory.MACE, swapInventory.get()).isEmpty();
    }

    /** 伪造下落：真正出手前给服务端塞一段假的掉落位移（锤子重击用）。 */
    private void applyMaceSpoof(ClientPlayerEntity player, WeaponCategory category) {
        if (category != WeaponCategory.MACE || !maceSpoof.get()) {
            return;
        }
        ServerMovementState.spoofFall(player, maceSpoofHeight.get());
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
        boolean weaponMode = weaponCooldown.get() && autoSwap.get() && delay <= 0.0;
        ItemStack weapon = weaponMode
                ? GhostHand.findWeaponStack(player, weaponType.get(), swapInventory.get())
                : ItemStack.EMPTY;
        boolean ready = weaponMode
                ? AttackScheduler.canAttack(player, delay, weapon)
                : AttackScheduler.canAttack(player, delay);
        if (!ready) {
            logGate("wait — weapon=%s speed=%s progress=%s using=%s delay=%s sinceLastHit=%sms age=%s",
                    weapon.isEmpty() ? "<none>" : weapon.getItem(),
                    weapon.isEmpty() ? 0.0D : AttackScheduler.attackSpeedOf(player, weapon),
                    player.getAttackCooldownProgress(0.5F),
                    player.isUsingItem(), delay,
                    AttackScheduler.millisSinceLastAttack(), player.age);
        }
        return ready;
    }

    /**
     * 为什么这一 tick 没出手（2 秒一条，只在 {@code --debug} 打开时写）。
     * 排查“光环不开刀”时先看这里。
     */
    private void logGate(String format, Object... args) {
        Debug.logThrottled("aura", 2000L, format, args);
    }

    /** 正在吃 / 喝：只认消耗品，举盾、拉弓、长矛蓄力不算。 */
    private static boolean isConsuming(ClientPlayerEntity player) {
        return player.isUsingItem() && player.getActiveItem().contains(DataComponentTypes.CONSUMABLE);
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
    private void attackSilent(MinecraftClient client, ClientPlayerEntity player, Entity target,
                              WeaponCategory category) {
        int hotbar = GhostHand.findHotbarSlot(player, category);
        if (hotbar >= 0) {
            // 快捷栏本来就有这把武器：只给服务端发切手包，本地手持完全不动
            applyMaceSpoof(player, category);
            AttackScheduler.attackSilent(client, player, target, hotbar);
            return;
        }
        Runnable restore = swapInventory.get() && !GhostHand.silentBusy(player)
                ? GhostHand.swapInSilent(player, category)
                : null;
        if (restore == null) {
            // 背包里没有这一类武器（或上一轮换手还没收干净）：就用现在的手打
            applyMaceSpoof(player, category);
            AttackScheduler.attackNow(client, player, target);
            return;
        }
        try {
            applyMaceSpoof(player, category);
            AttackScheduler.attackNow(client, player, target);
        } finally {
            // 打没打中都要换回去：换手这件事不能留在手上过 tick
            restore.run();
        }
    }
}
