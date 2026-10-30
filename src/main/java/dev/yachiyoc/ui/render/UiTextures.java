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

    /** 64x64 white rounded mask with an 18px radius, generated offline. */
    public static Identifier rounded() {
        return load("rounded", "/assets/yachiyoc/textures/ui/rounded.png");
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
