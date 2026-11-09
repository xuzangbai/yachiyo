package dev.yachiyoc.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 世界渲染的回调表：模块在 {@code onEnable} 注册、{@code onDisable} 退订。
 *
 * <p>和 HUD 那套一样是"注册式"，但只在世界渲染帧里被调用（HUD 关掉时也照常画）。
 */
public final class Render3DCallbacks {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private Render3DCallbacks() {
    }

    public static void register(Runnable listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void clear() {
        LISTENERS.clear();
    }

    public static void dispatch() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable ignored) {
                // 单个模块画崩不能拖垮整帧
            }
        }
    }
}
