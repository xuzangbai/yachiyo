package dev.yachiyoc.combat;

import dev.yachiyoc.mixin.ClientPlayerEntityAccessor;
import dev.yachiyoc.net.PostManager;
import dev.yachiyoc.platform.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;

import java.util.OptionalInt;

/**
 * 鬼手切刀（移植自 SLM 的 {@code InvExtra#swapItemToHand} / {@code syncSelectedHotbar} /
 * {@code sendPacketsForInventoryAction}）。
 *
 * <p>把目标武器换到手上，用完再换回来：
 * <ul>
 *   <li><b>快捷栏</b>：{@code PlayerInventory#setSelectedSlot} + 发
 *       {@code UpdateSelectedSlotC2SPacket}，等价于 SLM 的 {@code syncSelectedHotbar}；</li>
 *   <li><b>背包（9..35）</b>：容器 {@code ClickSlot(SWAP)} 把背包格子换进当前手。
 *       点之前先 {@link #flushForInventoryAction}（停冲刺 + 清一次输入包），
 *       对应 SLM 的 {@code sendPacketsForInventoryAction}。</li>
 * </ul>
 *
 * <h2>静默切刀：跟 SLM 一样，一个 tick 内换手 → 出手 → 换回</h2>
 *
 * <p>SLM 的 {@code Attack#attackWithSettings} 是这么写的：
 *
 * <pre>
 * Runnable callback = InvExtra.swapItemToHand(...);   // 点一下，把刀换进手
 * attackWithCritic(player, target, ...);             // 立刻出手，不判冷却
 * if (callback != null) callback.run();              // 同一个 tick 点回去
 * </pre>
 *
 * <p>它<b>不等服务器确认</b>：点击是客户端预测（{@code ClientPlayerInteractionManager#clickSlot}
 * 会先本地 {@code onSlotClick}），所以“刀在手上”只存在于这一个 tick 的中间态，
 * 一帧都渲染不到 —— 这就是“静默”的全部来源。服务端按包序处理，先应用换手、再收攻击，
 * 所以那一刀用的是刀。
 *
 * <p>对应这里：{@link #swapInSilent} 只发点击并返回“点回去”的动作，
 * 调用方（{@code Aura}）自己排好 {@code 换手 → 出手 → 换回}。
 *
 * <p>另外，<b>冷却必须按换手前的手判</b>：换进来的刀会改玩家的 {@code ATTACK_SPEED} 属性
 * （剑 1.6 / 斧 1.0 / 重锤 0.6），换完再判一次 {@code getAttackCooldownProgress}
 * 就会变成“刀拿在手上等 cd”。所以出手走 {@link AttackScheduler#attackNow}（不重复判冷却）。
 *
 * <p>换回仍然有兜底：{@link #serviceSilent} 在 tick 末尾看两个格子里的东西，
 * 刀还留在手上（服务端按 revision 丢了那次点击）就隔几个 tick 补一次，最多几次后放弃。
 */
public final class GhostHand {

    /** 换回点击最多补几次（服务端按 revision 丢包时自愈）。 */
    private static final int RESTORE_MAX_ATTEMPTS = 8;
    /** 两次补点之间至少隔几个 tick，不要一 tick 连点。 */
    private static final int RESTORE_RETRY_TICKS = 2;
    /** 刀确认回到背包格之后，看门狗再多看几个 tick（服务端 re-sync 可能把预测打回来）。 */
    private static final int RESTORE_WATCH_TICKS = 10;

    private static Integer hotBarRestore;

