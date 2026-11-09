package dev.yachiyoc.mixin;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开了名字标签模块就把原版玩家名牌取消，避免和我们自己画的重叠。
 *
 * <p>注意注入点是 {@link PlayerEntityRenderer} 自己的重写（父类 {@code EntityRenderer} 那个会被它覆盖），
 * 而且这个类里同时有桥接方法和真正的重写，所以用完整描述符指定，避免歧义。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yachiyoc$hideVanillaNameTag(PlayerEntityRenderState state, MatrixStack matrices,
                                             OrderedRenderCommandQueue queue,
                                             CameraRenderState cameraState, CallbackInfo ci) {
        Module nametags = YachiyoC.modules() == null ? null : YachiyoC.modules().byId("nametags");
        if (nametags != null && nametags.enabled()) {
            ci.cancel();
        }
    }
}
