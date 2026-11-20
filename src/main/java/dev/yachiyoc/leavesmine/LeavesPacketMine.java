/*
 * Mining state machine ported from LeavesHack PacketMine+ (AGPL-3.0).
 * See META-INF/licenses/leaveshack.txt.
 */
package dev.yachiyoc.leavesmine;

import dev.yachiyoc.leavesmine.LeavesMineRender.ShapeMode;
import dev.yachiyoc.mixin.LeavesMineClientWorldAccessor;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.TimerTask;

public final class LeavesPacketMine extends Module {
    private static final String ID = "packet_mine_plus";
    private static LeavesPacketMine instance;
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
    private final BoolSetting checkGround = add(new BoolSetting(ID, "check_ground", true));
    private final BoolSetting bypassGround = add(new BoolSetting(ID, "bypass_ground", false));
    private final BoolSetting renderProgress = add(new BoolSetting(ID, "render_progress", true));
    private final BoolSetting clientSwitch = add(new BoolSetting(ID, "client_switch", true));
    private final DoubleSetting range = add(new DoubleSetting(ID, "range", 6, 0, 12, 1));
    private final DoubleSetting maxBreaks = add(new DoubleSetting(ID, "try_break_time", 6, 0, 10, 1));
    private final DoubleSetting instantDelay = add(new DoubleSetting(ID, "instant_delay", 10, 0, 1000, 1));
    private final DoubleSetting switchDamage = add(new DoubleSetting(ID, "switch_damage", 95, 0, 100, 1));
    private final DoubleSetting switchTime = add(new DoubleSetting(ID, "switch_time", 100, 0, 1000, 1));
    private final DoubleSetting mineDelay = add(new DoubleSetting(ID, "mine_delay", 300, 0, 1000, 1));
    private final DoubleSetting packetDelay = add(new DoubleSetting(ID, "packet_delay", 0, 0, 1000, 1));
    private final DoubleSetting mineDamage = add(new DoubleSetting(ID, "damage", 0.8, 0, 2, 0.01));
    private final DoubleSetting animationExp = add(new DoubleSetting(ID, "animation_exponent", 3, 0, 10, 0.1));
    private final EnumSetting<MineSwitchMode> autoSwitch =
            add(new EnumSetting<>(ID, "auto_switch", MineSwitchMode.class, MineSwitchMode.Silent));
    private final EnumSetting<SwingMode> attackSwing =
            add(new EnumSetting<>(ID, "attack_swing", SwingMode.class, SwingMode.Packet));
    private final EnumSetting<HandMode> handMode =
            add(new EnumSetting<>(ID, "hand_mode", HandMode.class, HandMode.MainHand));
    private final EnumSetting<ShapeMode> shapeMode =
            add(new EnumSetting<>(ID, "shape_mode", ShapeMode.class, ShapeMode.Both));
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

    // The original priority checks read these positions from the LeavesHack companion modules.
    public BlockPos anchorTarget;
    public BlockPos webTarget;

    public BlockPos selfClickPos;
    public int maxBreaksCount;
    public int publicProgress, secondPublicProgress;
    public boolean completed;
    public BlockPos targetPos, secondPos;
    private float progress, secondProgress;
    private long lastTime, secondLastTime;
    private boolean started, secondStarted;
    private int oldSlot = -1;
    private final LeavesMineTimer timer = new LeavesMineTimer();
    public final LeavesMineTimer mineTimer = new LeavesMineTimer();
    private final LeavesMineTimer instantTimer = new LeavesMineTimer();
    private boolean hasSwitch;
    private final LeavesMineRender renderer = new LeavesMineRender();

