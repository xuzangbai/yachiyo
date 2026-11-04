package dev.yachiyoc.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("lastYawClient")
    void yachiyoc$setLastYawClient(float yaw);

    @Accessor("lastPitchClient")
    void yachiyoc$setLastPitchClient(float pitch);
}
