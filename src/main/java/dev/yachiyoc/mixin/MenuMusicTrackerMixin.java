package dev.yachiyoc.mixin;

import dev.yachiyoc.ui.menuvideo.MenuBackground;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MenuMusicTrackerMixin {
    @Shadow public abstract void stop();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$replaceMenuMusic(CallbackInfo ci) {
        if (MenuBackground.hasCompanionMusic()) {
            stop();
            ci.cancel();
        }
    }
}