    public LeavesPacketMine() {
        super(ID, ModuleCategory.COMBAT);
        instance = this;
        onlyMain.visibleWhen(usingPause::get);
        preferAnchor.description(() -> Text.translatable(preferAnchor.translationKey() + ".description"));
        preferWeb.description(() -> Text.translatable(preferWeb.translationKey() + ".description"));
        group("visuals", animationExp, renderProgress, targetColor, secondColor, shapeMode,
                sideStartColor, sideEndColor, lineStartColor, lineEndColor,
                secondSideStartColor, secondSideEndColor, secondLineStartColor, secondLineEndColor);
        reset();
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (ready()) {
                renderer.begin(context, mc);
                try {
                    renderFrame(renderer);
                } finally {
                    renderer.end();
                }
            }
        });
        HudElementRegistry.addLast(Identifier.of("yachiyoc", ID), (context, ignored) -> {
            if (ready()) renderProgressText(context);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static LeavesPacketMine active() {
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
        if (hasSwitch && mc.player != null && mc.getNetworkHandler() != null) {
            switchToSlot(oldSlot);
        }
        hasSwitch = false;
    }

    public void onPacketSend(Packet<?> packet) {
        if (packet instanceof PlayerActionC2SPacket action
                && action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            mineTimer.reset();
        }
    }

    public boolean onStartBreakingBlock(BlockPos pos) {
        if (!LeavesMineSupport.canBreak(mc, pos)) return false;
        if (mineDelay.get() > 0 && !mineTimer.passedMs(mineDelay.get().longValue()) && completed) {
            targetPos = null;
            publicProgress = 0;
            started = false;
            progress = 0;
            completed = false;
            return true;
        }
        if (!mineTimer.passedMs(mineDelay.get().longValue())) return true;
        selfClickPos = pos;
        mine(pos);
        return true;
    }

    public void mine(BlockPos pos) {
        mineTimer.reset();
        maxBreaksCount = 0;
        if (doubleBreak.get()) {
            if (targetPos != null && secondPos == null && !targetPos.equals(pos)) {
                if (completed) {
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondPublicProgress = 0;
                    publicProgress = 0;
                    started = false;
                    progress = 0;
                    completed = false;
                } else {
                    secondPos = targetPos;
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondPublicProgress = 0;
                    started = false;
                }
            } else if (targetPos == null || !targetPos.equals(pos)){
                publicProgress = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        } else {
            if (!pos.equals(targetPos)) {
                publicProgress = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        }
        mineTimer.reset();
    }
    public String getInfoString() {
        if (mc.player == null || mc.world == null) return null;
        if (targetPos == null) return null;
        double max = getMineTicks(getTool(targetPos));
        if (progress >= max * mineDamage.get()) return "[100%]";
        return "[" + publicProgress + "%]";
    }

    private void renderFrame(LeavesMineRender frame) {
        if ((targetPos == null || completed) && secondPos == null) selfClickPos = null;
        if (publicProgress >= 100) {
            if (!instantMine.get()) targetPos = null;
        }
        if (secondPublicProgress >= 100) {
            secondPos = null;
        }
        if (timer.passedMs(switchTime.get().longValue()) && hasSwitch && autoSwitch.get() != MineSwitchMode.None) {
            if (autoSwitch.get() == MineSwitchMode.Delay) switchToSlot(oldSlot);
            if (autoSwitch.get() == MineSwitchMode.Silent) sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
            hasSwitch = false;
        }
        if (maxBreaksCount >= maxBreaks.get() * 10) {
            maxBreaksCount = 0;
            targetPos = null;
            secondPos = null;
        }
        if (secondPos != null && doubleBreak.get()) {
            if (farCancel.get() && Math.sqrt(mc.player.getEyePos().squaredDistanceTo(secondPos.toCenterPos())) > range.get()){
                secondPos = null;
                return;
            }
            double secondMax = getMineTicks2(getTool(secondPos));
            double secondDelta = (System.currentTimeMillis() - secondLastTime) / 1000d;
            secondPublicProgress = (int) (secondProgress / (secondMax * mineDamage.get()) * 100);
            secondLastTime = System.currentTimeMillis();
            if (!secondStarted) {
                sendStart(secondPos);
                secondStarted = true;
                secondProgress = 0;
                return;
            }
            Double secondDamage = mineDamage.get();
            if (!checkGround.get() || mc.player.isOnGround()) {
                secondProgress += secondDelta * 20;
            } else if (checkGround.get() && !mc.player.isOnGround()){
                secondProgress += secondDelta * 4;
            }
            renderSecondAnimation(frame, secondDamage);
            if (secondProgress >= secondMax * secondDamage) {
                countMaxBreaks(secondPos);
                sendStopSecond();
            }
        }
        if (doubleBreak.get()) {
            if (!usingPause.get() || !checkPause(onlyMain.get())) {
                if ((secondPublicProgress >= switchDamage.get() || publicProgress >= switchDamage.get())&& !hasSwitch && secondPos != null) {
                    int bestSlot = getTool(secondPos);
                    if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
                    if (autoSwitch.get() != MineSwitchMode.None && bestSlot != -1) {
                        if (autoSwitch.get() == MineSwitchMode.Delay) switchToSlot(bestSlot);
                        if (autoSwitch.get() == MineSwitchMode.Silent) sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                        timer.reset();
                        hasSwitch = true;
                    }
                }
            }
        }
        if (targetPos != null) {
            if (farCancel.get() && Math.sqrt(mc.player.getEyePos().squaredDistanceTo(targetPos.toCenterPos())) > range.get()){
                targetPos = null;
                return;
            }
            double max = getMineTicks(getTool(targetPos));
            publicProgress = (int) (progress / (max * mineDamage.get()) * 100);
            if (progress >= max * mineDamage.get() && completed) {
                countMaxBreaks(targetPos);
            }
            if (instantMine.get() && completed) {
                int side = getColor(sideStartColor.argb(), sideEndColor.argb(), 1);
                int line = getColor(lineStartColor.argb(), lineEndColor.argb(), 1);
                frame.box(new Box(targetPos), side, line, shapeMode.get());
                if (!mc.world.isAir(targetPos) && !mc.world.getBlockState(targetPos).isReplaceable() && instantTimer.passedMs(instantDelay.get().longValue())) {
                    sendStop();
                    instantTimer.reset();
                }
                return;
            }
            double delta = (System.currentTimeMillis() - lastTime) / 1000d;
            lastTime = System.currentTimeMillis();
            if (!started) {
                sendStart(targetPos);
                return;
            }
            Double damage = mineDamage.get();
            if (!checkGround.get() || mc.player.isOnGround()) {
                progress += delta * 20;
            } else if (checkGround.get() && !mc.player.isOnGround()) {
                progress += delta * 4;
            }
            renderAnimation(frame, damage);
            if (progress >= max * damage) {
                if (!instantMine.get()) sendStop();
                completed = true;
                if (!instantMine.get() && secondPos == null) targetPos = null;
            }
        }
    }

    private void countMaxBreaks(BlockPos pos) {
        if (isAir(pos) || mc.world.getBlockState(pos).isReplaceable()) {
            maxBreaksCount = 0;
            return;
        }
        if (usingPause.get() && checkPause(onlyMain.get())) return;
        maxBreaksCount ++;
    }

    private void sendStart(BlockPos pos) {
        sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, LeavesMineSupport.getClickSide(mc, pos), id));
        if (fastBypass.get()) {
            BlockPos bypassPos = new BlockPos(0, -999, 0);
            sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.UP, id));
        }
        if (doubleBreak.get()) {
            long delay = packetDelay.get().longValue();
            var world = mc.world;
            java.util.Timer timer = new java.util.Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        if (!enabled() || mc.world != world) return;
                        sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, LeavesMineSupport.getClickSide(mc, pos), id));
                    });
                    timer.cancel();
                }
            }, delay);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
        if (pos.equals(targetPos)) {
            started = true;
            progress = 0;
        } else {
            secondStarted = true;
            secondProgress = 0;
        }
    }

    private void sendStop() {
        if (preferAnchor.get() && anchorTarget != null) {
            return;
        }
        if (preferWeb.get() && webTarget != null && webTarget.equals(targetPos) && mc.world.getBlockState(targetPos).getBlock() == Blocks.COBWEB) {
            return;
        }
        if (usingPause.get() && checkPause(onlyMain.get())) {
            return;
        }
        int bestSlot = getTool(targetPos);
        if (!doubleBreak.get() || secondPos == null) {
            if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
            if (autoSwitch.get() != MineSwitchMode.None && bestSlot != -1) {
                if (autoSwitch.get() == MineSwitchMode.Delay) switchToSlot(bestSlot);
                if (autoSwitch.get() == MineSwitchMode.Silent) sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
                timer.reset();
                hasSwitch = true;
            }
        }
        if (bypassGround.get() && !mc.player.isGliding() && targetPos != null && !isAir(targetPos) && !mc.player.isOnGround()){
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true, mc.player.horizontalCollision));
            mc.player.onLanding();
        }
        if (swing.get()) attackSwingHand();
        sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, LeavesMineSupport.getClickSide(mc, targetPos), id));
    }
    private void sendStopSecond() {
        // The second slot's STOP is queued by sendStart above.
        if (bypassGround.get() && !mc.player.isGliding() && secondPos != null && !isAir(secondPos) && !mc.player.isOnGround()){
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true, mc.player.horizontalCollision));
            mc.player.onLanding();
        }
    }
    private boolean isAir(BlockPos breakPos) {
        return mc.world.isAir(breakPos) || mc.world.getBlockState(breakPos).getBlock() == Blocks.FIRE && LeavesMineSupport.hasCrystal(mc, breakPos);
    }
    private float getMineTicks(int slot) {
        if (targetPos == null || mc.world == null || mc.player == null) return 20;
        BlockState state = mc.world.getBlockState(targetPos);
        float hardness = state.getHardness(mc.world, targetPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1
                ? ItemStack.EMPTY
                : mc.player.getInventory().getStack(slot);
        boolean canHarvest = stack.isSuitableFor(state);
        float speed = stack.getMiningSpeedMultiplier(state);
        int efficiency = LeavesMineSupport.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasStatusEffect(StatusEffects.HASTE)) {
            int amp = mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            int amp = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }
    private float getMineTicks2(int slot) {
        if (secondPos == null || mc.world == null || mc.player == null) return 20;
        BlockState state = mc.world.getBlockState(secondPos);
        float hardness = state.getHardness(mc.world, secondPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1
                ? ItemStack.EMPTY
                : mc.player.getInventory().getStack(slot);
        boolean canHarvest = stack.isSuitableFor(state);
        float speed = stack.getMiningSpeedMultiplier(state);
        int efficiency = LeavesMineSupport.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasStatusEffect(StatusEffects.HASTE)) {
            int amp = mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            int amp = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }

    private void renderAnimation(LeavesMineRender frame, double damage) {
        double max = getMineTicks(getTool(targetPos));
        double p = 1 - MathHelper.clamp(progress / (max * damage), 0, 1);
        p = Math.pow(p, animationExp.get());
        p = 1 - p;
        double size = p / 2;
        Box box = new Box(
                targetPos.getX() + 0.5 - size,
                targetPos.getY() + 0.5 - size,
                targetPos.getZ() + 0.5 - size,
                targetPos.getX() + 0.5 + size,
                targetPos.getY() + 0.5 + size,
                targetPos.getZ() + 0.5 + size
        );

        int side = getColor(sideStartColor.argb(), sideEndColor.argb(), p);
        int line = getColor(lineStartColor.argb(), lineEndColor.argb(), p);
        frame.box(box, side, line, shapeMode.get());
    }
    private void renderSecondAnimation(LeavesMineRender frame, double damage) {
        double max = getMineTicks2(getTool(secondPos));
        double p = 1 - MathHelper.clamp(secondProgress / (max * damage), 0, 1);
        p = Math.pow(p, animationExp.get());
        p = 1 - p;

        double size = p / 2;
        Box box = new Box(
                secondPos.getX() + 0.5 - size,
                secondPos.getY() + 0.5 - size,
                secondPos.getZ() + 0.5 - size,
                secondPos.getX() + 0.5 + size,
                secondPos.getY() + 0.5 + size,
                secondPos.getZ() + 0.5 + size
        );

        int side = getColor(secondSideStartColor.argb(), secondSideEndColor.argb(), p);
        int line = getColor(secondLineStartColor.argb(), secondLineEndColor.argb(), p);

        frame.box(box, side, line, shapeMode.get());
    }

    private void renderProgressText(DrawContext context) {
        if (!renderProgress.get()) return;
        if (targetPos != null) {
            renderer.label(context, mc, targetPos.toCenterPos(), completed ? "Done" : publicProgress + "%",
                    targetColor.argb());
        }
        if (secondPos != null) {
            renderer.label(context, mc, secondPos.toCenterPos(), secondPublicProgress + "%",
                    secondColor.argb());
        }
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
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != ItemStack.EMPTY) {
                final float digSpeed = LeavesMineSupport.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
                final float destroySpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }
    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = ((LeavesMineClientWorldAccessor) mc.world).yachiyoc$getLeavesMinePendingUpdates().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }
    public boolean checkPause(boolean onlyMain) {
        return mc.options.useKey.isPressed() && (!onlyMain || mc.player.getActiveHand() == Hand.MAIN_HAND);
    }

    private void switchToSlot(int slot) {
        if (clientSwitch.get()) mc.player.getInventory().setSelectedSlot(slot);
        sendPacket(new UpdateSelectedSlotC2SPacket(slot));
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
        maxBreaksCount = 0;
        hasSwitch = false;
        mineTimer.setMs(999999);
        instantTimer.setMs(999999);
        timer.setMs(999999);
        targetPos = null;
        secondPos = null;
        started = false;
        secondStarted = false;
        publicProgress = 0;
        secondPublicProgress = 0;
        progress = 0;
        secondProgress = 0;
        lastTime = System.currentTimeMillis();
        secondLastTime = System.currentTimeMillis();
    }
    private enum MineSwitchMode { Delay, Silent, None }
    private enum SwingMode { Both, Packet, Client, None }
    private enum HandMode { MainHand, OffHand }
}
