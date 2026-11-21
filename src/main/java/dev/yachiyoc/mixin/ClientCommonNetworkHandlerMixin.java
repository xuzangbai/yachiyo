package dev.yachiyoc.mixin;

import dev.yachiyoc.module.impl.combat.PacketMine;
import dev.yachiyoc.net.PacketOrderManager;
import dev.yachiyoc.net.PostManager;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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
        if (packet instanceof UpdateSelectedSlotC2SPacket selected) {
            PacketMine mine = PacketMine.active();
            if (mine != null) mine.onSelectedSlot(selected.getSelectedSlot());
        }
    }

    @Inject(method = "onPing", at = @At("TAIL"))
    private void yachiyoc$flushAfterPong(CommonPingS2CPacket packet, CallbackInfo ci) {
        PostManager.onPing();
    }
}
