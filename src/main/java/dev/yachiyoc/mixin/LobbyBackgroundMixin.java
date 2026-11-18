package dev.yachiyoc.mixin;

import dev.yachiyoc.ui.menuvideo.MenuBackground;
import dev.yachiyoc.ui.menuvideo.MenuBackgroundPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

@Mixin(Screen.class)
public abstract class LobbyBackgroundMixin {
    @Redirect(method = "renderWithTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void yachiyoc$drawLobbyBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != screen || client.getOverlay() != null || !MenuBackground.isLobby(client)) {
            screen.renderBackground(context, mouseX, mouseY, delta);
            return;
        }
        MenuBackground.draw(context);
        if (!(screen instanceof TitleScreen)) {
            context.createNewRootLayer();
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0x540A0E18);
        }
    }

    @Inject(method = "onFilesDropped", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$dropIntoBackgroundLibrary(List<Path> paths, CallbackInfo ci) {
        if (MenuBackgroundPanel.acceptFileDrop((Screen) (Object) this, paths)) ci.cancel();
    }
}
