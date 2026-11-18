package dev.yachiyoc.mixin;

import dev.yachiyoc.ui.menuvideo.MenuBackground;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class MenuVideoFpsMixin {
    @Inject(method = "getLimitReason", at = @At("RETURN"), cancellable = true)
    private void yachiyoc$useConfiguredFpsForVideo(CallbackInfoReturnable<InactivityFpsLimiter.LimitReason> cir) {
        if (cir.getReturnValue() != InactivityFpsLimiter.LimitReason.OUT_OF_LEVEL_MENU || !MenuBackground.isPlaying()) return;
        if (MenuBackground.isLobby(MinecraftClient.getInstance())) {
            cir.setReturnValue(InactivityFpsLimiter.LimitReason.NONE);
        }
    }
}
