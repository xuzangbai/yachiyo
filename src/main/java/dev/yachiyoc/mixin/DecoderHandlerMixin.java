package dev.yachiyoc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.yachiyoc.module.impl.misc.ViaFix;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Via 防踢第一步：解码失败时把这一个包剩下的字节读掉。
 *
 * <p>原版只在 {@code PacketException} 上做 drain，其它解码异常会把剩余字节留在缓冲区里，
 * 后面整段字节流就错位了（读到一堆垃圾包 id），最终无法恢复。这里提前 drain，
 * 让下一条读从一个包边界开始，再由 {@link ClientConnectionMixin} 决定要不要断线。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
@Mixin(DecoderHandler.class)
public abstract class DecoderHandlerMixin {
    private static final Logger YACHIYOC_LOGGER = LoggerFactory.getLogger("yachiyoc");

    @WrapOperation(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object yachiyoc$drainOnDecodeFailure(PacketCodec<?, ?> codec, Object buffer,
                                                 Operation<Object> original) {
        try {
            return original.call(codec, buffer);
        } catch (DecoderException e) {
            if (ViaFix.guardBadPackets() && buffer instanceof ByteBuf byteBuf) {
                int remaining = byteBuf.readableBytes();
                if (remaining > 0) {
                    byteBuf.skipBytes(remaining);
                }
                YACHIYOC_LOGGER.warn("dropped undecodable packet ({} bytes left, now drained): {}",
                        remaining, e.getMessage());
            }
            throw e;
        }
    }
}
