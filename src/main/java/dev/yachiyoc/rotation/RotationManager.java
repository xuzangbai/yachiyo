package dev.yachiyoc.rotation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Hachimi-style silent movement: the player entity yaw is used for the
 * movement packet, the camera is restored at the end of the tick, and the
 * attack is sent on the next tick before movement.
 */
public final class RotationManager {
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

    private static boolean legacyActive;
    private static float legacyOldYaw;
    private static float legacyOldPitch;
    private static double legacyX;
    private static double legacyY;
    private static double legacyZ;
    private static Runnable postAttack;
    private static float postAttackYaw;
    private static float postAttackPitch;
    private static boolean movementPostActive;
    private static float movementPostOldYaw;
    private static float movementPostOldPitch;

    static {
        ClientTickEvents.START_CLIENT_TICK.register(client -> flushLegacyRestore());
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
            mode = RotationMode.DELAY_MOVEMENT;
        }
        if (mode == RotationMode.LEGACY_SILENT) {
            // Full/last-position snap is unsafe across current Via versions.
            // Use the stable native silent path until a real legacy profile exists.
            mode = RotationMode.SILENT_MOVEMENT;
        }
        if (mode == RotationMode.LEGACY_SILENT) {
            legacyActive = true;
            legacyOldYaw = player.lastYaw;
            legacyOldPitch = player.lastPitch;
            legacyX = player.lastX;
            legacyY = player.lastY;
            legacyZ = player.lastZ;
            sendFullAt(player, legacyX, legacyY, legacyZ, yaw, pitch);
            action.run();
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

        pending = true;
        applied = false;
        pendingMode = mode;
        oldYaw = player.getYaw();
        oldPitch = player.getPitch();
        targetYaw = yaw;
        targetPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        pendingAction = action;
        if (mode == RotationMode.DELAY_MOVEMENT) {
            player.setYaw(targetYaw);
            player.setPitch(targetPitch);
        }

    }

    public static void beforeMovementPacket(ClientPlayerEntity player) {
        if (applied || !pending) {
            return;
        }
        applied = true;
        if (pendingMode == RotationMode.DELAY_MOVEMENT
                || pendingMode == RotationMode.MOVEMENT_POST) {
            return;
        }
        player.setYaw(targetYaw);
        player.setPitch(targetPitch);
    }

    public static void afterMovementPacket(ClientPlayerEntity player) {
        if (!applied) {
            return;
        }
        if (pendingMode == RotationMode.MOVEMENT_POST && pendingAction != null) {
            movementPostActive = true;
            movementPostOldYaw = oldYaw;
            movementPostOldPitch = oldPitch;
            sendFull(player, targetYaw, targetPitch);
            pendingAction.run();
        } else if (pendingMode == RotationMode.DELAY_MOVEMENT && pendingAction != null) {
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

    private static void flushLegacyRestore() {
        if (!legacyActive) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player != null) {
            sendFullAt(player, legacyX, legacyY, legacyZ, legacyOldYaw, legacyOldPitch);
        }
        legacyActive = false;
    }

    private static void endTick() {
        if (movementPostActive) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player != null) {
                sendFull(player, movementPostOldYaw, movementPostOldPitch);
            }
            movementPostActive = false;
        }
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
            pending = false;
            pendingAction = null;
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

    public static void sendLook(ClientPlayerEntity player, float yaw, float pitch) {
        if (player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, player.isOnGround(), false));
    }

    public static void sendFullAt(ClientPlayerEntity player, double x, double y, double z,
                                  float yaw, float pitch) {
        if (player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                x, y, z, yaw, pitch, player.isOnGround(), false));
    }

    public static void sendFull(ClientPlayerEntity player, float yaw, float pitch) {
        if (player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                player.getX(), player.getY(), player.getZ(), yaw, pitch,
                player.isOnGround(), false));
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
