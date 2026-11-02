package dev.yachiyoc.module;

import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.FontSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.platform.Chat;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.text.Text;

/**
 * Client chat display settings. This is a settings-only module; it has no
 * toggle capsule and opens the floating settings window.
 */
public final class ConfigModule extends Module {
    public final FontSetting font = add(new FontSetting("client.config", "font"));
    public final StringSetting displayPrefix =
            add(new StringSetting("client.config", "chat_prefix", "YachiyoC", Chat::setDisplayName));
    public final ColorSetting prefixColor =
            add(new ColorSetting("client.config", "prefix_color", 0xFFFF69B4, Chat::setPrefixColor));
    public final ColorSetting moduleNameColor =
            add(new ColorSetting("client.config", "module_name_color", 0xFFFFFFFF, Chat::setNameColor));
    public final ColorSetting enabledColor =
            add(new ColorSetting("client.config", "enabled_color", 0xFF4FA3FF, Chat::setEnabledColor));
    public final ColorSetting disabledColor =
            add(new ColorSetting("client.config", "disabled_color", 0xFFFF5555, Chat::setDisabledColor));

    public ConfigModule() {
        super("config", ModuleCategory.CLIENT, true, false);
        font.description(() -> Text.translatable("setting.yachiyoc.client.config.font.description",
                FontSetting.folder().toString()));
        Text2D.setFont(dev.yachiyoc.ui.render.FontChoice.VANILLA);
        Chat.setDisplayName(displayPrefix.get());
        Chat.setPrefixColor(prefixColor.get());
        Chat.setNameColor(moduleNameColor.get());
        Chat.setEnabledColor(enabledColor.get());
        Chat.setDisabledColor(disabledColor.get());
    }
}
