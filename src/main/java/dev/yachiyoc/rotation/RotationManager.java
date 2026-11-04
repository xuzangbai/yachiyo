package dev.yachiyoc.rotation;

import dev.yachiyoc.mixin.ClientPlayerEntityAccessor;
import dev.yachiyoc.net.ServerMovementState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

/**
 * Rotation timing for combat actions.
 *
 * <ul>
 *   <li>{@link RotationMode#NONE}: no rotation at all, the action runs immediately.</li>
 *   <li>{@link RotationMode#DELAY_MOVEMENT}: the entity rotation is written before the movement
 *       packet and restored right after it, the action is sent on the next tick.</li>
 *   <li>{@link RotationMode#MOVEMENT_POST}: 与 {@code DELAY_MOVEMENT} 同实现。真后置（移动包之后补
 *       转头包再出手）实测在低版本服务端上会被按 Post 判掉、并触发拉回，所以保留旧别名。</li>
 *   <li>{@link RotationMode#SILENT_MOVEMENT}: Hachimi style, the entity rotation is held for the
 *       whole tick so the movement packet carries it, and the camera is restored at tick end.</li>
 *   <li>{@link RotationMode#LEGACY_SILENT}: "低版本静默". A real movement packet carrying the aim
 *       rotation is pushed at the <em>server known position</em>, the action runs in the very same
 *       tick right after it, and the camera is never touched. Vanilla pushes the camera rotation
 *       back later in the same tick (see {@link #beforeMovementPacket}).</li>
 * </ul>
 */
public final class RotationManager {
    /** How far the mirrored server position may be from the player before it is not trusted. */
    private static final double POSITION_TRUST_SQ = 3.0D * 3.0D;

    private static RotationMode defaultMode = RotationMode.SILENT_MOVEMENT;
    private static boolean pending;
    private static RotationMode pendingMode;
    private static float oldYaw;
    private static float oldPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static Runnable pendingAction;
    private static boolean applied;

    private static boolean hachimiActive;
    private static float hachimiYaw;
    private static float hachimiPitch;
    private static float hachimiOldYaw;
    private static float hachimiOldPitch;
    private static float hachimiOldHeadYaw;
    private static float hachimiOldBodyYaw;
    private static Runnable hachimiAction;

    private static Runnable postAttack;
    private static float postAttackYaw;
    private static float postAttackPitch;

    static {
        ClientTickEvents.START_CLIENT_TICK.register(client -> flushPostAttack());
        ClientTickEvents.END_CLIENT_TICK.register(client -> endTick());
    }

    private RotationManager() {
    }

    public static void initialize() {
    }

    public static RotationMode defaultMode() {
        return defaultMode;
    }

    public static void setDefaultMode(RotationMode mode) {
        defaultMode = mode == null ? RotationMode.SILENT_MOVEMENT : mode;
    }

    public static RotationMode resolve(RotationChoice choice) {
        if (choice == null || choice == RotationChoice.FOLLOW) {
            return defaultMode;
        }
        return switch (choice) {
            case FOLLOW -> defaultMode;
            case NONE -> RotationMode.NONE;
            case DELAY_MOVEMENT -> RotationMode.DELAY_MOVEMENT;
            case MOVEMENT_POST -> RotationMode.MOVEMENT_POST;
            case LEGACY_SILENT -> RotationMode.LEGACY_SILENT;
            case SILENT_MOVEMENT -> RotationMode.SILENT_MOVEMENT;
        };
    }

    public static void run(RotationMode mode, float yaw, float pitch, Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (mode == null || mode == RotationMode.NONE) {
            action.run();
            return;
        }
        if (mode == RotationMode.MOVEMENT_POST) {
            // 真后置（移动包 → 补转头包 → 出手）实测更差：低版本服务端按 Post 判掉、还会拉回，
            // 所以这里继续沿用延迟移动的实现。
            mode = RotationMode.DELAY_MOVEMENT;
        }
        if (mode == RotationMode.LEGACY_SILENT) {
            runLegacySilent(player, yaw, pitch, action);
            return;
        }
        if (mode == RotationMode.SILENT_MOVEMENT) {
            if (!hachimiActive) {
                hachimiOldYaw = player.getYaw();
                hachimiOldPitch = player.getPitch();
                hachimiOldHeadYaw = player.getHeadYaw();
                hachimiOldBodyYaw = player.getBodyYaw();
            }
            hachimiActive = true;
            hachimiYaw = yaw;
            hachimiPitch = pitch;
            hachimiAction = action;
            player.setYaw(yaw);
            player.setPitch(pitch);
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);
            return;
        }