    private static Pending pending;
    /** 静默切刀的看门狗：这一轮换手换了哪个格子、有没有换回去。 */
    private static SilentSwap silent;

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTickEnd(client));
    }

    private GhostHand() {
    }

    /** tick 末尾：快捷栏换回 + 静默切刀兜底。 */
    private static void onTickEnd(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            // 掉线 / 回主菜单：不能再点任何格子，把记录清干净
            forget();
            return;
        }
        Integer restore = hotBarRestore;
        hotBarRestore = null;
        if (restore != null) {
            player.getInventory().setSelectedSlot(restore);
            slideSyncHotbar(restore);
        }
        serviceSilent(player);
    }

    /** 快捷栏换回：本地槽位立刻改，包推到下一个服务器事务（SLM 的 {@code addNextTransactionAction}）。 */
    private static void slideSyncHotbar(int slot) {
        PostManager.queueNextTransaction(() -> {
            ClientPlayerEntity current = MinecraftClient.getInstance().player;
            if (current != null) {
                sendSelectedSlot(current, slot);
            }
        });
    }

    /**
     * 让手上拿着目标种类的武器（已经在拿就不动）。保持型模式（每次攻击切换 / 攻击后切回）用。
     *
     * @param allowInventory 快捷栏没有目标武器时，是否允许从背包里换
     * @return 手上现在是不是目标武器
     */
    public static boolean hold(ClientPlayerEntity player, WeaponCategory category, boolean allowInventory) {
        if (player.isUsingItem()) {
            // 吃东西 / 举盾 / 拉弓时别抢手
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        int index = findWeapon(inventory, category, allowInventory);
        if (index < 0) {
            return false;
        }
        if (index == selected) {
            return true;
        }
        if (index < 9) {
            if (pending == null) {
                pending = Pending.hotbar(selected);
            }
            setHotbar(player, index);
            return true;
        }
        // 背包格子：已经换过一次就不动（SLM 里点容器是不看界面的，ClickGUI 开着也能换，这里保持一致）
        if (pending != null) {
            return false;
        }
        OptionalInt handlerSlot = player.currentScreenHandler.getSlotIndex(inventory, index);
        if (handlerSlot.isEmpty()) {
            return false;
        }
        int syncId = player.currentScreenHandler.syncId;
        clickSwap(player, syncId, handlerSlot.getAsInt(), selected);
        pending = Pending.inventory(syncId, handlerSlot.getAsInt(), selected, index,
                inventory.getStack(index).copy());
        return true;
    }

    /** 之前 {@link #hold} 换过手就换回来；没换过什么都不做。静默切刀那一轮由看门狗自己收尾。 */
    public static void release(ClientPlayerEntity player) {
        Pending state = pending;
        pending = null;
        if (state == null || player == null) {
            return;
        }
        if (state.hotbarSlot >= 0) {
            // 快捷栏：tick 末尾改本地，包走下一个事务
            hotBarRestore = state.hotbarSlot;
            return;
        }
        // 处理器换了（界面关了或换了容器）就不再点，避免点错地方
        if (player.currentScreenHandler.syncId != state.syncId) {
            return;
        }
        SilentSwap restore = new SilentSwap(state.syncId, state.handlerSlot,
                state.hotbarButton, state.sourceIndex, state.weapon);
        silent = restore;
        clickSwap(player, restore.syncId, restore.handlerSlot, restore.hotbarButton);
    }

    /** 只清掉换手记录（换服务器 / 退出时用，不再点回去）。 */
    public static void forget() {
        pending = null;
        silent = null;
        hotBarRestore = null;
    }

    /**
     * 静默切刀的一步：把背包里最好的那把刀点进当前手，返回“点回去”的动作。
     *
     * <p>和 SLM 的 {@code InvExtra#swapItemToHand} 完全一样 —— 只发点击，<b>不等服务器确认</b>。
     * 点击在本地是预测生效的，所以调用方要按
     * {@code 换手 → 出手 → 换回} 的顺序在同一个 tick 内跑完（中间的“刀在手上”渲染不到）。
     *
     * @return 换回动作；背包里没有这一类武器时返回 {@code null}
     */
    public static Runnable swapInSilent(ClientPlayerEntity player, WeaponCategory category) {
        PlayerInventory inventory = player.getInventory();
        if (player.isUsingItem()) {
            return null;
        }
        int index = bestInRange(inventory, category, 9, 36);
        if (index < 0) {
            return null;
        }
        OptionalInt handlerSlot = player.currentScreenHandler.getSlotIndex(inventory, index);
        if (handlerSlot.isEmpty()) {
            return null;
        }
        int selected = inventory.getSelectedSlot();
        int syncId = player.currentScreenHandler.syncId;
        int slot = handlerSlot.getAsInt();
        SilentSwap state = new SilentSwap(syncId, slot, selected, index,
                inventory.getStack(index).copy());
        silent = state;
        clickSwap(player, syncId, slot, selected);
        Debug.log("ghosthand", "swap in slot %s -> hotbar %s (syncId %s)", index, selected, syncId);
        return () -> restoreSilent(player, state);
    }

    /** 反向点击：把刀换回原来那格（SLM 的 {@code wrapInvTask} 在非嵌套时也是立刻执行）。 */
    private static void restoreSilent(ClientPlayerEntity player, SilentSwap state) {
        if (player == null || !rebind(player, state)) {
            return;
        }
        clickSwap(player, state.syncId, state.handlerSlot, state.hotbarButton);
    }

    /** 静默切刀还没收干净（刀还挂在手上）：这一 tick 别再叠一次换手点击。 */
    public static boolean silentBusy(ClientPlayerEntity player) {
        SilentSwap state = silent;
        if (state == null) {
            return false;
        }
        if (!rebind(player, state)) {
            silent = null;
            return false;
        }
        return inHand(player.getInventory(), state);
    }

    /**
     * tick 末尾的兜底：换回点击被服务端按 revision 丢掉时，刀会留在手上，这里隔几个 tick 补一次。
     *
     * <p>不看 {@code revision}，只看格子里的东西：刀回到 {@code sourceIndex} 才算收完；
     * 还在 {@code hotbarButton} 那格就补点；两边都没有说明被玩家/其它模块动过，直接放弃。
     */
    public static void serviceSilent(ClientPlayerEntity player) {
        SilentSwap state = silent;
        if (state == null) {
            return;
        }
        if (!rebind(player, state)) {
            // 换了容器而且对不上格子：不再点，物品留在手上也不乱点
            Debug.log("ghosthand", "container changed, drop silent state (slot %s)", state.sourceIndex);
            silent = null;
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (inHand(inventory, state)) {
            state.watch = 0;
            if (++state.ticks < RESTORE_RETRY_TICKS) {
                return;
            }
            state.ticks = 0;
            if (state.attempts >= RESTORE_MAX_ATTEMPTS) {
                Debug.log("ghosthand", "give up restoring slot %s (still in hand)", state.sourceIndex);
                silent = null;
                return;
            }
            state.attempts++;
            Debug.log("ghosthand", "restore click dropped (slot %s), retry %s", state.sourceIndex, state.attempts);
            clickSwap(player, state.syncId, state.handlerSlot, state.hotbarButton);
            return;
        }
        if (!inSource(inventory, state)) {
            // 两边都没有这把刀：被玩家/其它模块动过，不再点击
            silent = null;
            return;
        }
        // 刀回背包格了，再多看几个 tick 才收工（服务端稍后可能把预测打回来）
        if (++state.watch > RESTORE_WATCH_TICKS) {
            silent = null;
        }
    }

    /** 快捷栏里目标武器最高伤害的格子；没有返回 -1（静默模式只认快捷栏）。 */
    public static int findHotbarSlot(ClientPlayerEntity player, WeaponCategory category) {
        return bestInRange(player.getInventory(), category, 0, 9);
    }

    /**
     * 按 {@link #hold} / 静默切刀同一套口径找出真正会用来出手的那把武器（快捷栏优先，其次背包）。
     *
     * <p>光环要按这把武器自己的 {@code ATTACK_SPEED} 判冷却（静默切刀时手里拿的不是它）。
     *
     * @return 找不到时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack findWeaponStack(ClientPlayerEntity player, WeaponCategory category,
                                            boolean allowInventory) {
        PlayerInventory inventory = player.getInventory();
        int index = findWeapon(inventory, category, allowInventory);
        return index < 0 ? ItemStack.EMPTY : inventory.getStack(index);
    }

    private static int findWeapon(PlayerInventory inventory, WeaponCategory category,
                                  boolean allowInventory) {
        int hotbar = bestInRange(inventory, category, 0, 9);
        if (hotbar >= 0 || !allowInventory) {
            return hotbar;
        }
        return bestInRange(inventory, category, 9, 36);
    }

    private static int bestInRange(PlayerInventory inventory, WeaponCategory category,
                                   int from, int to) {
        int bestSlot = -1;
        int bestDamage = Integer.MIN_VALUE;
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!category.matches(stack)) {
                continue;
            }
            int damage = damageOf(stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static int damageOf(ItemStack stack) {
        WeaponComponent weapon = stack.get(DataComponentTypes.WEAPON);
        return weapon == null ? 0 : weapon.itemDamagePerAttack();
    }

    /**
     * 容器换了（开箱子 / 关界面）以后把格子编号对齐到当前容器。
     *
     * <p>背包下标 {@code sourceIndex} 不会变，变的是它在容器里的 slot 编号；
     * {@code getSlotIndex} 找不到就说明当前容器里没有这个格子，那就不点了。
     *
     * @return 现在的容器能不能用来点这一轮换回
     */
    private static boolean rebind(ClientPlayerEntity player, SilentSwap state) {
        int syncId = player.currentScreenHandler.syncId;
        if (syncId == state.syncId) {
            return true;
        }
        OptionalInt slot = player.currentScreenHandler.getSlotIndex(
                player.getInventory(), state.sourceIndex);
        if (slot.isEmpty()) {
            return false;
        }
        state.syncId = syncId;
        state.handlerSlot = slot.getAsInt();
        return true;
    }

    /** 刀在不在手上（换进去的那一格快捷栏）。 */
    private static boolean inHand(PlayerInventory inventory, SilentSwap state) {
        return sameWeapon(inventory.getStack(state.hotbarButton), state.weapon);
    }

    /** 刀在不在原来的背包格子。 */
    private static boolean inSource(PlayerInventory inventory, SilentSwap state) {
        return sameWeapon(inventory.getStack(state.sourceIndex), state.weapon);
    }

    /**
     * 只比物品类型和数量，不比完整组件：刀用久了会掉耐久（{@code DAMAGE} 组件变了），
     * 但要判断的还是“是不是同一把刀”，用 {@code areItemsAndComponentsEqual} 会认不出来。
     */
    private static boolean sameWeapon(ItemStack stack, ItemStack expected) {
        return !expected.isEmpty() && !stack.isEmpty()
                && stack.getItem() == expected.getItem()
                && stack.getCount() == expected.getCount();
    }

    private static void setHotbar(ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        sendSelectedSlot(player, slot);
    }

    private static void sendSelectedSlot(ClientPlayerEntity player, int slot) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    /** 容器 SWAP 点击：把 {@code handlerSlot} 和 {@code hotbarButton} 那一格互换；点前后处理冲刺/输入包。 */
    private static void clickSwap(ClientPlayerEntity player, int syncId, int handlerSlot,
                                  int hotbarButton) {
        boolean sprinting = player.isSprinting();
        if (sprinting) {
            stopSprint(player);
        }
        flushForInventoryAction(player);
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.interactionManager != null) {
                client.interactionManager.clickSlot(syncId, handlerSlot, hotbarButton,
                        SlotActionType.SWAP, player);
            }
        } finally {
            if (sprinting) {
                startSprint(player);
            }
        }
    }

    /**
     * 点容器前的“别在动”处理（SLM 的 {@code sendNoMultiActionInputPacket} 精简版）：
     * 把输入包清成全部松开，再让原版下一 tick 重发真实输入。
     * 目的：服务端不会觉得你一边走/跳/潜行一边点背包（MultipleActions 一族）。
     */
    private static void flushForInventoryAction(ClientPlayerEntity player) {
        if (player.input == null || player.networkHandler == null) {
            return;
        }
        PlayerInput input = player.input.playerInput;
        if (input == null || !hasMovement(input)) {
            return;
        }
        PlayerInput empty = new PlayerInput(false, false, false, false, false, false, false);
        player.networkHandler.sendPacket(new PlayerInputC2SPacket(empty));
        if (player instanceof ClientPlayerEntityAccessor accessor) {
            // 让原版下一 tick 认为“输入变了”，自动把真实输入重发一次
            accessor.yachiyoc$setLastPlayerInput(empty);
        }
    }

    private static boolean hasMovement(PlayerInput input) {
        return input.forward() || input.backward() || input.left() || input.right()
                || input.jump() || input.sneak() || input.sprint();
    }

    private static void stopSprint(ClientPlayerEntity player) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
        player.setSprinting(false);
    }

    private static void startSprint(ClientPlayerEntity player) {
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        player.setSprinting(true);
    }

    /** 换手前的手持状态，用于原样换回。 */
    private record Pending(int syncId, int handlerSlot, int hotbarButton, int hotbarSlot,
                           int sourceIndex, ItemStack weapon) {
        static Pending hotbar(int slot) {
            return new Pending(-1, -1, -1, slot, -1, ItemStack.EMPTY);
        }

        static Pending inventory(int syncId, int handlerSlot, int hotbarButton, int sourceIndex,
                                 ItemStack weapon) {
            return new Pending(syncId, handlerSlot, hotbarButton, -1, sourceIndex, weapon);
        }
    }

    /** 静默切刀的一轮：换了哪两个格子，用来判断换没换回去。 */
    private static final class SilentSwap {
        /** 当前容器的 syncId 和源格子的 slot 编号（换容器后由 {@link #rebind} 重新算）。 */
        private int syncId;
        private int handlerSlot;
        private final int hotbarButton;
        private final int sourceIndex;
        private final ItemStack weapon;
        /** 补点击的节奏计数。 */
        private int ticks;
        /** 已经补过几次点击。 */
        private int attempts;
        /** 刀回背包格之后又看了几个 tick。 */
        private int watch;

        private SilentSwap(int syncId, int handlerSlot, int hotbarButton, int sourceIndex,
                           ItemStack weapon) {
            this.syncId = syncId;
            this.handlerSlot = handlerSlot;
            this.hotbarButton = hotbarButton;
            this.sourceIndex = sourceIndex;
            this.weapon = weapon;
        }
    }
}
