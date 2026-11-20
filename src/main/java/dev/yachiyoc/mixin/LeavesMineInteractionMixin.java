package dev.yachiyoc.mixin;

import dev.yachiyoc.leavesmine.LeavesPacketMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Yarn equivalent of Meteor's cancellable StartBreakingBlockEvent injection. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class LeavesMineInteractionMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$startLeavesMine(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        LeavesPacketMine mine = LeavesPacketMine.active();
        if (mine != null && mine.onStartBreakingBlock(pos)) {
            cir.setReturnValue(false);
        }
    }
}
