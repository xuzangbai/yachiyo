package dev.yachiyoc.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SoundPlayer {
    private SoundPlayer() {
    }

    public static void play(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String name = fileName.toLowerCase().endsWith(".ogg")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) {
            return;
        }
        SoundEvent event = SoundEvent.of(Identifier.of("yachiyoc", name));
        client.getSoundManager().play(PositionedSoundInstance.ui(event, 1.0F));
    }
}
