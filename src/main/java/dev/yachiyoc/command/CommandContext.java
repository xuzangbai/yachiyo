package dev.yachiyoc.command;

import dev.yachiyoc.platform.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Arrays;

public record CommandContext(MinecraftClient client, String[] args, String raw) {
    public String arg(int index) {
        return index >= 0 && index < args.length ? args[index] : "";
    }

    public String joinFrom(int index) {
        if (index >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, index, args.length));
    }

    public void reply(String message) {
        Chat.info(message);
    }

    public void reply(Text message) {
        Chat.info(message);
    }

    public void error(String message) {
        Chat.error(message);
    }

    public void error(Text message) {
        Chat.error(message);
    }
}
