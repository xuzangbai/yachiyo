package dev.yachiyoc.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Chat {
    private static final int ACCENT = 0xFF64B5F6;

    private Chat() {
    }

    public static void info(String message) {
        info(Text.literal(message));
    }

    public static void info(Text message) {
        raw(prefix().append(message.copy().formatted(Formatting.GRAY)));
    }

    public static void error(String message) {
        error(Text.literal(message));
    }

    public static void error(Text message) {
        raw(prefix().append(message.copy().formatted(Formatting.RED)));
    }

    public static void raw(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(text);
        }
    }

    private static MutableText prefix() {
        return Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("YachiyoC").withColor(ACCENT))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
    }
}
