package dev.yachiyoc.ui.loading;

import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.UiStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Draws bundled frame atlases; all native reload lifecycle and errors remain in SplashOverlay. */
public final class LoadingScreenRenderer implements AutoCloseable {
    private static LoadingAssets.Clip[] catalogue;
    private static LoadingTimeline.Deck deck;
    private static LoadingScreenRenderer active;

    private final Map<Integer, Identifier> textures = new LinkedHashMap<>(4, 0.75F, true);
    private LoadingTimeline timeline;
    private LoadingTimeline.Playback playback;
    private LoadingAudio audio;
    private boolean failed, closed, audible;
    private long lastSoundAt;
    private int previousClip = -1, previousFrame;
    private long transitionAt;

    public boolean prepare(long nowMs, boolean finishing) {
        if (!LoadingPreferences.enabled() || failed || closed) { close(); return false; }
        try {
            if (active != null && active != this) active.close();
            active = this;
            if (catalogue == null) {
                catalogue = LoadingAssets.read();
                deck = new LoadingTimeline.Deck(catalogue.length, new Random());
            }
            if (timeline == null) timeline = new LoadingTimeline(
                    Arrays.stream(catalogue).mapToInt(LoadingAssets.Clip::durationMs).toArray(), deck);
            LoadingTimeline.Playback next = timeline.update(nowMs, !finishing);
            if (next.changed() && playback != null && next.clip() != playback.clip()) {
                previousClip = playback.clip();
                previousFrame = catalogue[previousClip].frameAt(playback.elapsedMs());
                transitionAt = nowMs;
            }
            playback = next;
            texture(playback.clip());
            return true;
        } catch (Throwable exception) {
            failed = true;
            Debug.log("loading", "Using the original loading screen: %s", exception.toString());
            close();
            return false;
        }
    }

    private Identifier texture(int index) throws IOException {
        Identifier existing = textures.get(index);
        if (existing != null) return existing;
        LoadingAssets.Clip clip = catalogue[index];
        try (InputStream stream = LoadingScreenRenderer.class.getResourceAsStream(LoadingAssets.ROOT + clip.id() + ".png")) {
            if (stream == null) throw new IOException("Missing loading atlas " + clip.id());
            NativeImage image = NativeImage.read(stream);
            if (image.getWidth() != clip.atlasWidth() || image.getHeight() != clip.atlasHeight()) {
                image.close(); throw new IOException("Wrong loading atlas dimensions " + clip.id());
            }
            Identifier id = Identifier.of("yachiyoc", "dynamic/loading/" + clip.id());
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "yachiyoc_loading_" + clip.id(), image);
            try { MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture); }
            catch (Throwable exception) { texture.close(); throw exception; }
            textures.put(index, id);
            // At most two atlases stay on the GPU; originals and every variant remain bundled.
            if (textures.size() > 2) {
                Integer oldest = textures.keySet().iterator().next();
                MinecraftClient.getInstance().getTextureManager().destroyTexture(textures.remove(oldest));
            }
            Debug.log("loading", "Loaded animation %s (%d frames)", clip.id(), clip.frameTimes().length);
            return id;
        }
    }

    public void draw(DrawContext context, float progress, float opacity, long nowMs) {
        if (closed || playback == null) return;
        LoadingAssets.Clip clip = catalogue[playback.clip()];
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        float blend = previousClip < 0 || !UiStyle.animationsEnabled() ? 1 : Math.clamp((nowMs - transitionAt) / 180.0F, 0, 1);
        if (blend < 1 && textures.containsKey(previousClip))
            frame(context, catalogue[previousClip], previousFrame, textures.get(previousClip), width, height, opacity * (1 - blend));
        frame(context, clip, clip.frameAt(playback.elapsedMs()), textures.get(playback.clip()), width, height, opacity * blend);
        Render2D.pushOpacity(opacity);
        try {
            float barWidth = Math.min(220, width * 0.38F);
            float barY = height - Math.max(24, height * 0.08F);
            Render2D.fill(context, (width - barWidth) / 2, barY, barWidth, 2, 0x22000000);
            Render2D.fill(context, (width - barWidth) / 2, barY,
                    barWidth * Math.clamp(progress, 0, 1), 2, 0xAA24200A);
        } finally { Render2D.popOpacity(); }

        MinecraftClient client = MinecraftClient.getInstance();
        float master = client.options == null ? 1 : client.options.getSoundVolume(SoundCategory.MASTER);
        float volume = LoadingPreferences.volume() * master * opacity;
        if (!LoadingPreferences.sound() || volume <= 0.0001F) {
            if (audio != null) audio.stop();
            audible = false;
        } else {
            if (audio == null) audio = new LoadingAudio();
            // Skip late sound restarts after a long suspended window or repeated GUI submissions.
            if (!audible || (playback.changed() && nowMs != lastSoundAt)) {
                audio.play(clip.audio(), playback.elapsedMs(), volume);
                lastSoundAt = nowMs;
            } else audio.gain(volume);
            audible = true;
        }
    }

    private static void frame(DrawContext context, LoadingAssets.Clip clip, int frame, Identifier texture,
                              int width, int height, float opacity) {
        if (opacity <= 0.001F) return;
        float scale = Math.min((float) width / clip.sourceWidth(), (float) height / clip.sourceHeight());
        float x = (width - clip.sourceWidth() * scale) / 2 + clip.x() * scale;
        float y = (height - clip.sourceHeight() * scale) / 2 + clip.y() * scale;
        Render2D.pushOpacity(opacity);
        try {
            Render2D.textureRegion(context, texture, x, y, clip.width() * scale, clip.height() * scale,
                    clip.u(frame), clip.v(frame), clip.frameWidth(), clip.frameHeight(), clip.atlasWidth(), clip.atlasHeight());
        } finally { Render2D.popOpacity(); }
    }

    public static void overlayChanging() { if (active != null) active.close(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (audio != null) { audio.close(); audio = null; }
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier texture : textures.values()) client.getTextureManager().destroyTexture(texture);
        textures.clear();
        if (active == this) active = null;
    }
}
