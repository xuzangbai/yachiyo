package dev.yachiyoc;

import dev.yachiyoc.command.CommandManager;
import dev.yachiyoc.config.ConfigManager;
import dev.yachiyoc.hud.HudManager;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleRegistry;
import dev.yachiyoc.module.TriggerMode;
import dev.yachiyoc.platform.Chat;
import dev.yachiyoc.platform.Keybinds;
import dev.yachiyoc.ui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * YachiyoC client entry point.
 *
 * <p>Boot order matters: config is read first, then modules restore their state,
 * then keybinds/commands are registered. UI never touches disk directly.</p>
 */
public final class YachiyoC implements ClientModInitializer {
    public static final String MOD_ID = "yachiyoc";
    public static final String NAME = "YachiyoC";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, Boolean> KEY_STATE = new HashMap<>();
    private static final Map<String, Boolean> HOLD_BASELINE = new HashMap<>();

    private static ConfigManager config;
    private static ModuleRegistry modules;
    private static CommandManager commands;

    public static ConfigManager config() {
        return config;
    }

    public static ModuleRegistry modules() {
        return modules;
    }

    public static CommandManager commands() {
        return commands;
    }

    public static String prefix() {
        return config.data().prefix;
    }

    public static String version() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    @Override
    public void onInitializeClient() {
        long start = System.currentTimeMillis();
        config = new ConfigManager();
        config.load();

        modules = new ModuleRegistry();
        modules.bootstrap();
        modules.load(config.data());

        HudManager.bootstrap();
        HudManager.load(config.data());
        HudElementRegistry.addLast(Identifier.of("yachiyoc", "hud"),
                (context, tickCounter) -> HudManager.render(context));

        commands = new CommandManager();
        commands.bootstrap();

        Keybinds.init();
        Keybinds.apply(config.data().openKey);

        ClientLifecycleEvents.CLIENT_STARTED.register(YachiyoC::onClientStarted);
        ClientTickEvents.END_CLIENT_TICK.register(YachiyoC::onEndTick);
        ClientSendMessageEvents.ALLOW_CHAT.register(commands::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(YachiyoC::saveAll, "YachiyoC-save"));

        LOGGER.info("{} {} ready in {} ms ({} modules)",
                NAME, version(), System.currentTimeMillis() - start, modules.count());
    }

    private static void onEndTick(MinecraftClient client) {
        while (Keybinds.OPEN_GUI != null && Keybinds.OPEN_GUI.wasPressed()) {
            if (client.currentScreen == null) {
                openClickGui();
            }
        }
        if (client.currentScreen == null) {
            pollModuleKeys(client);
        } else {
            releaseHeldModules();
        }
    }

    private static void pollModuleKeys(MinecraftClient client) {
        for (Module module : modules.all()) {
            int key = module.bind.get();
            boolean wasDown = KEY_STATE.getOrDefault(module.id(), false);
            boolean down = key > 0 && InputUtil.isKeyPressed(client.getWindow(), key);
            if (down && !wasDown) {
                if (module.trigger.get() == TriggerMode.HOLD) {
                    setFromKey(module, true);
                } else if (module.trigger.get() == TriggerMode.HOLD_TOGGLE) {
                    boolean baseline = module.enabled();
                    HOLD_BASELINE.put(module.id(), baseline);
                    setFromKey(module, !baseline);
                } else {
                    setFromKey(module, !module.enabled());
                }
            } else if (!down && wasDown) {
                if (module.trigger.get() == TriggerMode.HOLD) {
                    setFromKey(module, false);
                } else if (module.trigger.get() == TriggerMode.HOLD_TOGGLE) {
                    Boolean baseline = HOLD_BASELINE.remove(module.id());
                    if (baseline != null) {
                        setFromKey(module, baseline);
                    }
                }
            }
            KEY_STATE.put(module.id(), down);
        }
    }

    public static void toggleModuleFromGui(Module module) {
        setModuleEnabled(module, !module.enabled());
    }

    private static void setFromKey(Module module, boolean enabled) {
        setModuleEnabled(module, enabled);
    }

    private static void setModuleEnabled(Module module, boolean enabled) {
        boolean before = module.enabled();
        module.setEnabled(enabled);
        if (before != module.enabled() && module.toggleFeedback()) {
            Text status = Chat.status(enabled, Text.translatable(enabled
                    ? "module.yachiyoc.toggle.on"
                    : "module.yachiyoc.toggle.off"));
            Text line = Chat.prefix()
                    .append(Text.literal(enabled ? "+ " : "- ")
                            .withColor(enabled ? Chat.enabledColor() : Chat.disabledColor()))
                    .append(Chat.name(module.name()))
                    .append(Text.literal(" "))
                    .append(status);
            Chat.replaceRaw("module_toggle:" + module.id(), line);
        }
    }

    private static void releaseHeldModules() {
        for (Module module : modules.all()) {
            if (KEY_STATE.getOrDefault(module.id(), false)) {
                if (module.trigger.get() == TriggerMode.HOLD) {
                    module.setEnabled(false);
                } else if (module.trigger.get() == TriggerMode.HOLD_TOGGLE) {
                    Boolean baseline = HOLD_BASELINE.remove(module.id());
                    if (baseline != null) {
                        module.setEnabled(baseline);
                    }
                }
                KEY_STATE.put(module.id(), false);
            }
        }
    }

    public static void openClickGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ClickGuiScreen) {
            return;
        }
        client.setScreen(new ClickGuiScreen());
    }

    public static void saveAll() {
        if (config == null || modules == null) {
            return;
        }
        try {
            modules.save(config.data());
            HudManager.save(config.data());
            config.save();
        } catch (Throwable t) {
            LOGGER.error("Failed to save client state", t);
        }
    }

    public static void reloadAll() {
        config.load();
        MinecraftClient client = MinecraftClient.getInstance();
        if (applyConfiguredLanguage(client)) {
            client.reloadResources();
        }
        modules.load(config.data());
        HudManager.load(config.data());
        Keybinds.apply(config.data().openKey);
    }

    private static void onClientStarted(MinecraftClient client) {
        if (applyConfiguredLanguage(client)) {
            client.reloadResources();
        }
    }

    /**
     * Applies the language from config once Minecraft's options are available.
     * During mod init {@code client.options} is still null, so this must run from
     * CLIENT_STARTED (or later).
     *
     * @return true when a resource reload is required
     */
    public static boolean applyConfiguredLanguage(MinecraftClient client) {
        String language = config.data().language;
        if (language == null || language.equalsIgnoreCase("auto") || client.options == null) {
            return false;
        }
        if (!language.equals(client.options.language)) {
            client.options.language = language;
            client.options.write();
            return true;
        }
        return false;
    }
}
