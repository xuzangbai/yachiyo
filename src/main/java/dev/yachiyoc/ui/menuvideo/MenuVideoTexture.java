package dev.yachiyoc.ui.menuvideo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;

import java.nio.ByteBuffer;

/** Four NV12 bytes occupy one RGBA8 texel. The GUI shader samples the planes at source resolution. */
final class MenuVideoTexture extends AbstractTexture {
    final WindowsVideo.Format format;

    MenuVideoTexture(WindowsVideo.Format format) {
        this.format = format;
        var device = RenderSystem.getDevice();
        glTexture = device.createTexture("Yachiyo menu video", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, format.textureWidth(), format.textureHeight(), 1, 1);
        try { glTextureView = device.createTextureView(glTexture); }
        catch (RuntimeException | Error exception) { close(); throw exception; }
    }

    void upload(ByteBuffer pixels) {
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(glTexture, pixels, NativeImage.Format.RGBA,
                0, 0, 0, 0, format.textureWidth(), format.textureHeight());
    }
}
