package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.FontManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements MinecraftClientAccessor {
    @Shadow
    @Final
    private FontManager fontManager;

    @Override
    public FontManager yachiyoc$fontManager() {
        return fontManager;
    }
}
