package dev.yachiyoc.render;

import dev.yachiyoc.render.v1_21_11.RenderApiImpl;

/**
 * 渲染入口。业务代码只用 {@code Render.api()}，具体实现由版本包提供。
 *
 * <p>升级 MC：新建 {@code render.v2_x_y.RenderApiImpl}，把这里的实现换掉即可。
 */
public final class Render {
    private static final RenderApi API = new RenderApiImpl();

    private Render() {
    }

    public static RenderApi api() {
        return API;
    }
}
