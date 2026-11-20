package dev.yachiyoc.ui.menuvideo;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.ui.anim.Anim;
import dev.yachiyoc.ui.home.YachiyoHomeScreen;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Render2D.MenuScene;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.ReconfiguringScreen;
import net.minecraft.client.gui.screen.TaskScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class MenuBackground {
    private static final Identifier TEXTURE = Identifier.of("yachiyoc", "dynamic/menu_video");
    private static final String PREFIX = "ui.yachiyoc.menu_background.";
    private static final Anim VIDEO_SHADE = new Anim(0);

    private static MenuScene scene = MenuScene.TIDAL_WALTZ;
    private static boolean activated;
    private static boolean awaitingFrame;
    private static MenuVideoLibrary.Entry selected;
    private static MenuVideoPlayback playback;
    private static MenuAudioPlayback audio;
    private static MenuVideoTexture texture;
    private static String textureVideoId;
    private static String failure;
    private static int shownClip = -1;
    private static long uploadedFrames;

    private MenuBackground() { }

    static boolean videosSupported() { return WindowsVideo.supported(); }
    public static boolean isPlaying() { return playback != null && texture != null; }
    public static boolean hasCompanionMusic() { return audio != null && audio.isPlaying(); }

    public static void registerScreenEntry() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (client.currentScreen == screen && client.world == null
                    && (screen instanceof TitleScreen || screen instanceof YachiyoHomeScreen || screen instanceof OptionsScreen)) {
                MenuBackgroundPanel.install(screen, width, height, null);
            }
        });
    }

    // Older configurations chose the classic lobby and the custom home background independently.
    private static boolean usesOriginalHome(MinecraftClient client) {
        return client.currentScreen instanceof YachiyoHomeScreen && YachiyoC.config().data().menuBackgroundScene == null;
    }

    public static boolean isLobby(MinecraftClient client) {
        var screen = client.currentScreen;
        if (screen instanceof MenuBackgroundSettingsScreen) return true;
        return client.world == null && screen != null && !usesOriginalHome(client) && !(screen instanceof ConnectScreen
                || screen instanceof DisconnectedScreen || screen instanceof ProgressScreen
                || screen instanceof ReconfiguringScreen || screen instanceof TaskScreen
                || screen instanceof CreditsScreen);
    }

    static boolean isSelected(MenuVideoLibrary.Entry entry) {
        if (usesOriginalHome(MinecraftClient.getInstance())) return entry == null;
        return entry == null ? selected == null : selected != null && selected.id().equals(entry.id());
    }

    static List<MenuVideoLibrary.Entry> videos() {
        if (!videosSupported()) return List.of();
        return MenuVideoLibrary.scan(YachiyoC.config().data().menuBackgroundVideo);
    }

    private static MenuVideoLibrary.Entry initialVideo() {
        if (!videosSupported()) return null;
        String saved = YachiyoC.config().data().menuBackgroundVideo;
        if ("".equals(saved)) return null;
        return videos().stream().filter(entry -> saved == null || entry.id().equals(saved)).findFirst().orElse(null);
    }

    static MenuScene scene() {
        return usesOriginalHome(MinecraftClient.getInstance()) ? MenuScene.HOME_FLOW : scene;
    }

    static void selectScene(MenuScene choice) {
        select(null, choice);
    }

    static void select(MenuVideoLibrary.Entry entry) {
        select(entry, scene());
    }

    private static void select(MenuVideoLibrary.Entry entry, MenuScene choice) {
        boolean same = activated && failure == null && !usesOriginalHome(MinecraftClient.getInstance()) && isSelected(entry)
                && (entry == null || selected.clips().equals(entry.clips()) && Objects.equals(selected.audio(), entry.audio()));
        String id = entry == null ? "" : entry.id();
        var settings = YachiyoC.config().data();
        scene = choice;
        if (!id.equals(settings.menuBackgroundVideo) || !choice.id.equals(settings.menuBackgroundScene)) {
            settings.menuBackgroundVideo = id;
            settings.menuBackgroundScene = choice.id;
            YachiyoC.config().save();
        }
        if (same) return;
        selected = entry;
        activated = true;
        open(selected);
    }

    private static void open(MenuVideoLibrary.Entry entry) {
        closePlayback();
        failure = null;
        shownClip = -1;
        VIDEO_SHADE.snap(0);
        if (entry != null && videosSupported()) {
            playback = new MenuVideoPlayback(entry.clips());
            awaitingFrame = true;
        } else {
            clearTexture();
        }
    }

    static void activate() {
        if (activated) return;
        activated = true;
        scene = MenuScene.fromId(YachiyoC.config().data().menuBackgroundScene);
        selected = initialVideo();
        if (selected == null || !selected.id().equals(textureVideoId)) clearTexture();
        open(selected);
    }

    public static void draw(DrawContext context) {
        activate();
        long now = System.nanoTime();
        var player = playback;
        if (player != null) {
            if (player.failure() != null) fail(player.failure());
            else {
                MenuVideoPlayback.Frame frame = player.next(now);
                if (frame != null) {
                    try { upload(frame); }
                    catch (RuntimeException | LinkageError | OutOfMemoryError e) { fail(e.toString()); }
                    finally { player.recycle(frame.pixels()); }
                }
            }
        }
        if (texture == null) {
            if (awaitingFrame) {
                context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xFF10152D);
            } else {
                if (scene == MenuScene.HOME_FLOW && MinecraftClient.getInstance().currentScreen instanceof YachiyoHomeScreen home) {
                    home.renderBackground(context, 0, 0, 0);
                } else if (scene == MenuScene.HOME_FLOW) {
                    Render2D.homeFlow(context, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0.5F, now);
                } else {
                    Render2D.menuShader(context, scene, now);
                }
            }
        } else {
            var format = texture.format;
            Render2D.menuVideo(context, TEXTURE, format.width(), format.height(), format.textureWidth(), format.textureHeight(),
                    format.aspectWidth(), format.aspectHeight(), format.rotation(), format.matrix(), format.fullRange());
            int shade = Render2D.withAlpha(0x7809111D, VIDEO_SHADE.value());
            if ((shade >>> 24) > 0) {
                context.createNewRootLayer();
                Render2D.fill(context, 0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), shade);
            }
        }
        if (audio != null) audio.gain(musicGain());
    }

    private static void upload(MenuVideoPlayback.Frame frame) {
        boolean resumeFromLastFrame = awaitingFrame && texture != null && selected.id().equals(textureVideoId);
        if (texture != null && texture.format.equals(frame.format())) texture.upload(frame.pixels());
        else {
            MenuVideoTexture next = new MenuVideoTexture(frame.format());
            try {
                next.upload(frame.pixels());
                MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, next);
            } catch (RuntimeException | Error e) { next.close(); throw e; }
            texture = next;
            Debug.log("menuvideo", "Original %dx%d %.3f fps, D3D11 reader: %s", frame.format().width(), frame.format().height(),
                    frame.format().fps(), frame.format().d3d());
        }
        textureVideoId = selected.id();
        uploadedFrames++;
        if (awaitingFrame && selected != null && selected.audio() != null) {
            audio = new MenuAudioPlayback(selected.audio(), musicGain());
        }
        awaitingFrame = false;
        if (shownClip != frame.clip()) {
            shownClip = frame.clip();
            if (!resumeFromLastFrame) {
                VIDEO_SHADE.snap(1);
                VIDEO_SHADE.set(0);
            }
        }
    }

    static Text status() {
        if (isSelected(null)) return Text.translatable(PREFIX + "scene." + scene().id);
        if (failure != null) return Text.translatable(PREFIX + "failed", selected.name());
        if (awaitingFrame || texture == null) return Text.translatable(PREFIX + "opening", selected.name());
        return Text.translatable(PREFIX + "playing", texture.format.width(), texture.format.height(),
                String.format(Locale.ROOT, "%.2f", texture.format.fps()));
    }

    private static void fail(String message) {
        failure = message;
        Debug.log("menuvideo", "Falling back for %s: %s", selected == null ? "" : selected.name(), message);
        closePlayback();
        clearTexture();
    }

    public static void tick(MinecraftClient client) {
        if (activated && !isLobby(client)) {
            activated = false;
            selected = null;
            // Keep one uploaded frame for the return to the lobby. Stop both workers immediately.
            closePlayback();
        }
    }

    private static void closePlayback() {
        awaitingFrame = false;
        if (audio != null) {
            audio.close();
            audio = null;
        }
        if (playback != null) {
            Debug.log("menuvideo", "Total uploads this launch: %d. Late frames skipped for this video: %d", uploadedFrames, playback.skipped());
            playback.close();
            playback = null;
        }
    }

    private static float musicGain() {
        return MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.MASTER) * companionVolume();
    }

    static float companionVolume() { return Math.clamp(YachiyoC.config().data().menuBackgroundVolume, 0, 1); }

    private static void clearTexture() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE);
            texture = null;
        }
        textureVideoId = null;
    }
}
