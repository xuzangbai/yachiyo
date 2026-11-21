package dev.yachiyoc.module.impl.combat;

import dev.yachiyoc.combat.PacketMineTools;
import dev.yachiyoc.mixin.ClientPlayerInteractionManagerAccessor;
import dev.yachiyoc.mixin.ClientWorldAccessor;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.render.Render;
import dev.yachiyoc.render.Render3DCallbacks;
import dev.yachiyoc.render.RenderApi;
import dev.yachiyoc.render.ShapeMode;
import dev.yachiyoc.rotation.RotationChoice;
import dev.yachiyoc.rotation.RotationManager;
import dev.yachiyoc.rotation.RotationMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

public final class PacketMine extends Module {
    private static final String ID = "packet_mine_plus";
    private static final long BLOCK_BREAK_COOLDOWN_MS = 300;
    private static final long CONFIRM_TIMEOUT_MS = 1_250;
    private static final long LOOK_TIMEOUT_MS = 2_000;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long SECONDARY_PAUSE_NANOS = NANOS_PER_TICK;
    private static PacketMine instance;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final BoolSetting usingPause = add(new BoolSetting(ID, "using_pause", true));
    private final BoolSetting onlyMain = add(new BoolSetting(ID, "only_main", true));
    private final BoolSetting preferAnchor = add(new BoolSetting(ID, "prefer_anchor", true));
    private final BoolSetting preferWeb = add(new BoolSetting(ID, "prefer_web", true));
    private final BoolSetting farCancel = add(new BoolSetting(ID, "far_cancel", true));
    private final BoolSetting swing = add(new BoolSetting(ID, "swing", true));
    private final BoolSetting instantMine = add(new BoolSetting(ID, "instant_mine", false));
    private final BoolSetting fastBypass = add(new BoolSetting(ID, "fast_bypass", true));
    private final BoolSetting doubleBreak = add(new BoolSetting(ID, "double_break", false));
    private final BoolSetting rotate = add(new BoolSetting(ID, "rotate", false));
    private final EnumSetting<RotationChoice> rotation =
            add(new EnumSetting<>(ID, "rotation", RotationChoice.class, RotationChoice.FOLLOW));
    private final BoolSetting renderProgress = add(new BoolSetting(ID, "render_progress", true));
    private final BoolSetting clientSwitch = add(new BoolSetting(ID, "client_switch", true));
    private final DoubleSetting range = add(new DoubleSetting(ID, "range", 6, 0, 12, 1));
    private final DoubleSetting instantDelay = add(new DoubleSetting(ID, "instant_delay", 10, 0, 1000, 1));
    private final DoubleSetting switchTime = add(new DoubleSetting(ID, "switch_time", 100, 0, 1000, 1));
    private final DoubleSetting mineDelay = add(new DoubleSetting(ID, "mine_delay", 300, 0, 1000, 1));
    private final DoubleSetting packetDelay = add(new DoubleSetting(ID, "packet_delay", 0, 0, 1000, 1));
    private final DoubleSetting mineDamage = add(new DoubleSetting(ID, "damage", 0.7, 0.6, 2, 0.01));
    private final DoubleSetting animationExp = add(new DoubleSetting(ID, "animation_exponent", 3, 0, 10, 0.1));
    private final EnumSetting<MineSwitchMode> autoSwitch =
            add(new EnumSetting<>(ID, "auto_switch", MineSwitchMode.class, MineSwitchMode.Silent));
    private final EnumSetting<SwingMode> attackSwing =
            add(new EnumSetting<>(ID, "attack_swing", SwingMode.class, SwingMode.Packet));
    private final EnumSetting<HandMode> handMode =
            add(new EnumSetting<>(ID, "hand_mode", HandMode.class, HandMode.MainHand));
    private final EnumSetting<ShapeMode> shapeMode =
            add(new EnumSetting<>(ID, "shape_mode", ShapeMode.class, ShapeMode.BOTH));
    private final ColorSetting targetColor = add(new ColorSetting(ID, "target_color", 0xFF8C8EFF));
    private final ColorSetting secondColor = add(new ColorSetting(ID, "second_color", 0xFF8C8EFF));
    private final ColorSetting sideStartColor = add(new ColorSetting(ID, "side_start", 0x328C8EFF));
    private final ColorSetting sideEndColor = add(new ColorSetting(ID, "side_end", 0x328C8EFF));
    private final ColorSetting lineStartColor = add(new ColorSetting(ID, "line_start", 0xFF8C8EFF));
    private final ColorSetting lineEndColor = add(new ColorSetting(ID, "line_end", 0xFF8C8EFF));
    private final ColorSetting secondSideStartColor = add(new ColorSetting(ID, "second_side_start", 0x328C8EFF));
    private final ColorSetting secondSideEndColor = add(new ColorSetting(ID, "second_side_end", 0x328C8EFF));
    private final ColorSetting secondLineStartColor = add(new ColorSetting(ID, "second_line_start", 0xFF8C8EFF));
    private final ColorSetting secondLineEndColor = add(new ColorSetting(ID, "second_line_end", 0xFF8C8EFF));

