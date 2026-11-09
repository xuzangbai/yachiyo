package dev.yachiyoc.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * 世界渲染挂载点：实体画完之后（{@link WorldRenderEvents#AFTER_ENTITIES}）把上下文交给
 * {@link RenderApi}，再分发给各模块的回调。
 *
 * <p>这里是**唯一**和 Fabric 世界渲染事件绑定的地方。注意 1.21.11 的 Fabric 把
 * 这套事件挪到了 {@code ...rendering.v1.world} 包，且不再直接给相机/tick —— 那些从
 * {@link MinecraftClient} 上取，接口本身保持干净。
 */
public final class WorldRenderHook {
    private static boolean registered;

    private WorldRenderHook() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.AFTER_ENTITIES.register(WorldRenderHook::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        MatrixStack matrices = context.matrices();
        if (matrices == null || context.consumers() == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null) {
            return;
        }
        float tickDelta = client.getRenderTickCounter() == null
                ? 1.0F
                : client.getRenderTickCounter().getTickProgress(true);
        Matrix4f projection;
        try {
            projection = client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue());
        } catch (Throwable ignored) {
            projection = new Matrix4f();
        }
        Render.api().beginWorld(matrices, context.consumers(), projection,
                matrices.peek().getPositionMatrix(), tickDelta);
        try {
            Render3DCallbacks.dispatch();
        } finally {
            Render.api().endWorld();
        }
    }
}