        // 同一 tick 内重复请求时不要覆盖“相机原始角度”，否则恢复时视角会停在上一刀的瞄准角。
        boolean carryOver = pending && !applied;
        pending = true;
        applied = false;
        pendingMode = mode;
        if (!carryOver) {
            oldYaw = player.getYaw();
            oldPitch = player.getPitch();
        }
        targetYaw = yaw;
        targetPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        pendingAction = action;
        if (mode == RotationMode.DELAY_MOVEMENT) {
            // 延迟移动：本 tick 的移动包就要带上瞄准角，所以先写实体 yaw。
            player.setYaw(targetYaw);
            player.setPitch(targetPitch);
        }
        // 移动后置：不动实体 yaw，本 tick 的移动包保持相机角，转头包在移动包之后补发。
    }

    /**
     * Low version silent rotation: rotate only the server side.
     *
     * <p>Old protocol versions (1.8 ~ 1.20.7, i.e. everything Via translates down to) validate the
     * attack against the last rotation they received, and they only accept that rotation when it
     * arrives on a movement packet. So the aim is pushed as a real movement packet anchored at the
     * position the server already has (no movement, no set back), the attack follows in the same
     * tick, and the client camera / entity rotation stay untouched. The vanilla movement packet of
     * the same tick carries the camera rotation back, which re-syncs the server.
     */
    private static void runLegacySilent(ClientPlayerEntity player, float yaw, float pitch, Runnable action) {
        snapAim(player, yaw, pitch);
        action.run();
    }

    /**
     * Sends a movement packet that makes the server look at {@code (yaw, pitch)} without moving the
     * player and without touching the client camera.
     *
     * @return true when a packet was actually sent (the server rotation changed)
     */
    public static boolean snapAim(ClientPlayerEntity player, float yaw, float pitch) {
        if (player == null || player.networkHandler == null) {
            return false;
        }
        if (player.hasVehicle()) {
            // 骑乘时位置由载具同步，服务端只读旋转，发纯 look 包就够了。
            return sendLook(player, yaw, pitch);
        }
        ServerMovementState.ensure(player);
        float safePitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        // Keep the yaw continuous with what the server already has, so the server side unwrap
        // (and every "rotation diff" check built on top of it) sees a small delta.
        float safeYaw = ServerMovementState.yaw()
                + MathHelper.wrapDegrees(yaw - ServerMovementState.yaw());
        if (!ServerMovementState.rotationDiffers(safeYaw, safePitch)) {
            return false;
        }
        // Anchor the packet at the position the server already has: a low version server would
        // read any delta as a movement and answer with a set back. If the mirror is obviously
        // out of sync (teleport / respawn), fall back to the real position instead of replaying
        // a stale one.
        double px = ServerMovementState.x();
        double py = ServerMovementState.y();
        double pz = ServerMovementState.z();
        double dx = player.getX() - px;
        double dy = player.getY() - py;
        double dz = player.getZ() - pz;
        if (dx * dx + dy * dy + dz * dz > POSITION_TRUST_SQ) {
            px = player.getX();
            py = player.getY();
            pz = player.getZ();
        }
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                px, py, pz,
                safeYaw, safePitch, ServerMovementState.onGround(), player.horizontalCollision));
        return true;
    }

    /**
     * Called at the head of {@code ClientPlayerEntity#sendMovementPackets}.
     *
     * <p>Vanilla decides "do I have to send a rotation" by comparing the entity rotation with
     * {@code lastYawClient/lastPitchClient}, which it only updates when it sent a packet itself.
     * After a silent snap those fields still describe the camera, so vanilla would keep quiet and
     * the server would stay rotated at the aim - that is what makes movement get corrected and
     * attacks land on a stale rotation. Re-pointing them at the rotation the server really holds
     * makes vanilla push the camera rotation at exactly the right moment, for every mode.
     */
    public static void beforeMovementPacket(ClientPlayerEntity player) {
        syncClientRotation(player);
        if (applied || !pending) {
            return;
        }
        applied = true;
        // 实体 yaw 已经在 run() 里写成瞄准角（延迟移动），这里不需要再动。
    }

    private static void syncClientRotation(ClientPlayerEntity player) {
        if (player == null || player.networkHandler == null) {
            return;
        }
        ServerMovementState.ensure(player);
        if (player instanceof ClientPlayerEntityAccessor accessor) {
            accessor.yachiyoc$setLastYawClient(ServerMovementState.yaw());
            accessor.yachiyoc$setLastPitchClient(ServerMovementState.pitch());
        }
    }

    /**
     * Called at the return of {@code ClientPlayerEntity#sendMovementPackets}, i.e. after this tick's
     * movement packet has already been written to the connection.
     *
     * <p>The entity rotation is restored here and the action is fired at the beginning of the next
     * tick, so the attack goes out {@code move(aim) → attack → move(camera)}.
     */
    public static void afterMovementPacket(ClientPlayerEntity player) {
        if (!applied) {
            return;
        }
        if (pendingMode == RotationMode.DELAY_MOVEMENT && pendingAction != null) {
            postAttack = pendingAction;
            postAttackYaw = targetYaw;
            postAttackPitch = targetPitch;
            player.setYaw(oldYaw);
            player.setPitch(oldPitch);
        }
        pending = false;
        applied = false;
        pendingAction = null;
    }

    public static void clearAim() {
        if (pending && !applied && pendingMode == RotationMode.DELAY_MOVEMENT) {
            // 请求还没过 sendMovementPackets 就被取消（例如同一 tick 里模块被关掉），
            // 这里必须把实体角度还原，否则视角会永远停在瞄准角。
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setYaw(oldYaw);
                player.setPitch(oldPitch);
            }
        }
        pending = false;
        applied = false;
        pendingAction = null;
        if (hachimiActive) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setYaw(hachimiOldYaw);
                player.setPitch(hachimiOldPitch);
                player.setHeadYaw(hachimiOldHeadYaw);
                player.setBodyYaw(hachimiOldBodyYaw);
            }
        }
        hachimiActive = false;
        hachimiAction = null;
    }

    private static void endTick() {
        if (hachimiActive) {
            postAttack = hachimiAction;
            postAttackYaw = hachimiYaw;
            postAttackPitch = hachimiPitch;
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setYaw(hachimiOldYaw);
                player.setPitch(hachimiOldPitch);
                player.setHeadYaw(hachimiOldHeadYaw);
                player.setBodyYaw(hachimiOldBodyYaw);
            }
            hachimiActive = false;
            hachimiAction = null;
        }
        if (pending && !applied) {
            // 本 tick 没有走到 sendMovementPackets（骑乘载具走的是载具分支），不能再等：
            // 把实体角度还原掉，动作直接补发，避免视角卡在瞄准角、整刀被丢掉。
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            Runnable action = pendingAction;
            RotationMode mode = pendingMode;
            if (player != null && mode == RotationMode.DELAY_MOVEMENT) {
                player.setYaw(oldYaw);
                player.setPitch(oldPitch);
            }
            pending = false;
            applied = false;
            pendingAction = null;
            if (action != null) {
                action.run();
            }
        }
    }

    private static void flushPostAttack() {
        if (postAttack == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        Runnable action = postAttack;
        postAttack = null;
        if (player == null) {
            return;
        }
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        try {
            player.setYaw(postAttackYaw);
            player.setPitch(postAttackPitch);
            action.run();
        } finally {
            player.setYaw(yaw);
            player.setPitch(pitch);
        }
    }

    /**
     * Rotates the server side only, with a look-only movement packet: no position is re-sent and the
     * client camera / entity rotation are untouched.
     *
     * @return true when a packet was actually sent (the server rotation changed)
     */
    public static boolean sendLook(ClientPlayerEntity player, float yaw, float pitch) {
        if (player == null || player.networkHandler == null) {
            return false;
        }
        ServerMovementState.ensure(player);
        float base = ServerMovementState.yaw();
        float safeYaw = base + MathHelper.wrapDegrees(yaw - base);
        float safePitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        if (!ServerMovementState.rotationDiffers(safeYaw, safePitch)) {
            return false;
        }
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                safeYaw, safePitch, ServerMovementState.onGround(), player.horizontalCollision));
        return true;
    }

    public static float[] anglesTo(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float canonical = MathHelper.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        float base = player == null ? canonical : player.getYaw();
        float yaw = base + MathHelper.wrapDegrees(canonical - base);
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }
}
