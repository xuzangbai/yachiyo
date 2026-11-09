package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.EffectParticles;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 抓药水粒子（服务端不发给别人效果包、元数据也被清时的最后一条路）。 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)"
            + "Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"))
    private void yachiyoc$captureEffectParticle(ParticleEffect effect,
                                                double x, double y, double z,
                                                double velocityX, double velocityY, double velocityZ,
                                                CallbackInfoReturnable<?> cir) {
        if (effect instanceof TintedParticleEffect tinted && tinted.getType() == ParticleTypes.ENTITY_EFFECT) {
            int red = Math.round(tinted.getRed() * 255.0F);
            int green = Math.round(tinted.getGreen() * 255.0F);
            int blue = Math.round(tinted.getBlue() * 255.0F);
            EffectParticles.record(x, y, z, (red << 16) | (green << 8) | blue);
        }
    }
}
