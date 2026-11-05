package dev.yachiyoc.mixin;

import dev.yachiyoc.module.impl.misc.ViaFix;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Via 防踢第二步：吞掉"解码失败"这一条断线原因。
 *
 * <p>{@code ClientConnection#exceptionCaught} 会先放过单包级 {@code PacketException}
 * （原版自己会跳过），但 Via 翻译出来的解码失败是 {@code DecoderException}
 * （消息里带 {@code Failed to decode packet}），会一路走到断线分支。
 * 这里在方法头把它拦掉：连接保持，只丢一个包。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    private static final Logger YACHIYOC_LOGGER = LoggerFactory.getLogger("yachiyoc");

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$swallowDecodeFailure(ChannelHandlerContext context, Throwable throwable,
                                               CallbackInfo ci) {
        if (!ViaFix.guardBadPackets() || !isPacketDecodeFailure(throwable)) {
            return;
        }
        YACHIYOC_LOGGER.warn("kept the connection alive after a packet decode failure", throwable);
        ViaFix.warnDroppedPacket(message(throwable));
        ci.cancel();
    }

    private static boolean isPacketDecodeFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DecoderException && current.getMessage() != null
                    && current.getMessage().contains("Failed to decode packet")) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                return current.getMessage();
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return null;
            }
            current = cause;
        }
        return null;
    }
}
