package dev.yachiyoc.mixin;

import dev.yachiyoc.leavesmine.LeavesPacketMine;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The outgoing packet event used by LeavesHack to reset its mining delay. */
@Mixin(ClientConnection.class)
public abstract class LeavesMineConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"))
    private void yachiyoc$observeLeavesMinePacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        LeavesPacketMine mine = LeavesPacketMine.active();
        if (mine != null) {
            mine.onPacketSend(packet);
        }
    }
}
