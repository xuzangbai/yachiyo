package dev.yachiyoc.mixin;

import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 容器 revision 的读写（端口自 SLM 的 {@code InvDesyncFix}，用来把点击的 revision 对齐服务端）。 */
@Mixin(ScreenHandler.class)
public interface ScreenHandlerAccessor {
    @Accessor("revision")
    void yachiyoc$setRevision(int revision);

    @Accessor("revision")
    int yachiyoc$getRevision();
}
