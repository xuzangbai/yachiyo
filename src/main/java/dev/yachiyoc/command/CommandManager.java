package dev.yachiyoc.command;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.ModuleRegistry;
import dev.yachiyoc.platform.Keybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side command dispatcher. Any chat message starting with the configured
 * prefix is cancelled before it reaches the server and routed here.
 */
public final class CommandManager {
    private final Map<String, ClientCommand> index = new LinkedHashMap<>();
    private final List<ClientCommand> commands = new ArrayList<>();

    public void bootstrap() {
        register(new SimpleCommand("clickgui", List.of("gui", "cg"), "clickgui",
                "command.yachiyoc.clickgui.desc", ctx -> ctx.client().execute(YachiyoC::openClickGui)));

        register(new SimpleCommand("help", List.of("?"), "help [command]",
                "command.yachiyoc.help.desc", ctx -> {
            if (!ctx.arg(0).isEmpty()) {
                ClientCommand command = find(ctx.arg(0));
                if (command == null) {
                    ctx.error(Text.translatable("command.yachiyoc.unknown", ctx.arg(0), YachiyoC.prefix()));
                    return;
                }
                ctx.reply(Text.translatable("command.yachiyoc.help.entry",
                        YachiyoC.prefix(), command.usage(), Text.translatable(command.description())));
                return;
            }
            ctx.reply(Text.translatable("command.yachiyoc.help.header", YachiyoC.prefix()));
            for (ClientCommand command : commands) {
                ctx.reply(Text.translatable("command.yachiyoc.help.entry",
                        YachiyoC.prefix(), command.usage(), Text.translatable(command.description())));
            }
        }));

        register(new SimpleCommand("prefix", List.of(), "prefix <text>",
                "command.yachiyoc.prefix.desc", ctx -> {
            String next = ctx.arg(0);
            if (next.isEmpty()) {
                ctx.error(Text.translatable("command.yachiyoc.prefix.usage", YachiyoC.prefix()));
                return;
            }
            if (next.length() > 4 || next.chars().anyMatch(Character::isWhitespace) || next.equals("/")) {
                ctx.error(Text.translatable("command.yachiyoc.prefix.invalid"));
                return;
            }
            YachiyoC.config().data().prefix = next;
            YachiyoC.config().save();
            ctx.reply(Text.translatable("command.yachiyoc.prefix.set", next));
        }));

        register(new SimpleCommand("openkey", List.of(), "openkey <key|code>",
                "command.yachiyoc.openkey.desc", ctx -> {
            String raw = ctx.arg(0).toLowerCase(Locale.ROOT);
            int code = parseKey(raw);
            if (code <= 0) {
                ctx.error(Text.translatable("command.yachiyoc.openkey.unknown"));
                return;
            }
            YachiyoC.config().data().openKey = code;
            Keybinds.apply(code);
            YachiyoC.config().save();
            ctx.reply(Text.translatable("command.yachiyoc.openkey.set", Keybinds.label().getString()));
        }));

        register(new SimpleCommand("language", List.of("lang"), "language <auto|en_us|zh_cn>",
                "command.yachiyoc.language.desc", ctx -> {
            String next = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (next.isEmpty()) {
                ctx.reply(Text.translatable("command.yachiyoc.language.current",
                        YachiyoC.config().data().language));
                return;
            }
            if (!next.equals("auto") && !next.equals("en_us") && !next.equals("zh_cn")) {
                ctx.error(Text.translatable("command.yachiyoc.language.usage", YachiyoC.prefix()));
                return;
            }
            YachiyoC.config().data().language = next;
            YachiyoC.config().save();
            MinecraftClient client = ctx.client();
            if (!next.equals("auto") && YachiyoC.applyConfiguredLanguage(client)) {
                client.reloadResources();
            }
            ctx.reply(Text.translatable("command.yachiyoc.language.set", next));
        }));

        register(new SimpleCommand("modules", List.of("mods"), "modules",
                "command.yachiyoc.modules.desc", ctx -> {
            ModuleRegistry registry = YachiyoC.modules();
            for (ModuleCategory category : ModuleCategory.values()) {
                List<Module> modules = registry.byCategory(category);
                StringBuilder body = new StringBuilder();
                if (modules.isEmpty()) {
                    body.append(Text.translatable("command.yachiyoc.modules.empty").getString());
                } else {
                    for (int i = 0; i < modules.size(); i++) {
                        Module module = modules.get(i);
                        if (i > 0) {
                            body.append(", ");
                        }
                        body.append(module.id()).append(' ')
                                .append(Text.translatable(module.enabled()
                                        ? "command.yachiyoc.state.on"
                                        : "command.yachiyoc.state.off").getString());
                        if (!module.implemented()) {
                            body.append(" (").append(Text.translatable("ui.yachiyoc.wip").getString()).append(')');
                        }
                    }
                }
                ctx.reply(Text.translatable("command.yachiyoc.modules.line",
                        category.label().getString(), body.toString()));
            }
        }));

        register(new SimpleCommand("config", List.of(), "config <save|reload>",
                "command.yachiyoc.config.desc", ctx -> {
            switch (ctx.arg(0).toLowerCase(Locale.ROOT)) {
                case "save" -> {
                    YachiyoC.saveAll();
                    ctx.reply(Text.translatable("command.yachiyoc.config.saved"));
                }
                case "reload" -> {
                    YachiyoC.reloadAll();
                    ctx.reply(Text.translatable("command.yachiyoc.config.reloaded"));
                }
                default -> ctx.error(Text.translatable("command.yachiyoc.config.usage", YachiyoC.prefix()));
            }
        }));
    }

    public void register(ClientCommand command) {
        commands.add(command);
        index.putIfAbsent(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            index.putIfAbsent(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public List<ClientCommand> commands() {
        return List.copyOf(commands);
    }

    public ClientCommand find(String name) {
        return index.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return true when the message should be sent to the server, false when the
     * client consumed it.
     */
    public boolean handle(String message) {
        String prefix = YachiyoC.config().data().prefix;
        if (message == null || !message.startsWith(prefix)) {
            return true;
        }
        String raw = message.substring(prefix.length()).trim();
        MinecraftClient client = MinecraftClient.getInstance();
        if (raw.isEmpty()) {
            ClientCommand help = find("help");
            if (help != null) {
                help.execute(new CommandContext(client, new String[0], ""));
            }
            return false;
        }
        String[] parts = raw.split("\\s+");
        ClientCommand command = find(parts[0]);
        if (command == null) {
            dev.yachiyoc.platform.Chat.error(
                    Text.translatable("command.yachiyoc.unknown", parts[0], prefix));
            return false;
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        try {
            command.execute(new CommandContext(client, args, raw));
        } catch (Throwable t) {
            dev.yachiyoc.platform.Chat.error(
                    Text.translatable("command.yachiyoc.failed", String.valueOf(t.getMessage())));
        }
        return false;
    }

    private int parseKey(String raw) {
        if (raw.isEmpty()) {
            return -1;
        }
        return switch (raw) {
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "rshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "lshift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "rctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "lctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "space" -> GLFW.GLFW_KEY_SPACE;
            default -> {
                try {
                    yield Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    yield -1;
                }
            }
        };
    }
}

