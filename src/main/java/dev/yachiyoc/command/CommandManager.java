package dev.yachiyoc.command;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.ModuleRegistry;
import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.platform.Keybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
                    Debug.setAll(YachiyoC.config().data().debugFeatures);
                    ctx.reply(Text.translatable("command.yachiyoc.config.reloaded"));
                }
                default -> ctx.error(Text.translatable("command.yachiyoc.config.usage", YachiyoC.prefix()));
            }
        }));

        register(new SimpleCommand("debug", List.of(), "debug [feature] [on|off]",
                "command.yachiyoc.debug.desc", ctx -> {
            String feature = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (feature.isEmpty()) {
                // 只输入 --debug：列出当前开着哪些
                Set<String> enabled = Debug.enabledFeatures();
                if (enabled.isEmpty()) {
                    ctx.reply(Text.translatable("command.yachiyoc.debug.none"));
                } else {
                    ctx.reply(Text.translatable("command.yachiyoc.debug.list",
                            String.join(", ", enabled)));
                }
                return;
            }
            String value = ctx.arg(1).toLowerCase(Locale.ROOT);
            boolean on;
            if (value.isEmpty()) {
                // all 没给值时：有开着的就全关，全关着就全开
                on = feature.equals(Debug.ALL)
                        ? Debug.enabledFeatures().isEmpty()
                        : !Debug.enabled(feature);
            } else if (value.equals("on") || value.equals("true") || value.equals("1")) {
                on = true;
            } else if (value.equals("off") || value.equals("false") || value.equals("0")) {
                on = false;
            } else {
                ctx.error(Text.translatable("command.yachiyoc.debug.usage", YachiyoC.prefix()));
                return;
            }
            Debug.set(feature, on);
            YachiyoC.config().data().debugFeatures = new LinkedHashSet<>(Debug.enabledFeatures());
            YachiyoC.config().save();
            ctx.reply(Text.translatable("command.yachiyoc.debug.set", feature,
                    Text.translatable(on ? "command.yachiyoc.state.on" : "command.yachiyoc.state.off")));
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

    public List<String> commandNames() {
        return commands.stream().map(ClientCommand::name).toList();
    }

    /**
     * Argument completions for the current commands. New commands automatically
     * appear in the first-token completion through {@link #commandNames()}; this
     * method can be extended with their argument suggestions.
     */
    public List<String> suggestionsFor(String commandName, int argIndex) {
        String name = commandName.toLowerCase(Locale.ROOT);
        if (argIndex == 1) {
            return name.equals("debug") ? List.of("on", "off") : List.of();
        }
        if (argIndex != 0) {
            return List.of();
        }
        return switch (name) {
            case "help" -> commandNames();
            case "openkey" -> List.of("up", "down", "left", "right",
                    "rshift", "lshift", "rctrl", "lctrl", "enter", "tab", "space");
            case "language" -> List.of("auto", "en_us", "zh_cn");
            case "config" -> List.of("save", "reload");
            case "debug" -> List.copyOf(Debug.knownFeatures());
            default -> List.of();
        };
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

