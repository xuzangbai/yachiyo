package dev.yachiyoc.mixin;

import dev.yachiyoc.net.PacketOrderManager;
import dev.yachiyoc.net.PostManager;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet order and transaction boundary hooks. */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void yachiyoc$trackPacket(Packet<?> packet, CallbackInfo ci) {
        PacketOrderManager.onSend(packet);
    }

    @Inject(method = "onPing", at = @At("TAIL"))
    private void yachiyoc$flushAfterPong(CommonPingS2CPacket packet, CallbackInfo ci) {
        PostManager.onPing();
    }
}
