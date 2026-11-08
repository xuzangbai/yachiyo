package dev.yachiyoc.ui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Small classpath-backed textures for the UI. Registering them dynamically means
 * the GUI does not depend on the resource-pack scan (which is unreliable for
 * single assets in dev runs) and lets us keep anti-aliased masks in code.
 */
public final class UiTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger("yachiyoc/textures");
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private UiTextures() {
    }

    public static Identifier logo() {
        return load("logo", "/assets/yachiyoc/textures/logo/logo.png");
    }

    /** White logo silhouette used by the difference/invert pipeline. */
    public static Identifier logoMask() {
        return load("logo_mask", "/assets/yachiyoc/textures/logo/logo_mask.png");
    }

    /** White alpha logo used by the icy glow shader. */
    public static Identifier logoGlow() {
        return load("logo_glow", "/assets/yachiyoc/textures/logo/logo_glow.png");
    }

    /** 1×1 纯白纹理：给「自定义片元着色器 + drawTexture」的管线当占位采样用。 */
    public static Identifier white() {
        Identifier existing = CACHE.get("white");
        if (existing != null) {
            return existing;
        }
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
            image.setColorArgb(0, 0, 0xFFFFFFFF);
            Identifier id = Identifier.of("yachiyoc", "dynamic/white");
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "yachiyoc_white", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            CACHE.put("white", id);
            return id;
        } catch (Throwable t) {
            LOGGER.warn("white texture failed", t);
            return null;
        }
    }

    private static Identifier load(String key, String path) {
        Identifier existing = CACHE.get(key);
        if (existing != null) {
            return existing;
        }
        try (InputStream stream = UiTextures.class.getResourceAsStream(path)) {
            if (stream == null) {
                LOGGER.warn("Missing bundled texture {}", path);
                return null;
            }
            NativeImage image = NativeImage.read(stream);
            Identifier id = Identifier.of("yachiyoc", "dynamic/" + key);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "yachiyoc_" + key, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            CACHE.put(key, id);
            return id;
        } catch (Throwable t) {
            LOGGER.warn("Failed to register texture {}", path, t);
            return null;
        }
    }
}
