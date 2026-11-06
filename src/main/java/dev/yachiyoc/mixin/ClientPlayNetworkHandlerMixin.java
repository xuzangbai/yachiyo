package dev.yachiyoc.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器 revision 快同步（端口自 SLM 的 {@code InvDesyncFix#fastAsyncUpdateRevision}）。
 *
 * <p>{@code ClickSlotC2SPacket} 会带上客户端容器的 {@code revision}，服务端按它校验；
 * 一但客户端本地预测把 revision 推得比服务端"超前"（鬼手连续换手时很常见），
 * 后续点击就会被服务端整条丢掉 —— 表现就是"换上去的武器再也换不回来"。
 *
 * <p>SLM 的做法是在每个格子更新包到达后直接把 handler 的 revision 覆盖成服务端给的值；
 * 这里照做：syncId 0（玩家自己的背包）走 {@code playerScreenHandler}，其它走当前打开的容器。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void yachiyoc$syncRevision(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        yachiyoc$applyRevision(packet.getSyncId(), packet.getRevision());
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void yachiyoc$syncRevisionFromInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        yachiyoc$applyRevision(packet.syncId(), packet.revision());
    }

    private static void yachiyoc$applyRevision(int syncId, int revision) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        ScreenHandler handler = syncId == 0 ? player.playerScreenHandler : player.currentScreenHandler;
        if (handler == null || handler.syncId != syncId) {
            return;
        }
        if (handler instanceof ScreenHandlerAccessor accessor
                && accessor.yachiyoc$getRevision() != revision) {
            accessor.yachiyoc$setRevision(revision);
        }
    }
}
