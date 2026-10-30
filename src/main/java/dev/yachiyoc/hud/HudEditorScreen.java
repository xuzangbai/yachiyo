package dev.yachiyoc.hud;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.setting.SoundSetting;
import dev.yachiyoc.module.setting.StringSetting;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import dev.yachiyoc.ui.widget.SettingRow;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transparent HUD editor. The ClickGUI is closed before this screen opens, so
 * only the game + HUD remain. Right-click empty space to add an element,
 * right-click an element for its settings, left-drag to move it.
 */
public final class HudEditorScreen extends Screen {
    private HudElement dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private HudElement selected;
    private HudElement settingsTarget;
    private final List<SettingRow> settingRows = new ArrayList<>();
    private final Map<HudElement, float[]> panelPositions = new HashMap<>();
    private boolean draggingPanel;
    private float panelDragOffsetX;
    private float panelDragOffsetY;

    private boolean addMenuOpen;
    private float menuX;
    private float menuY;
    private final long openedAt = System.currentTimeMillis();

    private float panelX;
    private float panelY;
    private float panelWidth = 244.0F;
    private float panelHeight;

    public HudEditorScreen() {
        super(Text.translatable("hud.yachiyoc.editor"));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // HUD elements themselves are drawn by HudManager's HUD callback, so the
        // editor only adds outlines, the add menu and the settings panel.
        HudManager.renderEditorOverlay(context, mouseX, mouseY, selected);

        Render2D.roundedRect(context, 10.0F, 10.0F, 270.0F, 30.0F, 8.0F, 0xB810141C);
        Text2D.draw(context, Text.translatable("hud.yachiyoc.editor"), 20.0F, 16.0F,
                Theme.TEXT, false);
        Text2D.draw(context, Text.translatable("hud.yachiyoc.hint"), 20.0F, 28.0F,
                Theme.TEXT_MUTED, false);

        if (addMenuOpen) {
            renderAddMenu(context, mouseX, mouseY);
        }
        if (settingsTarget != null) {
            renderSettings(context, mouseX, mouseY);
        }
        SoundSetting.renderOpen(context, mouseX, mouseY, width, height);
    }

    private void renderAddMenu(DrawContext context, int mouseX, int mouseY) {
        List<HudManager.HudType> types = HudManager.types();
        float w = 190.0F;
        float h = 34.0F + types.size() * 30.0F + 6.0F;
        float x = clamp(menuX, 8.0F, width - w - 8.0F);
        float y = clamp(menuY, 8.0F, height - h - 8.0F);

        Render2D.roundedBox(context, x, y, w, h, 10.0F, Theme.CARD_BORDER, 0xF2141922);
        Text2D.draw(context, Text.translatable("hud.yachiyoc.add"), x + 10.0F, y + 9.0F,
                Theme.TEXT_DIM, false);

        float rowY = y + 30.0F;
        for (HudManager.HudType type : types) {
            boolean hovered = mouseX >= x + 6 && mouseX <= x + w - 6
                    && mouseY >= rowY && mouseY <= rowY + 26;
            if (hovered) {
                Render2D.roundedRect(context, x + 6.0F, rowY, w - 12.0F, 26.0F, 6.0F,
                        Render2D.withAlpha(Theme.ACCENT, 0.18F));
            }
            Text2D.draw(context, Text.translatable("hud.yachiyoc." + type.id()), x + 12.0F,
                    rowY + 4.0F, Theme.TEXT, false);
            Text2D.draw(context, Text.translatable(type.descriptionKey()), x + 12.0F,
                    rowY + 14.0F, Theme.TEXT_MUTED, false);
            rowY += 30.0F;
        }
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY) {
        int visible = 0;
        for (SettingRow row : settingRows) {
            if (row.visible()) {
                visible++;
            }
        }
        panelHeight = 40.0F + visible * Theme.SETTING_HEIGHT + 6.0F;
        float[] stored = panelPositions.get(settingsTarget);
        if (stored != null) {
            panelX = stored[0];
            panelY = stored[1];
        } else {
            panelX = settingsTarget.x() + settingsTarget.width() + 8.0F;
            panelY = settingsTarget.y();
        }
        panelX = clamp(panelX, 8.0F, width - panelWidth - 8.0F);
        panelY = clamp(panelY, 8.0F, height - panelHeight - 8.0F);

        Render2D.roundedBox(context, panelX, panelY, panelWidth, panelHeight, 10.0F,
                Theme.CARD_BORDER, 0xF2141922);
        Text2D.draw(context, settingsTarget.name(), panelX + 10.0F, panelY + 9.0F,
                Theme.TEXT, false);
        drawDelete(context, panelX + panelWidth - 56.0F, panelY + 6.0F, mouseX, mouseY);
        drawClose(context, panelX + panelWidth - 20.0F, panelY + 6.0F, mouseX, mouseY);

        float rowY = panelY + 30.0F;
        for (SettingRow row : settingRows) {
            if (!row.visible()) {
                continue;
            }
            row.setBounds(panelX + 8.0F, rowY, panelWidth - 16.0F);
            row.render(context, mouseX, mouseY, Theme.ACCENT);
            rowY += Theme.SETTING_HEIGHT;
        }
    }

    private void drawDelete(DrawContext context, float x, float y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 32 && mouseY >= y && mouseY <= y + 14;
        Render2D.roundedRect(context, x, y, 32.0F, 14.0F, 4.0F,
                hovered ? 0x44FF5A5A : 0x18FFFFFF);
        Text2D.drawCentered(context, Text.translatable("hud.yachiyoc.delete"),
                x + 16.0F, y + 3.0F, hovered ? 0xFFFF9A9A : Theme.TEXT_DIM, false);
    }

