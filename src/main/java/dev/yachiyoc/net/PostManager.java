package dev.yachiyoc.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Queues combat actions until the next ping/pong transaction. Sending an
 * attack immediately after a movement packet is what Grim reports as Post.
 */
public final class PostManager {
    private static final Deque<QueuedAction> QUEUE = new ArrayDeque<>();
    private static long queuedTick;

    private PostManager() {
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> flushIfStale());
    }

    public static void queue(Runnable action, float yaw, float pitch) {
        if (action == null) {
            return;
        }
        QUEUE.addLast(new QueuedAction(action, yaw, pitch, clientTick()));
        if (queuedTick == 0L) {
            queuedTick = clientTick();
        }
    }

    /**
     * 把动作推到“下一个服务器事务”（ping/pong）再执行，等价 SLM 的
     * {@code PostManager.addNextTransactionAction}。没有 ping 来时 3 tick 后兜底执行。
     */
    public static void queueNextTransaction(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) {
            return;
        }
        queue(action, player.getYaw(), player.getPitch());
    }

    /** Called after the client answers a server CommonPing (pong sent). */
    public static void onPing() {
        flush();
    }

    public static void flushIfStale() {
        if (QUEUE.isEmpty()) {
            queuedTick = 0L;
            return;
        }
        if (clientTick() - queuedTick >= 3L) {
            flush();
        }
    }

    public static void flush() {
        queuedTick = 0L;
        QueuedAction action;
        while ((action = QUEUE.pollFirst()) != null) {
            PacketOrderManager.execute(action.action(), action.yaw(), action.pitch());
        }
    }

    public static void clear() {
        QUEUE.clear();
        queuedTick = 0L;
    }

    private static long clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0L : client.world.getTime();
    }

    private record QueuedAction(Runnable action, float yaw, float pitch, long tick) {
    }
}
