package dev.yachiyoc.mixin;

import dev.yachiyoc.module.impl.render.HandRender;
import dev.yachiyoc.platform.MinecraftClientAccessor;
import dev.yachiyoc.ui.home.YachiyoHomeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import dev.yachiyoc.ui.loading.LoadingScreenRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccessor {
    @Shadow
    @Final
    private FontManager fontManager;

    @Override
    public FontManager yachiyoc$fontManager() {
        return fontManager;
    }

    @ModifyVariable(method = "setScreen", at = @At("LOAD"), argsOnly = true,
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", ordinal = 0),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;", opcode = Opcodes.PUTFIELD)))
    private Screen yachiyoc$routeHomeScreen(Screen screen) {
        return YachiyoHomeScreen.route(screen);
    }

    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void yachiyoc$releaseLoadingOverlay(Overlay overlay, CallbackInfo ci) {
        if (((MinecraftClient) (Object) this).getOverlay() != overlay) LoadingScreenRenderer.overlayChanging();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$hitLocalBalloon(CallbackInfoReturnable<Boolean> cir) {
        HandRender hand = HandRender.active();
        // A handled press also suppresses vanilla's held-button block breaking on this tick.
        if (hand != null && hand.attack((MinecraftClient) (Object) this)) cir.setReturnValue(true);
    }
}
