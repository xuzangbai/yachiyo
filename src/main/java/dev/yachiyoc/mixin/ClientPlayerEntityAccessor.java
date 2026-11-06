package dev.yachiyoc.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("lastYawClient")
    void yachiyoc$setLastYawClient(float yaw);

    @Accessor("lastPitchClient")
    void yachiyoc$setLastPitchClient(float pitch);

    /** 上一 tick 发出去的按键输入；改掉它原版下一 tick 就会重发真实输入（等价 SLM 的 resyncInput）。 */
    @Accessor("lastPlayerInput")
    void yachiyoc$setLastPlayerInput(PlayerInput input);
}
