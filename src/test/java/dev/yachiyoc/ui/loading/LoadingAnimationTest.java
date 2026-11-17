package dev.yachiyoc.ui.loading;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Headless asset, timing, playlist and Minecraft hook compatibility checks. Never opens a game or audio device. */
public final class LoadingAnimationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        LoadingAssets.Clip[] clips = LoadingAssets.read();
        check(clips.length == 6, "All six approved clips remain bundled");
        Set<String> ids = new HashSet<>();
        for (LoadingAssets.Clip clip : clips) {
            ids.add(clip.id());
            try (InputStream stream = resource(clip.id() + ".png")) {
                var image = ImageIO.read(stream);
                check(image != null && image.getWidth() == clip.atlasWidth() && image.getHeight() == clip.atlasHeight(), "Atlas matches manifest");
                check(image.getColorModel().hasAlpha(), "Symbols retain transparency for the native fade");
                int ink = 0;
                for (int y = clip.v(0); y < clip.v(0) + clip.frameHeight(); y++)
                    for (int x = clip.u(0); x < clip.u(0) + clip.frameWidth(); x++)
                        if ((image.getRGB(x, y) >>> 24) > 64) ink++;
                check(ink > 20, "Atlas contains the actual original symbols");
                image.flush();
            }
            try (var sound = AudioSystem.getAudioInputStream(new BufferedInputStream(resource(clip.audio())))) {
                check(sound.getFormat().getSampleRate() == 44100 && sound.getFormat().getChannels() == 2
                        && sound.getFormat().getSampleSizeInBits() == 16, "Original sound is bundled as startup-safe stereo PCM");
                double duration = sound.getFrameLength() / sound.getFormat().getFrameRate() * 1000;
                check(Math.abs(duration - clip.durationMs()) < 100, "Sound duration stays synchronized with its clip");
            }
            try (InputStream original = resource("originals/" + clip.id() + ".webm")) {
                check(original.readNBytes(4).length == 4, "Original clip is retained");
            }
            check(clip.frameAt(-100) == 0 && clip.frameAt(Long.MAX_VALUE) == clip.frameTimes().length - 1, "Frame lookup clamps safely");
            for (int index = 0; index < clip.frameTimes().length; index++) {
                check(clip.frameAt(clip.frameTimes()[index]) == index, "Original frame time is respected");
                if (index > 0) check(clip.frameAt(clip.frameTimes()[index] - 1) == index - 1, "Hold frame lasts until next timestamp");
            }
        }
        check(ids.equals(Set.of("s1e02_01", "s1e02_02", "s1e02_03", "s1e02_04", "s1e07_01", "s1e07_02")), "Exactly the user's four symbol and two face clips are selected");

        LoadingTimeline.Deck deck = new LoadingTimeline.Deck(6, new Random(43));
        int previous = -1;
        for (int round = 0; round < 50; round++) {
            Set<Integer> seen = new HashSet<>();
            for (int index = 0; index < 6; index++) {
                int value = deck.next();
                check(value != previous, "No repeated adjacent clips, including across shuffle boundaries");
                seen.add(value); previous = value;
            }
            check(seen.size() == 6, "Every shuffle includes every variant");
        }
        LoadingTimeline timeline = new LoadingTimeline(new int[] {1000,1000}, new LoadingTimeline.Deck(2, new Random(3)));
        var first = timeline.update(100, true);
        check(first.changed() && first.elapsedMs() == 0, "First frame starts at zero");
        var middle = timeline.update(1099, true);
        check(!middle.changed() && middle.clip() == first.clip() && middle.elapsedMs() == 999, "No early switching");
        var next = timeline.update(1100, true);
        check(next.changed() && next.clip() != first.clip() && next.elapsedMs() == 0, "Next clip begins at the exact boundary");
        var finish = timeline.update(4000, false);
        check(!finish.changed() && finish.clip() == next.clip() && finish.elapsedMs() == 999, "Completion holds the last frame without new cues or delays");
        var resume = timeline.update(100_000, true);
        check(resume.changed() && resume.elapsedMs() == 0, "A suspended window resumes without queuing stale clips");
        check(timeline.update(50, true).elapsedMs() == 0, "Backwards timestamps cannot index outside the atlas");
        close(LoadingTimeline.opacity(1000, -1, -1, false), 1, "Startup is fully visible");
        close(LoadingTimeline.opacity(1250, -1, 1000, true), 0.5F, "Resource reload fades in");
        close(LoadingTimeline.opacity(1500, 1000, -1, false), 1, "Native completion grace period is retained");
        close(LoadingTimeline.opacity(2500, 1000, -1, false), 0.5F, "Native completion fade is retained");
        close(LoadingTimeline.opacity(3000, 1000, -1, false), 0, "Finished overlay becomes fully transparent");
        checkMinecraftHooks();
        System.out.println("Loading animation checks passed: " + checks);
    }

    private static void checkMinecraftHooks() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream stream = LoadingAnimationTest.class.getResourceAsStream("/net/minecraft/client/gui/screen/SplashOverlay.class")) {
            if (stream == null) throw new AssertionError("Minecraft SplashOverlay class is missing");
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        int logos = 0, backgrounds = 0, completionHandlers = 0;
        boolean progress = false, tick = false;
        for (var method : node.methods) {
            if (method.name.equals("renderProgressBar")) progress = method.desc.equals("(Lnet/minecraft/client/gui/DrawContext;IIIIF)V");
            if (method.name.equals("tick")) tick = true;
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (method.name.equals("render") && call.owner.equals("java/util/function/IntSupplier") && call.name.equals("getAsInt")) backgrounds++;
                if (method.name.equals("render") && call.owner.equals("net/minecraft/client/gui/DrawContext") && call.name.equals("drawTexture")
                        && call.desc.equals("(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V")) logos++;
                if (method.name.equals("tick") && call.owner.equals("java/util/function/Consumer") && call.name.equals("accept")) completionHandlers++;
            }
        }
        check(logos == 2 && backgrounds == 3 && progress, "Minecraft 1.21.11 visual hook descriptors match all injection sites");
        check(tick && completionHandlers == 2, "Native success and exception completion callbacks are preserved");
    }

    private static InputStream resource(String name) {
        InputStream stream = LoadingAnimationTest.class.getResourceAsStream(LoadingAssets.ROOT + name);
        if (stream == null) throw new AssertionError("Missing bundled asset " + name);
        return stream;
    }

    private static void close(float value, float expected, String message) { check(Math.abs(value - expected) < 0.0001F, message); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
