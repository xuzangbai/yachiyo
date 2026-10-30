package dev.yachiyoc.module;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ButtonSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.platform.Chat;
import net.minecraft.text.Text;

/**
 * Demo module used to exercise the UI: expand/collapse, boolean toggle,
 * slider drag, enum cycling and color cycling. It has no gameplay effect.
 */
public final class ExampleModule extends Module {
    public final BoolSetting announce = add(new BoolSetting("example", "announce", true));
    public final DoubleSetting range = add(new DoubleSetting("example", "range", 4.5, 1.0, 8.0, 0.1));
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("example", "mode", Mode.class, Mode.LEGIT));
    public final ColorSetting color = add(new ColorSetting("example", "color", 0xFF64B5F6));
    public final ButtonSetting reset = add(new ButtonSetting("example", "reset", this::resetValues));

    public ExampleModule() {
        super("example", ModuleCategory.MISC, false);
    }

    @Override
    public void onEnable() {
        if (announce.get()) {
            Chat.info(Text.translatable("module.yachiyoc.example.enabled"));
        }
    }

    @Override
    public void onDisable() {
        if (announce.get()) {
            Chat.info(Text.translatable("module.yachiyoc.example.disabled"));
        }
    }

    private void resetValues() {
        for (Setting<?> setting : settings()) {
            if (setting != reset) {
                setting.reset();
            }
        }
        Chat.info(Text.translatable("module.yachiyoc.example.reset.done"));
    }

    public enum Mode {
        LEGIT,
        SAFE,
        RAGE
    }
}
