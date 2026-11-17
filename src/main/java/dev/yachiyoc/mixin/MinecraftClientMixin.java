package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.gui.screen.Overlay;
import dev.yachiyoc.ui.loading.LoadingScreenRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccessor {
    @Shadow
    @Final
    private FontManager fontManager;

    @Override
    public FontManager yachiyoc$fontManager() {
        return fontManager;
    }

    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void yachiyoc$releaseLoadingOverlay(Overlay overlay, CallbackInfo ci) {
        if (((MinecraftClient) (Object) this).getOverlay() != overlay) LoadingScreenRenderer.overlayChanging();
    }
}
