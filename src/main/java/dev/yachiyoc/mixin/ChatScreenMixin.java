package dev.yachiyoc.mixin;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.command.CommandCompleter;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds Tab completion and a small suggestion list to the vanilla chat field
 * while the text starts with the client's command prefix.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected TextFieldWidget chatField;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yachiyoc$tabComplete(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        String text = chatField.getText();
        if (!text.startsWith(YachiyoC.prefix())) {
            CommandCompleter.reset();
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_UP || input.key() == GLFW.GLFW_KEY_DOWN) {
            String moved = CommandCompleter.move(text, input.key() == GLFW.GLFW_KEY_UP ? -1 : 1);
            if (moved != null) {
                chatField.setText(moved);
                chatField.setCursorToEnd(true);
                cir.setReturnValue(true);
            }
            return;
        }

        if (input.key() != GLFW.GLFW_KEY_TAB) {
            return;
        }
        String completed = CommandCompleter.tab(text);
        if (completed != null) {
            chatField.setText(completed);
            chatField.setCursorToEnd(true);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void yachiyoc$renderCompletions(DrawContext context, int mouseX, int mouseY,
                                            float delta, CallbackInfo ci) {
        String text = chatField.getText();
        if (!text.startsWith(YachiyoC.prefix())) {
            return;
        }
        List<String> labels = CommandCompleter.labels(text);
        if (labels.isEmpty()) {
            return;
        }

        int selected = CommandCompleter.selectedIndex(text);
        int shown = Math.min(labels.size(), 8);
        float width = 120.0F;
        for (int i = 0; i < shown; i++) {
            width = Math.max(width, Text2D.width(Text.literal(labels.get(i))) + 24.0F);
        }
        width = Math.min(width, 300.0F);
        float height = shown * 16.0F + 8.0F;
        float x = chatField.getX();
        float y = Math.max(4.0F, chatField.getY() - height - 4.0F);

        Render2D.roundedBox(context, x, y, width, height, 6.0F,
                Theme.WINDOW_BORDER, Theme.WINDOW);
        for (int i = 0; i < shown; i++) {
            if (i == selected) {
                Render2D.roundedRect(context, x + 3.0F, y + 2.0F + i * 16.0F,
                        width - 6.0F, 16.0F, 3.0F, Render2D.withAlpha(Theme.ACCENT, 0.18F));
            }
            Text2D.draw(context, Text.literal(labels.get(i)), x + 8.0F, y + 4.0F + i * 16.0F,
                    i == selected ? Theme.ACCENT : Theme.TEXT_DIM, false);
        }
    }
}
