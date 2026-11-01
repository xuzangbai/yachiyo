package dev.yachiyoc.hud;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.UiTextures;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Icy glow logo HUD. Position is handled by the HUD editor; speed drives the
 * animated highlight, while glow/outline expose the shader's other controls.
 */
public final class LogoHud extends HudElement {
    private final BoolSetting enabled =
            add(new BoolSetting("hud.logo", "enabled", true));
    private final DoubleSetting speed =
            add(new DoubleSetting("hud.logo", "speed", 1.0, 0.0, 4.0, 0.1));
    private final DoubleSetting glow =
            add(new DoubleSetting("hud.logo", "glow", 0.8, 0.0, 2.0, 0.1));
    private final DoubleSetting outline =
            add(new DoubleSetting("hud.logo", "outline", 2.0, 0.0, 4.0, 0.5));

    public LogoHud() {
        super("logo");
    }

    @Override
    public void layout() {
        width = 120.0F;
        height = 37.0F;
    }

    @Override
    public void render(DrawContext context) {
        if (!enabled.get()) {
            return;
        }
        Identifier texture = UiTextures.logoGlow();
        if (texture == null) {
            texture = UiTextures.logoMask();
        }
        if (texture == null) {
            texture = UiTextures.logo();
        }
        if (texture == null) {
            return;
        }

        double phase = (System.nanoTime() / 1.0E9 * speed.get()) % (Math.PI * 2.0);
        int phaseByte = (int) Math.round(phase / (Math.PI * 2.0) * 255.0);
        int glowByte = (int) Math.round(glow.get() / 2.0 * 255.0);
        int outlineByte = (int) Math.round(outline.get() / 4.0 * 255.0);
        int packed = (255 << 24) | (phaseByte << 16) | (glowByte << 8) | outlineByte;

        Render2D.glowTexture(context, texture, x, y, width, height, 720, 221, packed);
    }
}
