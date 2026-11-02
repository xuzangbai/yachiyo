package dev.yachiyoc.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;

public final class Chat {
    private static String displayName = "YachiyoC";
    private static int prefixColor = 0xFFFF69B4;
    private static int nameColor = 0xFFFFFFFF;
    private static int enabledColor = 0xFF4FA3FF;
    private static int disabledColor = 0xFFFF5555;
    private static final Map<String, MessageSignatureData> REPLACE = new HashMap<>();

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

    /**
     * Replaces a previous client message with the same key, similar to Meteor's
     * message-id feedback. Used for rapid module toggle feedback so the chat does
     * not fill with duplicate lines.
     */
    public static void replace(String key, Text message) {
        replaceRaw(key, prefix().append(message));
    }

    public static void replaceRaw(String key, Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }
        ChatHud hud = client.inGameHud.getChatHud();
        MessageSignatureData old = REPLACE.remove(key);
        if (old != null && hud instanceof ChatHudAccessor accessor) {
            accessor.yachiyoc$removeBySignature(old);
        }
        byte[] signatureBytes = new byte[MessageSignatureData.SIZE];
        ThreadLocalRandom.current().nextBytes(signatureBytes);
        MessageSignatureData signature = new MessageSignatureData(signatureBytes);
        hud.addMessage(message, signature, null);
        REPLACE.put(key, signature);
    }

    public static MutableText prefix() {
        return Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(displayName).withColor(prefixColor))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
    }

    public static void setDisplayName(String name) {
        displayName = name == null || name.isBlank() ? "YachiyoC" : name;
    }

    public static void setPrefixColor(int color) {
        prefixColor = color;
    }

    public static void setNameColor(int color) {
        nameColor = color;
    }

    public static void setEnabledColor(int color) {
        enabledColor = color;
    }

    public static void setDisabledColor(int color) {
        disabledColor = color;
    }

    public static int enabledColor() {
        return enabledColor;
    }

    public static int disabledColor() {
        return disabledColor;
    }

    public static MutableText name(Text text) {
        return text.copy().withColor(nameColor);
    }

    public static MutableText status(boolean enabled, Text text) {
        return text.copy().withColor(enabled ? enabledColor : disabledColor);
    }
}
