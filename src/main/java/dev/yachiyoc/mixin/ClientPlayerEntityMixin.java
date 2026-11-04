package dev.yachiyoc.mixin;

import dev.yachiyoc.rotation.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the client camera still while the outgoing movement packet carries
 * the silent rotation, then restores the camera. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void yachiyoc$beforeMovementPacket(CallbackInfo ci) {
        RotationManager.beforeMovementPacket((ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void yachiyoc$afterMovementPacket(CallbackInfo ci) {
        RotationManager.afterMovementPacket((ClientPlayerEntity) (Object) this);
    }
}
