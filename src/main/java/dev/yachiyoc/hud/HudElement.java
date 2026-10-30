package dev.yachiyoc.hud;

import dev.yachiyoc.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A movable HUD widget. HUD elements are independent of module categories and
 * are added/removed in the HUD editor.
 */
public abstract class HudElement {
    private final String id;
    private final List<Setting<?>> settings = new ArrayList<>();
    protected float x;
    protected float y;
    protected float width;
    protected float height;

    protected HudElement(String id) {
        this.id = id;
    }

    protected final <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public final String id() {
        return id;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public Text name() {
        return Text.translatable("hud.yachiyoc." + id);
    }

    public Text description() {
        return Text.translatable("hud.yachiyoc." + id + ".description");
    }

    public final float x() {
        return x;
    }

    public final float y() {
        return y;
    }

    public final float width() {
        return width;
    }

    public final float height() {
        return height;
    }

    public final void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public abstract void layout();

    public abstract void render(DrawContext context);
}
