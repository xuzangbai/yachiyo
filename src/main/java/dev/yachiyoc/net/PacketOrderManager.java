package dev.yachiyoc.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Tracks the outgoing packet order and delays combat actions until the next
 * server transaction. This is the part that prevents attack-after-movement
 * "Post" flags.
 */
public final class PacketOrderManager {
    private static long lastMovementTick;
    private static long lastAttackTick;
    private static boolean movementThisTick;
    private static boolean attackThisTick;

    private PacketOrderManager() {
    }

    static {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            movementThisTick = false;
            attackThisTick = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> PostManager.flushIfStale());
    }

    public static void onSend(Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket) {
            movementThisTick = true;
            lastMovementTick = clientTick();
        } else if (packet instanceof PlayerInteractEntityC2SPacket) {
            attackThisTick = true;
            lastAttackTick = clientTick();
        }
    }

    public static boolean movementThisTick() {
        return movementThisTick;
    }

    public static boolean attackThisTick() {
        return attackThisTick;
    }

    public static long lastMovementTick() {
        return lastMovementTick;
    }

    public static long lastAttackTick() {
        return lastAttackTick;
    }

    private static long clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0L : client.world.getTime();
    }

    static void execute(Runnable action, float yaw, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        float oldYaw = player.getYaw();
        float oldPitch = player.getPitch();
        try {
            player.setYaw(yaw);
            player.setPitch(pitch);
            action.run();
        } finally {
            player.setYaw(oldYaw);
            player.setPitch(oldPitch);
        }
    }
}
