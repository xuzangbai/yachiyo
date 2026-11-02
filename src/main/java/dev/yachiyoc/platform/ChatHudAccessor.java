package dev.yachiyoc.platform;

import net.minecraft.network.message.MessageSignatureData;

/**
 * Implemented by ChatHudMixin so client-side status lines can be replaced
 * without the vanilla "message deleted" system notice.
 */
public interface ChatHudAccessor {
    void yachiyoc$removeBySignature(MessageSignatureData signature);
}
