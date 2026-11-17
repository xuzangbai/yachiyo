package dev.yachiyoc.render.esp;

import dev.yachiyoc.render.RenderApi;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone regression checks; no game world, display or test-library download required. */
public final class SonarGeometryTest {
    private static int checks;

    public static void main(String[] args) {
        Box cube = new Box(0, 0, 0, 1, 1, 1), adjacent = new Box(1, 0, 0, 2, 1, 1);
        List<SonarGeometry.Face> pair = exposed(List.of(cube, adjacent));
        close(area(pair), 10, "Adjacent blocks must not expose their shared face");
        List<SonarGeometry.Face> merged = SonarGeometry.merge(pair);
        close(area(merged), 10, "Merging must preserve area");
        check(merged.size() == 6, "Two cubes must merge into six prism faces");

        Box slab = new Box(0, 0, 0, 1, 0.5, 1);
        List<SonarGeometry.Face> slabFaces = exposed(List.of(slab));
        close(area(slabFaces), 4, "Half slab exposed area");
        check(slabFaces.stream().anyMatch(f -> f.axis() == 1 && f.sign() == 1 && f.plane() == 0.5), "Slab top stays at half height");
        check(slabFaces.stream().noneMatch(f -> f.axis() == 1 && f.sign() == 1 && f.plane() == 1), "Slab must not acquire a full-block top");

        Box upperStep = new Box(0.5, 0.5, 0, 1, 1, 1);
        List<SonarGeometry.Face> stairs = exposed(List.of(slab, upperStep));
        close(area(stairs), 5.5, "Stair shape removes its interior surfaces");
        close(area(SonarGeometry.merge(stairs)), 5.5, "Stair merging preserves the riser");

        Box neighborSlab = new Box(1, 0, 0, 2, 0.5, 1);
        List<SonarGeometry.Face> partial = SonarGeometry.exposedFaces(cube, List.of(cube, neighborSlab));
        SonarGeometry.Face remainder = partial.stream().filter(f -> f.axis() == 0 && f.sign() == 1).findFirst().orElseThrow();
        close(remainder.minV(), 0.5, "Partial occlusion clips the covered half");
        close(remainder.area(), 0.5, "Partial occlusion leaves exactly half a face");

        List<SonarGeometry.Face> border = new ArrayList<>();
        for (int u = 0; u < 3; u++) for (int v = 0; v < 3; v++) {
            if (u != 1 || v != 1) border.add(new SonarGeometry.Face(1, 1, 0, u, v, u + 1, v + 1));
        }
        List<SonarGeometry.Face> borderMerged = SonarGeometry.merge(border);
        close(area(borderMerged), 8, "Merging does not fill terrain holes");
        check(borderMerged.stream().noneMatch(f -> f.minU() < 1.5 && f.maxU() > 1.5 && f.minV() < 1.5 && f.maxV() > 1.5),
                "No merged rectangle may bridge the hole");
        List<Box> far = List.of(cube.offset(-1000003, -61, 3000001), adjacent.offset(-1000003, -61, 3000001));
        close(area(SonarGeometry.merge(exposed(far))), 10, "Negative and large world coordinates preserve seams");
        SonarGeometry.Face ground = new SonarGeometry.Face(1, 1, 0, -2, -2, 2, 2);
        close(ground.minimumDistanceSquared(new Vec3d(0, 1, 0)), 1, "Sphere culling considers the face interior");
        check(ground.facesCamera(new Vec3d(0, 1, 0)) && !ground.facesCamera(new Vec3d(0, -1, 0)), "Back faces are rejected");

        SonarPulse pulse = new SonarPulse();
        Object world = new Object();
        Vec3d first = new Vec3d(1, 2, 3), moved = new Vec3d(3, 2, 3);
        long start = 5_000_000_000L;
        pulse.update(world, 7, first, 2, 2, start);
        pulse.update(world, 7, moved, 2, 2, start + 500_000_000L);
        check(pulse.origin().equals(first), "Moving a target does not drag an emitted ping");
        close(pulse.radius(start + 500_000_000L), 2 * 0.25 / 0.82, "Pulse advances continuously");
        pulse.update(world, 7, moved, 2, 2, start + 2_100_000_000L);
        check(pulse.origin().equals(moved), "The next ping uses the current target position");
        pulse.update(world, 8, first, 2, 2, start + 2_200_000_000L);
        close(pulse.phase(start + 2_200_000_000L), 0, "Target changes restart the ping");
        pulse.update(new Object(), 8, moved, 2, 2, start + 2_300_000_000L);
        close(pulse.phase(start + 2_300_000_000L), 0, "World changes restart the ping");
        pulse.reset();
        pulse.update(world, 7, first, 2, 2, start);
        close(pulse.opacity(start), 0, "Ping starts without a flash");
        check(pulse.opacity(start + 1_990_000_000L) < 0.01, "Ping fades before its origin resets");
        pulse.update(world, 7, moved, 2, 2, start + 21_000_000_000L);
        close(pulse.phase(start + 21_000_000_000L), 0.5, "Long frame gaps skip whole cycles without accumulating drift");

        AtomicBoolean through = new AtomicBoolean(true);
        AtomicInteger draws = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean();
        RenderApi api = (RenderApi) Proxy.newProxyInstance(RenderApi.class.getClassLoader(), new Class<?>[] { RenderApi.class },
                (proxy, method, values) -> switch (method.getName()) {
                    case "throughWalls" -> { if (values == null) yield through.get(); through.set((boolean) values[0]); yield null; }
                    case "cameraPos" -> new Vec3d(3, 3, 3);
                    case "sonarQuad" -> { draws.incrementAndGet(); if (fail.get()) throw new IllegalStateException("Injected render failure"); yield null; }
                    default -> null;
                });
        SonarRenderer renderer = new SonarRenderer();
        EspStyle style = new EspStyle(0xCCFF3030, 0xFFFFFFFF, 2.5F, 2, 2, 0.4F, 8, 0.6F, 5);
        renderer.render(api, null, cube, style, 7, start);
        renderer.render(api, null, cube, style, 7, start + 500_000_000L);
        check(draws.get() > 0 && through.get(), "Rendering preserves the caller's depth mode");
        fail.set(true);
        try { renderer.render(api, null, cube, style, 7, start + 600_000_000L); throw new AssertionError("Failure was not injected"); }
        catch (IllegalStateException expected) { check(through.get(), "Render errors restore the previous depth mode"); }
        checkFades();
        System.out.println("Sonar and ESP regression checks passed: " + checks);
    }

