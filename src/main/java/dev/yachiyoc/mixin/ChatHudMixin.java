package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.ChatHudAccessor;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
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
        Iterator<ChatHudLine> iterator = messages.iterator();
        while (iterator.hasNext()) {
            ChatHudLine line = iterator.next();
            if (signature.equals(line.signature())) {
                creationTick = line.creationTick();
                iterator.remove();
            }
        }
        if (creationTick >= 0) {
            int tick = creationTick;
            visibleMessages.removeIf(line -> line.addedTime() == tick);
        }
    }
}
