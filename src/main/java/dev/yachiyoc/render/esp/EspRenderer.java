package dev.yachiyoc.render.esp;

import dev.yachiyoc.render.Render;
import dev.yachiyoc.render.RenderApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Per-owner target renderer with bounded crossfades and per-ping sonar state. */
public final class EspRenderer {
    private static final float PIXEL = 0.005F;
    private static final float HALO_PERIOD = 1.9F;
    private static final float HALO_LAG = 0.035F;
    private static final int HALO_SEG = 36;
    private static final double TAU = Math.PI * 2.0;
    private Layer current;
    private Layer previous;
    private World world;

    private static final class Layer {
        final EspMode mode;
        final int target;
        final EspFade fade = new EspFade();
        final SonarRenderer sonar = new SonarRenderer();
        Box box;
        EspStyle style;
        boolean throughWalls;

        Layer(EspMode mode, int target) { this.mode = mode; this.target = target; }
        boolean matches(EspMode mode, int target) { return this.mode == mode && this.target == target; }
        void dispose() { sonar.reset(); }
    }

    public void reset() {
        if (current != null) current.dispose();
        if (previous != null) previous.dispose();
        current = previous = null;
        world = null;
    }

    public void render(EspMode mode, Box box, EspStyle style, int targetId) {
        render(Render.api(), MinecraftClient.getInstance().world, mode, box, style, targetId, System.nanoTime());
    }

    /** Keep receiving frames after the target disappears, so its effect can finish fading. */
    public void fadeOut() {
        render(null, null, null, -1);
    }

    void render(RenderApi api, World world, EspMode mode, Box box, EspStyle style, int targetId, long now) {
        if (!api.inWorld()) {
            reset();
            return;
        }
        if (this.world != world) {
            reset();
            this.world = world;
        }
        if (mode != null && box != null && style != null) {
            if (current == null || !current.matches(mode, targetId)) {
                Layer incoming = previous != null && previous.matches(mode, targetId) ? previous : new Layer(mode, targetId);
                Layer outgoing = current;
                if (previous != null && previous != incoming) {
                    if (outgoing == null || previous.fade.value(now) > outgoing.fade.value(now)) {
                        if (outgoing != null) outgoing.dispose();
                        outgoing = previous;
                    } else previous.dispose();
                }
                previous = outgoing;
                current = incoming;
                if (previous != null) previous.fade.setVisible(false, now);
            }
            current.box = box;
            current.style = style;
            current.throughWalls = api.throughWalls();
            current.fade.setVisible(true, now);
        } else {
            if (current != null) current.fade.setVisible(false, now);
            if (previous != null) previous.fade.setVisible(false, now);
        }
        boolean previousThroughWalls = api.throughWalls();
        try {
            if (previous != null) drawLayer(api, world, previous, now);
            if (current != null) drawLayer(api, world, current, now);
        } finally {
            api.throughWalls(previousThroughWalls);
        }
        if (previous != null && previous.fade.finished(now)) { previous.dispose(); previous = null; }
        if (current != null && current.fade.finished(now)) { current.dispose(); current = null; }
    }

    private void drawLayer(RenderApi api, World world, Layer layer, long now) {
        float opacity = layer.fade.value(now);
        if (opacity <= 0.001F) return;
        if (world != null && (layer == previous || !layer.fade.visible())) {
            Entity target = world.getEntityById(layer.target);
            if (target != null && !target.isRemoved()) {
                Vec3d position = target.getLerpedPos(api.tickDelta());
                layer.box = target.getBoundingBox().offset(position.subtract(target.getEntityPos()));
            }
        }
        api.throughWalls(layer.throughWalls);
        if (layer.mode == EspMode.HALO) {
            halo(api, layer.box, layer.style, now, opacity);
        } else {
            layer.sonar.render(api, world, layer.box, layer.style, layer.target, now, opacity);
        }
    }

    // ---------------------------------------------------------------- 光环

    /**
     * 薰衣草光环：在目标身上做正弦上下往复，{@code haloTail} 层越来越淡的旧位置当拖尾。
     *
     * <p>拖尾不存历史：第 k 层就是"t − k·Δ 时刻的位置"，所以上下两个方向都有尾巴，
     * 也不会因为掉帧/切目标而粘一坨东西。
     */
    private static void halo(RenderApi api, Box box, EspStyle style, long now, float opacity) {
        Vec3d center = box.getCenter();
        float halfHeight = (float) (box.maxY - box.minY) * 0.5F;
        float amplitude = Math.max(0.0F, Math.min(1.0F, style.haloAmplitude())) * halfHeight;
        float radius = (float) Math.max(box.maxX - box.minX, box.maxZ - box.minZ) * 0.5F + 0.16F;
        int tail = Math.max(0, style.haloTail());
        float width = width(api, style, center);

        float cycleNanos = HALO_PERIOD * 1.0E9F;
        float t = (now % (long) cycleNanos) / cycleNanos;
        float lagPhase = HALO_LAG / HALO_PERIOD;

        for (int k = tail; k >= 0; k--) {
            float phase = t - k * lagPhase;
            float y = (float) (center.y + Math.sin(phase * Math.PI * 2.0) * amplitude);
            float fade = 1.0F - (float) k / (tail + 1.0F);
            int alpha = (int) (((style.haloColor() >>> 24) & 0xFF) * fade * fade * opacity);
            if (alpha < 3) {
                continue;
            }
            int color = withAlpha(style.haloColor(), alpha);
            Vec3d ringCenter = new Vec3d(center.x, y, center.z);
            ring(api, ringCenter, radius, color, k == 0 ? width : width * 0.75F, HALO_SEG);
            if (k == 0) {
                // 主环外侧再套一圈淡的，做出"发光"感（固定 0.05 格外扩，跟着线宽走会看不出层次）
                ring(api, ringCenter, radius + 0.05F,
                        withAlpha(color, alpha / 3), width * 0.8F, HALO_SEG);
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 水平圆环（一圈面向相机的四边形带子）。 */
    private static void ring(RenderApi api, Vec3d center, float radius, int color, float width, int segments) {
        if (radius < 0.02F || segments < 3) {
            return;
        }
        Vec3d prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = TAU * i / segments;
            Vec3d point = new Vec3d(
                    center.x + radius * Math.cos(angle),
                    center.y,
                    center.z + radius * Math.sin(angle));
            if (prev != null) {
                api.ribbon(prev, point, width, color);
            }
            prev = point;
        }
    }

    /** 把"像素粗细"按到相机的距离换算成世界宽度：近处 1x，远处逐渐加粗，上限 8x。 */
    private static float width(RenderApi api, EspStyle style, Vec3d center) {
        Vec3d camera = api.cameraPos();
        double distance = camera.distanceTo(center);
        float scale = (float) Math.max(1.0D, Math.min(8.0D, distance / 4.0D));
        return Math.max(0.002F, style.thickness() * PIXEL * scale);
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

}
