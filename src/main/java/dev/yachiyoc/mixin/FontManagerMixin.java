package dev.yachiyoc.mixin;

import dev.yachiyoc.platform.FontManagerAccessor;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FontManager.class)
public abstract class FontManagerMixin implements FontManagerAccessor {
    @Shadow
    abstract FontStorage getStorageInternal(Identifier id);

    @Override
    public FontStorage yachiyoc$getStorage(Identifier id) {
        return getStorageInternal(id);
    }
}
