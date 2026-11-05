package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.ChatHudAccessor;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;
import java.util.List;

/**
 * Removes a client status line directly from ChatHud's lists. Calling
 * ChatHud.removeMessage would enqueue the vanilla "message deleted" notice,
 * which is exactly the spam we want to avoid.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatHudAccessor {
    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Override
    public void yachiyoc$removeBySignature(MessageSignatureData signature) {
        int creationTick = -1;
        String oldText = null;
        Iterator<ChatHudLine> iterator = messages.iterator();
        while (iterator.hasNext()) {
            ChatHudLine line = iterator.next();
            if (signature.equals(line.signature())) {
                creationTick = line.creationTick();
                oldText = line.content().getString();
                iterator.remove();
            }
        }
        if (creationTick >= 0 && oldText != null) {
            int tick = creationTick;
            String expected = oldText;
            visibleMessages.removeIf(line -> line.addedTime() == tick
                    && orderedToString(line.content()).equals(expected));
        }
    }

    private static String orderedToString(OrderedText text) {
        StringBuilder builder = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
