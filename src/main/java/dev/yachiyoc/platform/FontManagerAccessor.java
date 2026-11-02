package dev.yachiyoc.platform;

import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;

public interface FontManagerAccessor {
    FontStorage yachiyoc$getStorage(Identifier id);
}
