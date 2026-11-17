package dev.yachiyoc.ui.loading;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;

/** Defaults also work before the client settings module has finished initializing. */
public final class LoadingPreferences {
    private static BoolSetting animation, sound;
    private static DoubleSetting volume;

    private LoadingPreferences() { }

    public static void configure(BoolSetting enabled, BoolSetting audio, DoubleSetting gain) {
        animation = enabled; sound = audio; volume = gain;
    }

    public static boolean enabled() { return animation == null || animation.get(); }
    public static boolean sound() { return sound == null || sound.get(); }
    public static float volume() { return volume == null ? 0.5F : volume.get().floatValue(); }
}
