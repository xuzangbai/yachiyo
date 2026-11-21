package dev.yachiyoc.mixin;

import dev.yachiyoc.module.impl.combat.PacketMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$startPacketMine(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        PacketMine mine = PacketMine.active();
        if (mine != null && mine.onStartBreakingBlock(pos, side)) {
            cir.setReturnValue(false);
        }
    }
}
