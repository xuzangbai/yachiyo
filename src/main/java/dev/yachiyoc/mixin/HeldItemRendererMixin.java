package dev.yachiyoc.mixin;

import dev.yachiyoc.render.v1_21_11.BalloonWrist;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;"
                    + "Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void yachiyoc$captureHeldArm(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                        float swing, ItemStack item, float equip, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (hand == Hand.MAIN_HAND) BalloonWrist.captureItem(MinecraftClient.getInstance(), player.getMainArm(), matrices, queue, light);
    }
}
