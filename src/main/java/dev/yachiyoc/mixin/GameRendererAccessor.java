package dev.yachiyoc.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use the same animated FOV as the world when converting pixels to billboard units. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float yachiyoc$getFov(Camera camera, float tickDelta, boolean changingFov);
}