    public BlockPos anchorTarget;
    public BlockPos webTarget;

    public BlockPos selfClickPos;
    public int publicProgress;
    public boolean completed;
    public BlockPos targetPos, secondPos;
    private float progress;
    private float secondProgress;
    private ClientWorld miningWorld;
    private long lastTickNanos;
    private long secondTickNanos;
    private long secondFinishNanos;
    private long startNanos;
    private long attemptId;
    private float fastestDelta;
    private boolean started, awaitingBreak;
    private boolean extraCandidate;
    private boolean extraPrepared;
    private boolean reusableStart;
    private boolean secondToolHeld;
    private int toolSlot = -1;
    private int primaryHeldSlot = -1;
    private int extraHeldSlot = -1;
    private ItemStack miningTool = ItemStack.EMPTY;
    private int serverSlot = -1;
    private int switchedSlot = -1;
    private int oldSlot = -1;
    private final Timer timer = new Timer();
    private final Timer mineTimer = new Timer();
    private final Timer stateTimer = new Timer();
    private boolean hasSwitch;
    private boolean changedClientSlot;

    public PacketMine() {
        super(ID, ModuleCategory.COMBAT);
        instance = this;
        onlyMain.visibleWhen(usingPause::get);
        packetDelay.visibleWhen(doubleBreak::get);
        rotation.visibleWhen(rotate::get);
        preferAnchor.description(() -> Text.translatable(preferAnchor.translationKey() + ".description"));
        preferWeb.description(() -> Text.translatable(preferWeb.translationKey() + ".description"));
        group("visuals", animationExp, renderProgress, targetColor, secondColor, shapeMode,
                sideStartColor, sideEndColor, lineStartColor, lineEndColor,
                secondSideStartColor, secondSideEndColor, secondLineStartColor, secondLineEndColor);
        reset();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (ready()) tickMining();
        });
        Render3DCallbacks.register(this::renderMining);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static PacketMine active() {
        return instance != null && instance.ready() ? instance : null;
    }

    private boolean ready() {
        return enabled() && mc.world != null && mc.player != null && mc.getNetworkHandler() != null;
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.world == miningWorld && mc.getNetworkHandler() != null) abortPrimary();
        reset();
    }

    public void onSelectedSlot(int slot) {
        serverSlot = slot;
        if (hasSwitch && slot != switchedSlot) hasSwitch = false;
    }

    public void onServerBlockUpdate(BlockPos pos, BlockState state) {
        if (mc.world != miningWorld || !state.isAir()) return;
        if (started && pos.equals(targetPos)) confirmBreak();
        if (pos.equals(secondPos)) clearSecondary();
    }

    public boolean onStartBreakingBlock(BlockPos pos, Direction side) {
        if (mc.player.isCreative() || mc.player.isSpectator() || !PacketMineTools.canBreak(mc, pos)) return false;
        checkWorld();
        if (pos.equals(targetPos) || pos.equals(secondPos) || awaitingBreak) return true;
        boolean transferring = doubleBreak.get() && secondPos == null && extraPrepared && started && !mc.world.isAir(targetPos);
        if (transferring) {
            secondPos = targetPos;
            float displayedProgress = (float) MathHelper.clamp(progress / finishProgress(), 0, 1);
            secondProgress = Math.min(progress, displayedProgress * 0.90F);
            secondTickNanos = System.nanoTime() + SECONDARY_PAUSE_NANOS;
            extraHeldSlot = primaryHeldSlot;
        } else {
            abortPrimary();
        }
        selectPrimary(pos);
        selfClickPos = targetPos;
        if (!paused() && cooldownElapsed(0)) startPrimary(side);
        return true;
    }

    public String getInfoString() {
        if (targetPos == null) return null;
        return completed ? "[100%]" : "[" + publicProgress + "%]";
    }

    private void tickMining() {
        checkWorld();
        if (!doubleBreak.get() || secondPos != null && !canContinue(secondPos)) clearSecondary();
        if (targetPos != null) {
            if (awaitingBreak) {
                awaitBreak();
            } else if (outOfRange(targetPos) || (!mc.world.isAir(targetPos) && !canContinue(targetPos))) {
                abortPrimary();
            } else if (completed) {
                tryRebreak();
            } else if (mc.world.isAir(targetPos)) {
                confirmBreak();
            } else {
                advancePrimary();
            }
        }

        if (secondPos != null) advanceSecondary();
        if (hasSwitch && !started && !awaitingBreak && !secondToolHeld && timer.passedMs(switchTime.get().longValue())) restoreSlot();
    }

    private void checkWorld() {
        if (miningWorld != mc.world) reset();
    }

    private void advancePrimary() {
        if (!started) {
            if (!paused() && cooldownElapsed(0) && actOnTarget(this::startPrimary)) return;
            if (stateTimer.passedMs(LOOK_TIMEOUT_MS)) {
                abandonUnconfirmed();
            }
            return;
        }

        int heldSlot = primaryHeldSlot >= 0 ? primaryHeldSlot : toolSlot;
        if (heldSlot != currentServerSlot() || !sameTool(mc.player.getInventory().getStack(toolSlot))) {
            BlockPos pos = targetPos;
            abortPrimary();
            selectPrimary(pos);
            return;
        }

        long now = System.nanoTime();
        double ticks = Math.min(1.0, (now - lastTickNanos) / (double) NANOS_PER_TICK);
        lastTickNanos = now;
        if (paused()) return;

        if (extraCandidate && doubleBreak.get() && !extraPrepared && secondPos == null && progress < finishProgress()
                && now - startNanos >= packetDelay.get().longValue() * 1_000_000L) {
            actOnTarget(this::prepareExtra);
        }

        double threshold = finishProgress();
        if (progress < threshold) {
            float delta = PacketMineTools.breakingDelta(mc, targetPos, toolSlot);
            fastestDelta = Math.max(fastestDelta, delta);
            progress += delta * ticks;
            publicProgress = MathHelper.clamp((int) (progress / threshold * 100), 0, 99);
            stateTimer.reset();
            if (progress < threshold) return;
        }
        double requiredTicks = Math.ceil(threshold / fastestDelta);
        if (threshold < 1) requiredTicks++;
        if (now - startNanos < requiredTicks * NANOS_PER_TICK) return;
        boolean canFinish = !finishBlocked() && actOnTarget(this::sendFinish);
        if (!canFinish && stateTimer.passedMs(LOOK_TIMEOUT_MS)) abandonUnconfirmed();
    }

    private void startPrimary(Direction side) {
        toolSlot = getTool(targetPos);
        miningTool = mc.player.getInventory().getStack(toolSlot).copy();
        fastestDelta = PacketMineTools.breakingDelta(mc, targetPos, toolSlot);
        reusableStart = fastestDelta < 1;
        extraCandidate = reusableStart && doubleBreak.get() && secondPos == null;
        if (secondPos != null && reusableStart) {
            primaryHeldSlot = extraHeldSlot;
        } else if (extraCandidate) {
            primaryHeldSlot = slowerSlot(targetPos, fastestDelta);
        }
        if (primaryHeldSlot >= 0) equipTool(primaryHeldSlot, false, true);
        else equipTool(toolSlot);
        sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, side);
        if (fastBypass.get()) {
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos.up(1420), side);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
        started = true;
        startNanos = lastTickNanos = System.nanoTime();
        stateTimer.reset();
        if (!reusableStart) waitForConfirmation();
        else if (packetDelay.get() == 0) prepareExtra(side);
    }

    private boolean sameTool(ItemStack current) {
        if (ItemStack.areItemsAndComponentsEqual(miningTool, current)) return true;
        if (!current.isOf(miningTool.getItem()) || !current.isDamageable() || current.getDamage() == miningTool.getDamage()) {
            return false;
        }
        ItemStack comparison = current.copy();
        comparison.setDamage(miningTool.getDamage());
        if (!ItemStack.areItemsAndComponentsEqual(miningTool, comparison)) return false;
        miningTool = current.copy();
        return true;
    }

    private double finishProgress() {
        return fastBypass.get() ? mineDamage.get() : Math.max(1, mineDamage.get());
    }

    private void prepareExtra(Direction side) {
        if (extraCandidate && doubleBreak.get() && secondPos == null && !extraPrepared) {
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, side);
            extraPrepared = true;
            mineTimer.reset();
        }
    }

    private void sendFinish(Direction side) {
        equipTool(toolSlot, true, false);
        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, side);
        if (swing.get()) attackSwingHand();
        waitForConfirmation();
    }

    private void waitForConfirmation() {
        awaitingBreak = true;
        stateTimer.reset();
        mineTimer.reset();
    }

    private void awaitBreak() {
        if (mc.world.isAir(targetPos)) {
            confirmBreak();
        } else if (stateTimer.passedMs(CONFIRM_TIMEOUT_MS) || outOfRange(targetPos)) {
            abandonUnconfirmed();
        }
    }

    private void confirmBreak() {
        if (instantMine.get() && started) {
            completed = true;
            started = false;
            awaitingBreak = false;
            publicProgress = 100;
            stateTimer.reset();
            selfClickPos = null;
        } else {
            clearPrimary();
        }
        timer.reset();
        mineTimer.reset();
    }

    private void tryRebreak() {
        if (!instantMine.get()) {
            clearPrimary();
            return;
        }
        if (!PacketMineTools.canBreak(mc, targetPos) || paused()
                || !mineTimer.passedMs(instantDelay.get().longValue())
                || !reusableStart && !cooldownElapsed(0)) return;
        actOnTarget(side -> {
            if (!reusableStart) {
                selectPrimary(targetPos);
                startPrimary(side);
            } else {
                toolSlot = getTool(targetPos);
                started = true;
                sendFinish(side);
            }
        });
    }

    private void advanceSecondary() {
        long now = System.nanoTime();
        if (now <= secondTickNanos) return;
        if (secondFinishNanos != 0 && now - secondFinishNanos >= CONFIRM_TIMEOUT_MS * 1_000_000L) {
            clearSecondary();
            return;
        }
        int slot = getTool(secondPos);
        double ticks = Math.min(1.0, (now - secondTickNanos) / (double) NANOS_PER_TICK);
        secondTickNanos = now;
        secondProgress = Math.min(1, secondProgress + (float) (PacketMineTools.breakingDelta(mc, secondPos, slot) * ticks));
        if (secondProgress < 1 || started || awaitingBreak || paused()) return;

        if (secondFinishNanos == 0) secondFinishNanos = now;
        if (!secondToolHeld) {
            equipTool(slot);
            secondToolHeld = hasSwitch;
        }
    }

    private boolean cooldownElapsed(long delay) {
        return mineTimer.passedMs(Math.max(BLOCK_BREAK_COOLDOWN_MS, mineDelay.get().longValue()) + delay);
    }

    private boolean actOnTarget(Consumer<Direction> action) {
        if (!mc.player.canInteractWithBlockAt(targetPos, 0)) return false;
        RotationMode mode = miningRotation();
        if (mode != RotationMode.NONE && RotationManager.hasPendingAttack()) return false;

        HitResult aimed = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (aimed instanceof BlockHitResult hit && isTargetHit(hit)) {
            action.accept(hit.getSide());
            return true;
        }
        BlockHitResult hit = PacketMineTools.reachableHit(mc, targetPos);
        if (hit == null) return false;
        if (mode == RotationMode.NONE) {
            action.accept(hit.getSide());
            return true;
        }

        Vec3d delta = hit.getPos().subtract(mc.player.getEyePos());
        float[] angles = RotationManager.anglesTo(delta.x, delta.y, delta.z);
        long scheduledAttempt = attemptId;
        Runnable finish = () -> {
            if (ready() && scheduledAttempt == attemptId && mc.world == miningWorld && !awaitingBreak && !paused()
                    && miningRotation() == mode && canContinue(targetPos) && mc.player.canInteractWithBlockAt(targetPos, 0)) {
                Vec3d eye = mc.player.getEyePos();
                Vec3d end = eye.add(Vec3d.fromPolar(angles[1], angles[0]).multiply(mc.player.getBlockInteractionRange()));
                BlockHitResult current = PacketMineTools.raycast(mc, eye, end);
                if (isTargetHit(current)) action.accept(current.getSide());
            }
        };
        if (mode == RotationMode.DELAY_MOVEMENT || mode == RotationMode.MOVEMENT_POST) {
            return RotationManager.runAfterMovementAim(angles[0], angles[1], finish);
        }
        RotationManager.run(mode, angles[0], angles[1], finish);
        return true;
    }

    private RotationMode miningRotation() {
        return rotate.get() ? RotationManager.resolve(rotation.get()) : RotationMode.NONE;
    }

    private boolean isTargetHit(BlockHitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK && targetPos.equals(hit.getBlockPos());
    }

    private boolean finishBlocked() {
        return preferAnchor.get() && anchorTarget != null || preferWeb.get() && targetPos.equals(webTarget)
                && mc.world.getBlockState(targetPos).isOf(Blocks.COBWEB);
    }

    private boolean canContinue(BlockPos pos) {
        return !mc.world.isAir(pos) && !outOfRange(pos) && PacketMineTools.canBreak(mc, pos);
    }

    private boolean outOfRange(BlockPos pos) {
        return farCancel.get() && mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get();
    }

    private boolean paused() {
        return usingPause.get() && checkPause(onlyMain.get());
    }

    private void selectPrimary(BlockPos pos) {
        clearPrimary();
        targetPos = pos.toImmutable();
        stateTimer.reset();
    }

    private void clearPrimary() {
        attemptId++;
        targetPos = null;
        started = false;
        awaitingBreak = false;
        completed = false;
        extraCandidate = false;
        extraPrepared = false;
        reusableStart = false;
        publicProgress = 0;
        progress = 0;
        selfClickPos = null;
        toolSlot = -1;
        primaryHeldSlot = -1;
        miningTool = ItemStack.EMPTY;
    }

    private void abortPrimary() {
        if (started && !awaitingBreak && !extraPrepared && !mc.world.isAir(targetPos)) {
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, targetPos, Direction.DOWN));
        }
        clearPrimary();
        restoreSlot();
    }

    private void abandonUnconfirmed() {
        abortPrimary();
        clearSecondary();
    }

    private void clearSecondary() {
        if (secondToolHeld) timer.reset();
        secondPos = null;
        secondProgress = 0;
        extraHeldSlot = -1;
        secondFinishNanos = 0;
        secondToolHeld = false;
    }

    private void sendAction(PlayerActionC2SPacket.Action action, BlockPos pos, Direction side) {
        sendSequencedPacket(id -> new PlayerActionC2SPacket(action, pos, side, id));
    }

    private void renderMining() {
        if (!ready() || (targetPos == null && secondPos == null)) return;
        RenderApi render = Render.api();
        boolean throughWalls = render.throughWalls();
        render.throughWalls(true);
        try {
            if (targetPos != null) renderAnimation(render, targetPos, completed ? 1 : progress / finishProgress(),
                    sideStartColor.argb(), sideEndColor.argb(), lineStartColor.argb(), lineEndColor.argb());
            if (secondPos != null) renderAnimation(render, secondPos, secondProgress,
                    secondSideStartColor.argb(), secondSideEndColor.argb(), secondLineStartColor.argb(), secondLineEndColor.argb());
        } finally {
            render.throughWalls(throughWalls);
        }
        if (renderProgress.get() && !mc.options.hudHidden) renderProgressText(render);
    }

    private void renderAnimation(RenderApi render, BlockPos pos, double fraction,
                                 int sideStart, int sideEnd, int lineStart, int lineEnd) {
        double p = 1 - Math.pow(1 - MathHelper.clamp(fraction, 0, 1), animationExp.get());
        double size = Math.max(0.025, p / 2);
        Box box = new Box(pos.toCenterPos(), pos.toCenterPos()).expand(size);
        render.box(box, getColor(sideStart, sideEnd, p), getColor(lineStart, lineEnd, p), shapeMode.get());
    }

    private void renderProgressText(RenderApi render) {
        if (targetPos != null) {
            Text label = completed ? Text.translatable("yachiyoc.packet_mine.done") : awaitingBreak
                    ? Text.translatable("yachiyoc.packet_mine.confirming") : Text.literal(publicProgress + "%");
            renderLabel(render, targetPos, label, targetColor.argb());
        }
        if (secondPos != null) {
            Text label = secondFinishNanos == 0 ? Text.literal(Math.min(99, (int) (secondProgress * 100)) + "%")
                    : Text.translatable("yachiyoc.packet_mine.confirming");
            renderLabel(render, secondPos, label, secondColor.argb());
        }
    }

    private void renderLabel(RenderApi render, BlockPos pos, Text label, int color) {
        Vec3d anchor = pos.toCenterPos();
        float pixelSize = render.billboardPixelSize(anchor);
        if (pixelSize <= 0) return;
        float size = 1.5F * pixelSize * (float) Math.max(0.5, 1 - render.cameraPos().distanceTo(anchor) * 0.01);
        Vec3d origin = render.billboardOffset(anchor, 0, mc.textRenderer.fontHeight * size / 2);
        render.text(label, origin, size / 0.025F, color, true, true);
    }

    private static int getColor(int start, int end, double progress) {
        int alpha = lerp(start >>> 24, end >>> 24, progress);
        int red = lerp(start >>> 16 & 255, end >>> 16 & 255, progress);
        int green = lerp(start >>> 8 & 255, end >>> 8 & 255, progress);
        int blue = lerp(start & 255, end & 255, progress);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int lerp(double start, double end, double d) {
        return (int) Math.round(start + (end - start) * d);
    }
    private int getTool(BlockPos pos) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (autoSwitch.get() == MineSwitchMode.None) return selected;
        int bestSlot = selected;
        float bestDamage = PacketMineTools.breakingDelta(mc, pos, selected);
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected || mc.player.getInventory().getStack(slot).isEmpty()) continue;
            float damage = PacketMineTools.breakingDelta(mc, pos, slot);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int slowerSlot(BlockPos pos, float fastest) {
        if (autoSwitch.get() == MineSwitchMode.None) return -1;
        int selected = mc.player.getInventory().getSelectedSlot();
        int slowestSlot = selected;
        float slowest = PacketMineTools.breakingDelta(mc, pos, selected);
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) continue;
            float delta = PacketMineTools.breakingDelta(mc, pos, slot);
            if (delta < slowest) {
                slowest = delta;
                slowestSlot = slot;
            }
        }
        return slowest < fastest ? slowestSlot : -1;
    }
    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = ((ClientWorldAccessor) mc.world).yachiyoc$getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }
    public boolean checkPause(boolean onlyMain) {
        return mc.options.useKey.isPressed() && (!onlyMain || mc.player.getActiveHand() == Hand.MAIN_HAND);
    }

    private void equipTool(int slot) {
        equipTool(slot, true, true);
    }

    private void equipTool(int slot, boolean updateClient, boolean flush) {
        if (autoSwitch.get() == MineSwitchMode.None) return;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (serverSlot < 0) serverSlot = selected;
        if (!hasSwitch && serverSlot == slot && selected == slot) return;

        if (!hasSwitch) {
            oldSlot = selected;
            changedClientSlot = false;
        }
        if (updateClient && autoSwitch.get() == MineSwitchMode.Delay && clientSwitch.get() && selected != slot) {
            setClientSlot(slot);
            changedClientSlot = true;
        }
        hasSwitch = true;
        switchedSlot = slot;
        sendSlot(slot, flush);
        timer.reset();
    }

    private int currentServerSlot() {
        return serverSlot < 0 ? mc.player.getInventory().getSelectedSlot() : serverSlot;
    }

    private void restoreSlot() {
        if (!hasSwitch) return;
        int selected = mc.player.getInventory().getSelectedSlot();
        hasSwitch = false;
        if (changedClientSlot && selected == switchedSlot) {
            selected = oldSlot;
            setClientSlot(selected);
        }
        changedClientSlot = false;
        sendSlot(selected, true);
    }

    private void setClientSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).yachiyoc$setLastSelectedSlot(slot);
    }

    private void sendSlot(int slot, boolean flush) {
        if (serverSlot == slot) return;
        serverSlot = slot;
        mc.getNetworkHandler().getConnection().send(new UpdateSelectedSlotC2SPacket(slot), null, flush);
    }

    private void attackSwingHand() {
        Hand hand = handMode.get() == HandMode.MainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (attackSwing.get() != SwingMode.Packet && attackSwing.get() != SwingMode.None) {
            mc.player.swingHand(hand);
        }
        if (attackSwing.get() != SwingMode.Client && attackSwing.get() != SwingMode.None) {
            sendPacket(new HandSwingC2SPacket(hand));
        }
    }

    private void sendPacket(Packet<?> packet) {
        mc.getNetworkHandler().sendPacket(packet);
    }

    public void reset() {
        hasSwitch = false;
        changedClientSlot = false;
        serverSlot = -1;
        switchedSlot = -1;
        miningWorld = mc.world;
        mineTimer.setMs(999999);
        clearPrimary();
        clearSecondary();
    }
    private enum MineSwitchMode { Delay, Silent, None }
    private enum SwingMode { Both, Packet, Client, None }
    private enum HandMode { MainHand, OffHand }

    private static final class Timer {
        private long time = System.nanoTime();

        private void reset() {
            time = System.nanoTime();
        }

        private boolean passedMs(long ms) {
            return System.nanoTime() - time >= ms * 1_000_000L;
        }

        private void setMs(long ms) {
            time = System.nanoTime() - ms * 1_000_000L;
        }
    }
}
