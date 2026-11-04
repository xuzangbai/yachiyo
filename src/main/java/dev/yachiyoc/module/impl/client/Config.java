package dev.yachiyoc.module.impl.client;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.FontSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.platform.Chat;
import dev.yachiyoc.rotation.RotationManager;
import dev.yachiyoc.rotation.RotationMode;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.text.Text;

/**
 * Client chat display settings. This is a settings-only module; it has no
 * toggle capsule and opens the floating settings window.
 */
public final class Config extends Module {
    public final EnumSetting<RotationMode> rotationMode = add(new EnumSetting<>(
            "client.config", "rotation_mode", RotationMode.class,
            RotationMode.SILENT_MOVEMENT, RotationManager::setDefaultMode));
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

    public Config() {
        super("config", ModuleCategory.CLIENT, true, false);
        RotationManager.setDefaultMode(rotationMode.get());
        rotationMode.description(() -> Text.translatable("setting.yachiyoc.client.config.rotation_mode.description"));
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
