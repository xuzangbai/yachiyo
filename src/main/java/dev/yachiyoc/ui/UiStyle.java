package dev.yachiyoc.ui;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.anim.Easing;

/** Live appearance preferences, persisted by the ordinary module settings. */
public final class UiStyle {
    private static BoolSetting motion;
    private static BoolSetting glass;
    private static DoubleSetting strength;
    private static final Anim material = new Anim(0.0F, 220L, Easing.OUT_CUBIC);

    private UiStyle() { }

    public static void configure(BoolSetting smoothMotion, BoolSetting liquidGlass, DoubleSetting glassStrength) {
        motion = smoothMotion;
        glass = liquidGlass;
        strength = glassStrength;
    }

    public static boolean animationsEnabled() {
        return motion == null || motion.get();
    }

    public static float glassAmount() {
        material.set(glass != null && glass.get() ? strength.get().floatValue() : 0.0F);
        return material.value();
    }
}
