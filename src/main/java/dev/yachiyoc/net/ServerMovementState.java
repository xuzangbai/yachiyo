package dev.yachiyoc.net;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
        x = packet.getX(x);
        y = packet.getY(y);
        z = packet.getZ(z);
        yaw = packet.getYaw(yaw);
        pitch = packet.getPitch(pitch);
        onGround = packet.isOnGround();
        horizontalCollision = packet.horizontalCollision();
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
        valid = true;
    }

    public static void reset() {
        owner = null;
        valid = false;
        yaw = 0.0F;
        pitch = 0.0F;
        onGround = false;
        horizontalCollision = false;
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
