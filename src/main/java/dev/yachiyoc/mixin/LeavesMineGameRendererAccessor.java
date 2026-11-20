package dev.yachiyoc.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface LeavesMineGameRendererAccessor {
    @Invoker("getFov")
    float yachiyoc$getLeavesMineFov(Camera camera, float tickDelta, boolean changingFov);
}
