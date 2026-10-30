package dev.yachiyoc.ui.widget;

import dev.yachiyoc.module.setting.BindSetting;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ButtonSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.module.setting.Setting;
import dev.yachiyoc.module.setting.SoundSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.ui.RegistrySelectionScreen;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SettingRow {
    private final Setting<?> setting;
    private final Anim boolAnim;
    private final Anim hover = new Anim(0.0F, 110L, Easing.OUT_CUBIC);
    private float x;
    private float y;
    private float width;
    private boolean dragging;

    public SettingRow(Setting<?> setting) {
        this.setting = setting;
        this.boolAnim = new Anim(setting instanceof BoolSetting bool && bool.get() ? 1.0F : 0.0F,
                150L, Easing.OUT_CUBIC);
    }

    public Setting<?> setting() {
        return setting;
    }

    public boolean visible() {
        return setting.isVisible();
    }

    public float height() {
        return Theme.SETTING_HEIGHT;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public void render(DrawContext ctx, float mouseX, float mouseY, int accent) {
        if (!visible()) {
            return;
        }
        boolean hovered = contains(mouseX, mouseY);
        hover.set(hovered ? 1.0F : 0.0F);
        if (hovered) {
            Render2D.roundedRect(ctx, x, y, width, height(), Theme.SMALL_RADIUS - 1.0F, 0x14FFFFFF);
        }

        int labelSpace = setting instanceof StringSetting || setting instanceof SoundSetting
                || setting instanceof RegistryListSetting ? 132 : 82;
        Text label = Text2D.trim(setting.label(), (int) width - labelSpace);
        Text2D.draw(ctx, label, x + 8.0F, y + 7.0F, Theme.TEXT_DIM, false);

        if (setting instanceof BoolSetting bool) {
            float trackX = x + width - 34.0F;
            float trackY = y + 6.0F;
            float t = boolAnim.value();
            Render2D.roundedRect(ctx, trackX, trackY, 26.0F, 12.0F, 6.0F,
                    Render2D.mix(Theme.TRACK_OFF, accent, t * 0.75F));
            float knobX = trackX + 2.0F + t * 12.0F;
            Render2D.roundedRect(ctx, knobX, trackY + 2.0F, 8.0F, 8.0F, 4.0F, Theme.KNOB);
        } else if (setting instanceof DoubleSetting slider) {
            Text2D.drawRight(ctx, Text.literal(slider.displayValue()),
                    x + width - 8.0F, y + 6.0F, Theme.TEXT, false);
            float trackX = x + 8.0F;
            float trackY = y + 17.0F;
            float trackWidth = width - 16.0F;
            Render2D.roundedRect(ctx, trackX, trackY, trackWidth, 3.0F, 1.5F, Theme.TRACK_OFF);
            float filled = trackWidth * (float) slider.ratio();
            if (filled > 0.0F) {
                Render2D.roundedRect(ctx, trackX, trackY, filled, 3.0F, 1.5F, accent);
                Render2D.roundedRect(ctx, trackX + filled - 2.5F, trackY - 2.0F, 5.0F, 7.0F, 2.5F, Theme.KNOB);
            }
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            float pillX = x + width - 68.0F;
            float pillY = y + 5.0F;
            Render2D.roundedRect(ctx, pillX, pillY, 60.0F, 14.0F, 5.0F,
                    Render2D.withAlpha(0xFFFFFFFF, 0.06F + hover.value() * 0.05F));
            Text2D.drawCentered(ctx, Text.literal(enumSetting.displayValue()),
                    pillX + 30.0F, pillY + 3.0F, Theme.TEXT, false);
        } else if (setting instanceof ColorSetting color) {
            float swatchX = x + width - 30.0F;
            float swatchY = y + 6.0F;
            Render2D.roundedBox(ctx, swatchX, swatchY, 22.0F, 12.0F, 4.0F,
                    Theme.CARD_BORDER, color.argb());
        } else if (setting instanceof StringSetting text) {
            float fieldX = x + width - 116.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    text.editing() ? Render2D.withAlpha(accent, 0.85F) : Theme.CARD_BORDER,
                    Render2D.withAlpha(0xFFFFFFFF, 0.05F));
            Text shown = Text2D.trim(Text.literal(text.displayValue()), 92);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT, false);
            if (text.editing() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float cursorX = Math.min(fieldX + 100.0F, fieldX + 6.0F + Text2D.width(shown));
                Render2D.fill(ctx, cursorX, fieldY + 3.0F, 1.0F, 9.0F, accent);
            }
        } else if (setting instanceof SoundSetting sound) {
            float fieldX = x + width - 116.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    Theme.CARD_BORDER, Render2D.withAlpha(0xFFFFFFFF, 0.05F));
            Text shown = Text2D.trim(Text.literal(sound.displayValue()), 88);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT_DIM, false);
            Text2D.draw(ctx, Text.literal("v"), fieldX + 98.0F, fieldY + 3.0F, Theme.TEXT_MUTED, false);
        } else if (setting instanceof RegistryListSetting list) {
            float fieldX = x + width - 116.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    Render2D.withAlpha(accent, 0.60F), Render2D.withAlpha(0xFFFFFFFF, 0.05F));
            Text shown = Text2D.trim(Text.translatable("selector.yachiyoc.button", list.count()), 96);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT, false);
        } else if (setting instanceof BindSetting bind) {
            float pillX = x + width - 78.0F;
            float pillY = y + 5.0F;
            int pillColor = bind.listening()
                    ? Render2D.withAlpha(accent, 0.35F)
                    : Render2D.withAlpha(0xFFFFFFFF, 0.07F + hover.value() * 0.04F);
            Render2D.roundedRect(ctx, pillX, pillY, 70.0F, 14.0F, 5.0F, pillColor);
            Text shown = bind.listening()
                    ? Text.translatable("ui.yachiyoc.listening")
                    : Text.literal(Text2D.trim(Text.literal(bind.displayValue()), 64).getString());
            Text2D.drawCentered(ctx, shown, pillX + 35.0F, pillY + 3.0F,
                    bind.listening() ? Theme.TEXT : Theme.TEXT_DIM, false);
        } else if (setting instanceof ButtonSetting) {
            float buttonX = x + width - 54.0F;
            float buttonY = y + 4.0F;
            int fill = Render2D.mix(Render2D.withAlpha(accent, 0.22F),
                    Render2D.withAlpha(accent, 0.38F), hover.value());
            Render2D.roundedBox(ctx, buttonX, buttonY, 46.0F, 16.0F, 6.0F,
                    Render2D.withAlpha(accent, 0.55F), fill);
            Text2D.drawCentered(ctx, Text.translatable("ui.yachiyoc.run"),
                    buttonX + 23.0F, buttonY + 4.0F, Theme.TEXT, false);
        }
    }

    public boolean mouseClicked(Click click) {
        if (!visible() || !contains(click.x(), click.y())) {
            return false;
        }
        if (setting instanceof BoolSetting bool) {
            bool.toggle();
            boolAnim.set(bool.get() ? 1.0F : 0.0F);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            enumSetting.cycle();
        } else if (setting instanceof ColorSetting color) {
            color.cycle();
        } else if (setting instanceof DoubleSetting slider) {
            dragging = true;
            updateSlider(slider, click.x());
        } else if (setting instanceof RegistryListSetting list) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new RegistrySelectionScreen(list, client.currentScreen));
        } else if (setting instanceof StringSetting text) {
            text.startEditing();
        } else if (setting instanceof SoundSetting sound) {
            sound.setAnchor(x + width - 116.0F, y + 19.0F);
            sound.toggleOpen();
        } else if (setting instanceof BindSetting bind) {
            bind.startListening();
        } else if (setting instanceof ButtonSetting button) {
            button.run();
        }
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (setting instanceof BindSetting bind && bind.listening()) {
            bind.setKey(input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key());
            return true;
        }
        return false;
    }

    public void cancelListening() {
        if (setting instanceof BindSetting bind) {
            bind.cancelListening();
        }
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && setting instanceof DoubleSetting slider) {
            updateSlider(slider, click.x());
            return true;
        }
        return false;
    }

    public void mouseReleased(Click click) {
        dragging = false;
    }

    private void updateSlider(DoubleSetting slider, double mouseX) {
        float trackX = x + 8.0F;
        float trackWidth = width - 16.0F;
        slider.setFromRatio((mouseX - trackX) / trackWidth);
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height();
    }
}