    private static void checkFades() {
        long start = 10_000_000_000L;
        EspFade fade = new EspFade();
        fade.setVisible(true, start);
        close(fade.value(start), 0, "Fade-in starts transparent");
        close(fade.value(start + 110_000_000L), 0.5, "Fade-in reaches half opacity smoothly");
        fade.setVisible(true, start + 110_000_000L);
        close(fade.value(start + 220_000_000L), 1, "Repeated visible frames must not restart the fade");
        fade.setVisible(false, start + 220_000_000L);
        close(fade.value(start + 350_000_000L), 0.5, "Fade-out has a gradual midpoint");
        fade.setVisible(true, start + 350_000_000L);
        close(fade.value(start + 350_000_000L), 0.5, "Reappearing during fade-out preserves opacity");
        close(fade.value(start + 570_000_000L), 1, "Reappearing finishes its fade-in");
        fade.setVisible(false, start + 570_000_000L);
        check(fade.finished(start + 830_000_000L), "Fade-out eventually releases its effect");

        AtomicBoolean through = new AtomicBoolean(true);
        AtomicBoolean fail = new AtomicBoolean();
        AtomicInteger draws = new AtomicInteger(), alpha = new AtomicInteger();
        List<Float> centers = new ArrayList<>();
        RenderApi api = (RenderApi) Proxy.newProxyInstance(RenderApi.class.getClassLoader(), new Class<?>[] {RenderApi.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "inWorld" -> true;
                    case "throughWalls" -> { if (values == null) yield through.get(); through.set((boolean) values[0]); yield null; }
                    case "cameraPos" -> new Vec3d(3, 3, 3);
                    case "tickDelta" -> 1.0F;
                    case "ribbon" -> {
                        if (fail.get()) throw new IllegalStateException("Injected ESP failure");
                        draws.incrementAndGet();
                        alpha.set(Math.max(alpha.get(), ((int) values[3]) >>> 24));
                        centers.add((float) ((Vec3d) values[0]).x);
                        yield null;
                    }
                    case "sonarQuad" -> { draws.incrementAndGet(); alpha.set(Math.max(alpha.get(), ((int) values[9]) >>> 24)); yield null; }
                    default -> null;
                });
        EspRenderer renderer = new EspRenderer();
        Box first = new Box(0, 0, 0, 1, 2, 1), second = first.offset(5, 0, 0);
        EspStyle style = new EspStyle(0xCCFF3030, 0xFFFFFFFF, 2.5F, 2, 2, 0.4F, 8, 0.6F, 0);
        renderer.render(api, null, EspMode.HALO, first, style, 1, start);
        check(draws.get() == 0, "New target does not appear at full brightness");
        renderer.render(api, null, EspMode.HALO, first, style, 1, start + 110_000_000L);
        check(alpha.get() >= 126 && alpha.get() <= 128, "Halo alpha respects fade-in");
        renderer.render(api, null, EspMode.HALO, first, style, 1, start + 220_000_000L);
        draws.set(0);
        renderer.render(api, null, null, null, null, -1, start + 230_000_000L);
        check(draws.get() > 0, "Lost target continues drawing while fading out");
        draws.set(0);
        renderer.render(api, null, null, null, null, -1, start + 500_000_000L);
        check(draws.get() == 0, "Lost target stops drawing after fade-out");

        renderer.reset();
        renderer.render(api, null, EspMode.HALO, first, style, 1, start);
        renderer.render(api, null, EspMode.HALO, first, style, 1, start + 220_000_000L);
        renderer.render(api, null, EspMode.HALO, second, style, 2, start + 230_000_000L);
        centers.clear();
        renderer.render(api, null, EspMode.HALO, second, style, 2, start + 340_000_000L);
        check(centers.stream().anyMatch(x -> x < 2) && centers.stream().anyMatch(x -> x > 4),
                "Switching targets crossfades their effects without sliding positions");
        renderer.render(api, null, EspMode.HALO, first, style, 1, start + 350_000_000L);
        check(through.get(), "Reversing a crossfade preserves depth state");
        fail.set(true);
        try { renderer.render(api, null, EspMode.HALO, first, style, 1, start + 360_000_000L); throw new AssertionError("Failure was not injected"); }
        catch (IllegalStateException expected) { check(through.get(), "ESP exceptions restore depth state"); }

        fail.set(false);
        renderer.reset();
        renderer.render(api, null, EspMode.SONAR, first, style, 1, start);
        renderer.render(api, null, EspMode.SONAR, first, style, 1, start + 110_000_000L);
        alpha.set(0);
        renderer.render(api, null, EspMode.SONAR, first, style, 1, start + 600_000_000L);
        check(alpha.get() > 0, "Sonar uses the same target fade lifecycle");
        draws.set(0);
        renderer.render(api, null, null, null, null, -1, start + 610_000_000L);
        check(draws.get() > 0, "Sonar echoes survive long enough to fade out");
    }

    private static List<SonarGeometry.Face> exposed(List<Box> boxes) {
        List<SonarGeometry.Face> result = new ArrayList<>();
        for (Box box : boxes) result.addAll(SonarGeometry.exposedFaces(box, boxes));
        return result;
    }

    private static double area(List<SonarGeometry.Face> faces) { return faces.stream().mapToDouble(SonarGeometry.Face::area).sum(); }
    private static void close(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1.0E-6, message + ": " + actual); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
