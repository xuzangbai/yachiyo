package dev.yachiyoc.ui.widget;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.setting.BindSetting;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ButtonSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.FontSetting;
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
    private final Anim resetHover = new Anim(0.0F, 100L, Easing.OUT_CUBIC);
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

    public void refresh() {
        if (setting instanceof BoolSetting bool) {
            boolAnim.set(bool.get() ? 1.0F : 0.0F);
        }
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
        if (hovered) {
            SettingDescriptionOverlay.set(setting.description());
        }
        hover.set(hovered ? 1.0F : 0.0F);
        if (hovered) {
            Render2D.roundedRect(ctx, x, y, width, height(), Theme.SMALL_RADIUS - 1.0F, Theme.ROW_HOVER);
        }

        // 编辑中的数值输入框：靠右、不铺满整行（最多 96px），左边照常显示设置名
        DoubleSetting editingSlider = setting instanceof DoubleSetting ds && ds.editing() ? ds : null;
        float editBoxW = editingSlider == null ? 0.0F : Math.min(96.0F, width - 60.0F);
        float editBoxX = x + width - 26.0F - editBoxW;
        int labelSpace = setting instanceof StringSetting || setting instanceof SoundSetting
                || setting instanceof RegistryListSetting ? 132 : 82;
        int labelWidth = editingSlider == null
                ? (int) width - labelSpace
                : (int) Math.max(10.0F, editBoxX - x - 12.0F);
        Text label = Text2D.trim(setting.label(), labelWidth);
        Text2D.draw(ctx, label, x + 8.0F, y + 7.0F, Theme.TEXT_DIM, false);

        if (setting instanceof BoolSetting bool) {
            float trackX = x + width - 54.0F;
            float trackY = y + 6.0F;
            float t = boolAnim.value();
            Render2D.roundedRect(ctx, trackX, trackY, 26.0F, 12.0F, 6.0F,
                    Render2D.mix(Theme.TRACK_OFF, accent, t * 0.75F));
            float knobX = trackX + 2.0F + t * 12.0F;
            Render2D.roundedRect(ctx, knobX, trackY + 2.0F, 8.0F, 8.0F, 4.0F, Theme.KNOB);
        } else if (setting instanceof DoubleSetting slider) {
            if (slider.editing()) {
                // 输入框只占右侧一块（宽度见 editBoxW），左边设置名照常显示，互不遮挡
                float boxX = editBoxX;
                float boxY = y + 5.0F;
                float boxW = editBoxW;
                float boxH = 13.0F;
                Render2D.roundedBox(ctx, boxX, boxY, boxW, boxH, 3.5F,
                        Render2D.withAlpha(accent, 0.9F), Theme.FIELD);
                Text shown = Text2D.trim(Text.literal(slider.buffer()), (int) (boxW - 12.0F));
                float textX = boxX + 5.0F;
                Text2D.draw(ctx, shown, textX, boxY + 3.0F, Theme.TEXT, false);
                if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                    float cursorX = Math.min(boxX + boxW - 6.0F, textX + Text2D.width(shown));
                    Render2D.fill(ctx, cursorX, boxY + 3.0F, 1.0F, 7.0F, accent);
                }
            } else {
                Text valueText = Text.literal(slider.displayValue());
                float valueW = Text2D.width(valueText);
                float valueRight = x + width - 26.0F;
                boolean valueHovered = isOnValue(slider, mouseX, mouseY);
                if (valueHovered) {
                    // 悬停提示块只包住数字本身：左右各 3px，上边 y+4.5、高 11 —— 底边停在 y+15.5，
                    // 不会压到 y+17 的滑条（之前 y+7 高 10 正好把滑条那一截盖掉了）
                    Render2D.roundedRect(ctx, valueRight - valueW - 3.0F, y + 4.5F,
                            valueBlockWidth(slider), 11.0F, 3.0F, Render2D.withAlpha(accent, 0.16F));
                }
                Text2D.drawRight(ctx, valueText, valueRight, y + 6.0F,
                        valueHovered ? accent : Theme.TEXT, false);
                float trackX = x + 8.0F;
                float trackY = y + 17.0F;
                float trackWidth = width - 36.0F;
                Render2D.roundedRect(ctx, trackX, trackY, trackWidth, 3.0F, 1.5F, Theme.TRACK_OFF);
                float filled = trackWidth * (float) slider.ratio();
                if (filled > 0.0F) {
                    Render2D.roundedRect(ctx, trackX, trackY, filled, 3.0F, 1.5F, accent);
                    Render2D.roundedRect(ctx, trackX + filled - 2.5F, trackY - 2.0F, 5.0F, 7.0F, 2.5F, Theme.KNOB);
                }
            }
        } else if (setting instanceof FontSetting fontSetting) {
            float pillX = x + width - 88.0F;
            float pillY = y + 5.0F;
            Render2D.roundedRect(ctx, pillX, pillY, 60.0F, 14.0F, 5.0F,
                    Render2D.mix(Theme.FIELD, Theme.FIELD_HOVER, hover.value()));
            Text2D.drawCentered(ctx, Text2D.trim(Text.literal(fontSetting.displayValue() + " v"), 56),
                    pillX + 30.0F, pillY + 3.0F, Theme.TEXT, false);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            float pillX = x + width - 88.0F;
            float pillY = y + 5.0F;
            Render2D.roundedRect(ctx, pillX, pillY, 60.0F, 14.0F, 5.0F,
                    Render2D.mix(Theme.FIELD, Theme.FIELD_HOVER, hover.value()));
            Text2D.drawCentered(ctx, Text2D.trim(Text.literal(enumSetting.displayValue() + " v"), 56),
                    pillX + 30.0F, pillY + 3.0F, Theme.TEXT, false);
        } else if (setting instanceof ColorSetting color) {
            float swatchX = x + width - 50.0F;
            float swatchY = y + 6.0F;
            Render2D.roundedBox(ctx, swatchX, swatchY, 22.0F, 12.0F, 4.0F,
                    Theme.CARD_BORDER, color.argb());
        } else if (setting instanceof StringSetting text) {
            float fieldX = x + width - 136.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    text.editing() ? Render2D.withAlpha(accent, 0.85F) : Theme.CARD_BORDER,
                    Theme.FIELD);
            Text shown = Text2D.trim(Text.literal(text.displayValue()), 92);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT, false);
            if (text.editing() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float cursorX = Math.min(fieldX + 100.0F, fieldX + 6.0F + Text2D.width(shown));
                Render2D.fill(ctx, cursorX, fieldY + 3.0F, 1.0F, 9.0F, accent);
            }
        } else if (setting instanceof SoundSetting sound) {
            float fieldX = x + width - 136.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    Theme.CARD_BORDER, Theme.FIELD);
            Text shown = Text2D.trim(Text.literal(sound.displayValue()), 88);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT_DIM, false);
            Text2D.draw(ctx, Text.literal("v"), fieldX + 98.0F, fieldY + 3.0F, Theme.TEXT_MUTED, false);
        } else if (setting instanceof RegistryListSetting list) {
            float fieldX = x + width - 136.0F;
            float fieldY = y + 5.0F;
            Render2D.roundedBox(ctx, fieldX, fieldY, 108.0F, 14.0F, 5.0F,
                    Render2D.withAlpha(accent, 0.60F), Theme.FIELD);
            Text shown = Text2D.trim(Text.translatable("selector.yachiyoc.button", list.count()), 96);
            Text2D.draw(ctx, shown, fieldX + 6.0F, fieldY + 3.0F, Theme.TEXT, false);
        } else if (setting instanceof BindSetting bind) {
            float pillX = x + width - 98.0F;
            float pillY = y + 5.0F;
            int pillColor = bind.listening()
                    ? Render2D.withAlpha(accent, 0.35F)
                    : Render2D.mix(Theme.FIELD, Theme.FIELD_HOVER, hover.value());
            Render2D.roundedRect(ctx, pillX, pillY, 70.0F, 14.0F, 5.0F, pillColor);
            Text shown = bind.listening()
                    ? Text.translatable("ui.yachiyoc.listening")
                    : Text.literal(Text2D.trim(Text.literal(bind.displayValue()), 64).getString());
            Text2D.drawCentered(ctx, shown, pillX + 35.0F, pillY + 3.0F,
                    bind.listening() ? Theme.TEXT : Theme.TEXT_DIM, false);
        } else if (setting instanceof ButtonSetting) {
            float buttonX = x + width - 74.0F;
            float buttonY = y + 4.0F;
            int fill = Render2D.mix(Render2D.withAlpha(accent, 0.22F),
                    Render2D.withAlpha(accent, 0.38F), hover.value());
            Render2D.roundedBox(ctx, buttonX, buttonY, 46.0F, 16.0F, 6.0F,
                    Render2D.withAlpha(accent, 0.55F), fill);
            Text2D.drawCentered(ctx, Text.translatable("ui.yachiyoc.run"),
                    buttonX + 23.0F, buttonY + 4.0F, Theme.TEXT, false);
        }

        if (!(setting instanceof ButtonSetting)) {
            float resetX = x + width - 18.0F;
            float resetY = y + 5.0F;
            boolean resetHovered = mouseX >= resetX && mouseX <= resetX + 14
                    && mouseY >= resetY && mouseY <= resetY + 14;
            resetHover.set(resetHovered ? 1.0F : 0.0F);
            Render2D.roundedRect(ctx, resetX, resetY, 14.0F, 14.0F, 4.0F,
                    Render2D.withAlpha(accent, 0.08F + resetHover.value() * 0.20F));
            Text2D.drawCentered(ctx, Text.literal("↺"), resetX + 7.0F, resetY + 3.0F,
                    resetHovered ? accent : Theme.TEXT_MUTED, false);
        }
    }

    public boolean mouseClicked(Click click) {
        if (!visible() || !contains(click.x(), click.y())) {
            return false;
        }
        if (click.button() == 0 && !(setting instanceof ButtonSetting)) {
            float resetX = x + width - 18.0F;
            if (click.x() >= resetX && click.x() <= resetX + 14
                    && click.y() >= y + 5.0F && click.y() <= y + 19.0F) {
                setting.reset();
                refresh();
                YachiyoC.config().save();
                return true;
            }
        }
        // 点别的设置就把正在编辑的数值输入框收掉（提交）
        DoubleSetting activeNumber = DoubleSetting.active();
        if (activeNumber != null && activeNumber != setting) {
            activeNumber.commit();
            YachiyoC.config().save();
        }
        if (setting instanceof BoolSetting bool) {
            bool.toggle();
            boolAnim.set(bool.get() ? 1.0F : 0.0F);
        } else if (setting instanceof FontSetting fontSetting) {
            MinecraftClient client = MinecraftClient.getInstance();
            FontDropdownPopup.toggle(fontSetting, x + width - 88.0F, y + 19.0F, 160.0F,
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            MinecraftClient client = MinecraftClient.getInstance();
            EnumDropdownPopup.toggle(enumSetting, x + width - 88.0F, y + 19.0F, 130.0F,
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        } else if (setting instanceof ColorSetting color) {
            if (click.button() == 1) {
                SoundSetting.closeOpen();
                MinecraftClient client = MinecraftClient.getInstance();
                ColorPickerPopup.toggle(color, x + width - 30.0F, y + 19.0F,
                        client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            }
        } else if (setting instanceof DoubleSetting slider) {
            if (isOnValue(slider, click.x(), click.y())) {
                // 点数值直接进输入框
                slider.startEditing();
            } else {
                DoubleSetting.stopAllEditing();
                dragging = true;
                updateSlider(slider, click.x());
            }
        } else if (setting instanceof RegistryListSetting list) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new RegistrySelectionScreen(list, client.currentScreen));
        } else if (setting instanceof StringSetting text) {
            text.startEditing();
        } else if (setting instanceof SoundSetting sound) {
            ColorPickerPopup.close();
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
        float trackWidth = width - 36.0F;
        slider.setFromRatio((mouseX - trackX) / trackWidth);
    }

    /** 鼠标是否在数值文字上（点它进输入框）；区域按数字实际宽度算，避开右侧重置按钮。 */
    private boolean isOnValue(DoubleSetting slider, double mouseX, double mouseY) {
        float valueW = Text2D.width(Text.literal(slider.displayValue()));
        float right = x + width - 26.0F;
        return mouseX >= right - valueW - 6.0F && mouseX <= right + 6.0F
                && mouseY >= y + 3.0F && mouseY <= y + 19.0F;
    }

    /** 数字那一块的实际宽度（左右各 3px 内边距）。 */
    private float valueBlockWidth(DoubleSetting slider) {
        return Text2D.width(Text.literal(slider.displayValue())) + 6.0F;
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height();
    }
}
