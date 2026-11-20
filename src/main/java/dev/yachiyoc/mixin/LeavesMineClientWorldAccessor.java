package dev.yachiyoc.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientWorld.class)
public interface LeavesMineClientWorldAccessor {
    @Invoker("getPendingUpdateManager")
    PendingUpdateManager yachiyoc$getLeavesMinePendingUpdates();
}
