package dev.yachiyoc.platform;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    public static KeyBinding OPEN_GUI;

    private Keybinds() {
    }

    public static void init() {
        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yachiyoc.clickgui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                KeyBinding.Category.create(Identifier.of("yachiyoc", "main"))
        ));
    }

    public static void apply(int keyCode) {
        if (OPEN_GUI == null) {
            return;
        }
        OPEN_GUI.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
        KeyBinding.updateKeysByCode();
    }

    public static int code() {
        return OPEN_GUI == null ? GLFW.GLFW_KEY_UP : KeyBindingHelper.getBoundKeyOf(OPEN_GUI).getCode();
    }

    public static Text label() {
        return OPEN_GUI == null ? Text.literal("UP") : OPEN_GUI.getBoundKeyLocalizedText();
    }
}
