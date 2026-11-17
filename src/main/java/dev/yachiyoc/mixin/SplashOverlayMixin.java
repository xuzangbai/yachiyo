package dev.yachiyoc.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.yachiyoc.ui.loading.LoadingAssets;
import dev.yachiyoc.ui.loading.LoadingScreenRenderer;
import dev.yachiyoc.ui.loading.LoadingTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/** Replace only visuals; vanilla render/tick keep completion, callbacks and error handling. */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private boolean reloading;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;
    @Shadow private float progress;
    @Unique private final LoadingScreenRenderer yachiyoc$loading = new LoadingScreenRenderer();
    @Unique private boolean yachiyoc$custom;

    @Inject(method = "render", at = @At("HEAD"))
    private void yachiyoc$prepare(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        yachiyoc$custom = yachiyoc$loading.prepare(Util.getMeasuringTimeMs(), reloadCompleteTime >= 0);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 3, allow = 3)
    private int yachiyoc$background(IntSupplier original) {
        return yachiyoc$custom ? LoadingAssets.BACKGROUND : original.getAsInt();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"), require = 2, allow = 2)
    private boolean yachiyoc$logo(DrawContext context, RenderPipeline pipeline, Identifier id,
                                  int x, int y, float u, float v, int width, int height,
                                  int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        return !yachiyoc$custom;
    }

    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$progress(DrawContext context, int x1, int y1, int x2, int y2, float opacity, CallbackInfo ci) {
        if (yachiyoc$custom) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void yachiyoc$draw(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!yachiyoc$custom) return;
        if (client.getOverlay() != (Object) this) { yachiyoc$loading.close(); return; }
        long now = Util.getMeasuringTimeMs();
        float opacity = LoadingTimeline.opacity(now, reloadCompleteTime, reloadStartTime, reloading);
        yachiyoc$loading.draw(context, progress, opacity, now);
    }
}
