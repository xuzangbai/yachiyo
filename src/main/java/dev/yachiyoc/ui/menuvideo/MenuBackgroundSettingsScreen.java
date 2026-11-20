package dev.yachiyoc.ui.menuvideo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class MenuBackgroundSettingsScreen extends Screen {
    private final Screen parent;

    private MenuBackgroundSettingsScreen(Screen parent) {
        super(Text.translatable("ui.yachiyoc.menu_background.title"));
        this.parent = parent;
    }

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new MenuBackgroundSettingsScreen(client.currentScreen));
    }

    @Override
    protected void init() {
        MenuBackground.activate();
        MenuBackgroundPanel.install(this, width, height, this::close);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