    private void drawClose(DrawContext context, float x, float y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 14 && mouseY >= y && mouseY <= y + 14;
        Render2D.roundedRect(context, x, y, 14.0F, 14.0F, 4.0F,
                hovered ? 0x44FF5A5A : 0x18FFFFFF);
        for (int i = 0; i < 6; i++) {
            Render2D.fill(context, x + 4 + i, y + 4 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
            Render2D.fill(context, x + 9 - i, y + 4 + i, 1.0F, 1.0F, Theme.TEXT_DIM);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (System.currentTimeMillis() - openedAt < 180L) {
            return true;
        }
        if (SoundSetting.mouseClickedOpen(click, width, height)) {
            save();
            return true;
        }
        StringSetting.stopAllEditing();
        if (addMenuOpen) {
            List<HudManager.HudType> types = HudManager.types();
            float w = 190.0F;
            float h = 34.0F + types.size() * 30.0F + 6.0F;
            float x = clamp(menuX, 8.0F, width - w - 8.0F);
            float y = clamp(menuY, 8.0F, height - h - 8.0F);

            float rowY = y + 30.0F;
            for (HudManager.HudType type : types) {
                if (click.x() >= x + 6 && click.x() <= x + w - 6
                        && click.y() >= rowY && click.y() <= rowY + 26) {
                    HudElement element = HudManager.add(type.id(), x + 10.0F, rowY + 4.0F);
                    addMenuOpen = false;
                    if (element != null) {
                        selected = element;
                        if (click.button() == 1) {
                            openSettings(element);
                        }
                        save();
                    }
                    return true;
                }
                rowY += 30.0F;
            }
            if (click.x() < x || click.x() > x + w || click.y() < y || click.y() > y + h) {
                addMenuOpen = false;
            }
            return true;
        }

        if (settingsTarget != null) {
            float deleteX = panelX + panelWidth - 56.0F;
            if (click.x() >= deleteX && click.x() <= deleteX + 32
                    && click.y() >= panelY + 6 && click.y() <= panelY + 20) {
                HudElement removed = settingsTarget;
                HudManager.remove(removed);
                panelPositions.remove(removed);
                if (selected == removed) {
                    selected = null;
                }
                settingsTarget = null;
                settingRows.clear();
                draggingPanel = false;
                save();
                return true;
            }
            float closeX = panelX + panelWidth - 20.0F;
            if (click.x() >= closeX && click.x() <= closeX + 14
                    && click.y() >= panelY + 6 && click.y() <= panelY + 20) {
                settingsTarget = null;
                draggingPanel = false;
                return true;
            }
            if (click.x() >= panelX && click.x() <= panelX + panelWidth
                    && click.y() >= panelY && click.y() <= panelY + 24) {
                draggingPanel = true;
                panelDragOffsetX = (float) click.x() - panelX;
                panelDragOffsetY = (float) click.y() - panelY;
                return true;
            }
            for (SettingRow row : settingRows) {
                if (row.visible() && row.mouseClicked(click)) {
                    save();
                    return true;
                }
            }
            if (click.x() >= panelX && click.x() <= panelX + panelWidth
                    && click.y() >= panelY && click.y() <= panelY + panelHeight) {
                return true;
            }
            settingsTarget = null;
        }

        HudElement hit = HudManager.findAt(click.x(), click.y());
        if (hit != null) {
            selected = hit;
            if (click.button() == 1) {
                openSettings(hit);
            } else if (click.button() == 0) {
                dragging = hit;
                dragOffsetX = (float) click.x() - hit.x();
                dragOffsetY = (float) click.y() - hit.y();
            }
            return true;
        }

        if (click.button() == 1) {
            addMenuOpen = true;
            menuX = (float) click.x();
            menuY = (float) click.y();
            return true;
        }

        selected = null;
        return false;
    }

    private void openSettings(HudElement element) {
        settingsTarget = element;
        settingRows.clear();
        element.settings().forEach(setting -> settingRows.add(new SettingRow(setting)));
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingPanel && settingsTarget != null) {
            panelX = clamp((float) click.x() - panelDragOffsetX, 8.0F, width - panelWidth - 8.0F);
            panelY = clamp((float) click.y() - panelDragOffsetY, 8.0F, height - panelHeight - 8.0F);
            panelPositions.put(settingsTarget, new float[]{panelX, panelY});
            return true;
        }
        if (settingsTarget != null) {
            for (SettingRow row : settingRows) {
                if (row.mouseDragged(click, deltaX, deltaY)) {
                    return true;
                }
            }
        }
        if (dragging != null) {
            dragging.setPosition(clamp((float) click.x() - dragOffsetX, 0.0F, width - dragging.width()),
                    clamp((float) click.y() - dragOffsetY, 0.0F, height - dragging.height()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        for (SettingRow row : settingRows) {
            row.mouseReleased(click);
        }
        if (draggingPanel) {
            draggingPanel = false;
            save();
            return true;
        }
        if (settingsTarget != null) {
            save();
        }
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        StringSetting editing = StringSetting.active();
        if (editing != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) {
                editing.stopEditing();
            } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                editing.backspace();
                save();
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsTarget != null) {
                settingsTarget = null;
            } else if (addMenuOpen) {
                addMenuOpen = false;
            } else {
                close();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        StringSetting editing = StringSetting.active();
        if (editing != null) {
            editing.append((char) input.codepoint());
            save();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        SoundSetting.closeOpen();
        StringSetting.stopAllEditing();
        save();
        super.removed();
    }

    private void save() {
        HudManager.save(YachiyoC.config().data());
        YachiyoC.config().save();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
