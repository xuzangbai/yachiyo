package dev.yachiyoc.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

/**
 * Mirror of the movement state the server currently believes it has.
 *
 * <p>Every {@link PlayerMoveC2SPacket} that leaves the client is folded into this snapshot, so the
 * rotation/position the server is holding can be reproduced on demand. This is what the low
 * version silent rotation needs:
 *
 * <ul>
 *   <li>the silent aim packet has to be sent <em>at the position the server already knows</em>,
 *       otherwise a low version server treats it as a movement (and answers with a set back,
 *       which is exactly the "卡脚" symptom);</li>
 *   <li>the camera rotation has to be pushed back after the action, and vanilla only does that
 *       when it believes the server rotation differs from the entity rotation.</li>
 * </ul>
 */
public final class ServerMovementState {
    /** Tolerance used to decide "the server already looks there" (same as the reference client). */
    public static final float ROTATION_EPSILON = 1.0E-2F;

    /**
     * If the mirror drifts further away than this from the player, it is stale (dimension change,
     * teleport, respawn, ...) and must be rebuilt from the player instead of being trusted.
     */
    private static final double MAX_TRUSTED_DISTANCE_SQ = 16.0D * 16.0D;

    private static ClientPlayerEntity owner;
    private static boolean valid;
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    private static float pitch;
    private static boolean onGround;
    private static boolean horizontalCollision;
    /** 服务端口径的下落距离（锤子重击判定用的就是这一份）。 */
    private static double fallDistance;

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                fallDistance = 0.0D;
                return;
            }
            if (player.isInLava()) {
                // 原版在岩浆里每 tick 把下落距离减半
                fallDistance *= 0.5D;
            }
        });
    }

    private ServerMovementState() {
    }

    /** Folds one outgoing movement packet into the mirror. */
    public static void onMovePacket(PlayerMoveC2SPacket packet) {
        if (packet == null) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        ensure(player);
        // getX(float)/getYaw(float)/... return the packet value when it carries one and the
        // fallback (= the current server state) when the packet variant does not.
        double previousY = y;
        x = packet.getX(x);
        y = packet.getY(y);
        z = packet.getZ(z);
        yaw = packet.getYaw(yaw);
        pitch = packet.getPitch(pitch);
        onGround = packet.isOnGround();
        horizontalCollision = packet.horizontalCollision();
        updateFall(player, previousY);
    }

    /**
     * 按服务端的口径维护下落距离。
     *
     * <p>规则照 SLM 的 {@code PlayerStateManager#handleY}：落地/进水/骑乘/缓降/漂浮/爬梯/滑翔清零，
     * 向上移动清零，向下移动按差值累加（岩浆在 tick 里减半，见上面的事件）。
     * 锤子重击算的是服务端那份，所以这里必须跟着“发出去的移动包”算，而不是客户端的
     * {@code player.fallDistance}。
     */
    private static void updateFall(ClientPlayerEntity player, double previousY) {
        if (onGround || resetsFallDistance(player)) {
            fallDistance = 0.0D;
            return;
        }
        double delta = previousY - y;
        if (delta > 0.0D) {
            fallDistance += delta;
        } else if (delta < 0.0D) {
            fallDistance = 0.0D;
        }
    }

    private static boolean resetsFallDistance(ClientPlayerEntity player) {
        return player.isTouchingWater()
                || player.hasVehicle()
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || player.hasStatusEffect(StatusEffects.LEVITATION)
                || player.isClimbing()
                || player.isGliding();
    }

    /** 服务端视角的下落格数（锤子重击门槛是 1.5，滑翔时不算）。 */
    public static double fallDistance() {
        return fallDistance;
    }

    public static void resetFall() {
        fallDistance = 0.0D;
    }

    /**
     * 向服务端伪造一段“从高处掉下来”的位移（锤子重击用，只建议单人 / 无反作弊服）。
     *
     * <p>先补 4 帧原地包让服务端把位置对上，再抬到 {@code +height}、落回原地 ——
     * 服务端按移动包里的 Y 变化累计落差，于是它认为你刚从 {@code height} 格高掉下来。
     * 包会经过 {@link dev.yachiyoc.net.PacketOrderManager} 折进镜像，所以 {@link #fallDistance()}
     * 也会跟着变成 {@code height}，后续判定一致。
     */
    public static void spoofFall(ClientPlayerEntity player, double height) {
        if (player == null || player.networkHandler == null || height <= 0.0D) {
            return;
        }
        ensure(player);
        double baseX = x;
        double baseY = y;
        double baseZ = z;
        ClientPlayNetworkHandler handler = player.networkHandler;
        for (int i = 0; i < 4; i++) {
            handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    baseX, baseY, baseZ, false, false));
        }
        handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                baseX, baseY + height, baseZ, false, false));
        handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                baseX, baseY, baseZ, false, false));
    }

    /**
     * Makes sure the mirror matches {@code player}. Cheap when nothing changed; rebuilds the
     * snapshot after a world / player / teleport discontinuity.
     */
    public static void ensure(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (player != owner) {
            owner = player;
            valid = false;
        }
        if (valid) {
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= MAX_TRUSTED_DISTANCE_SQ) {
                return;
            }
        }
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        yaw = player.getYaw();
        pitch = player.getPitch();
        onGround = player.isOnGround();
        horizontalCollision = player.horizontalCollision;
        fallDistance = 0.0D;
        valid = true;
    }

    public static void reset() {
        owner = null;
        valid = false;
        yaw = 0.0F;
        pitch = 0.0F;
        onGround = false;
        horizontalCollision = false;
        fallDistance = 0.0D;
    }

    public static double x() {
        return x;
    }

    public static double y() {
        return y;
    }

    public static double z() {
        return z;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static boolean onGround() {
        return onGround;
    }

    public static boolean horizontalCollision() {
        return horizontalCollision;
    }

    /** Would sending {@code (yaw, pitch)} actually change the server side rotation? */
    public static boolean rotationDiffers(float yaw, float pitch) {
        return Math.abs(pitch - ServerMovementState.pitch) > ROTATION_EPSILON
                || Math.abs(MathHelper.wrapDegrees(yaw - ServerMovementState.yaw)) > ROTATION_EPSILON;
    }
}
