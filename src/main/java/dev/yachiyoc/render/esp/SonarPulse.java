package dev.yachiyoc.render.esp;

import net.minecraft.util.math.Vec3d;

/** A ping has one fixed world-space origin, shared by terrain and target surfaces. */
final class SonarPulse {
    private Object world;
    private int target;
    private long started;
    private float maxRadius, period;
    private Vec3d origin;

    void update(Object world, int target, Vec3d center, float maxRadius, float period, long now) {
        if (origin == null || this.world != world || this.target != target || this.maxRadius != maxRadius || this.period != period) {
            this.world = world;
            this.target = target;
            this.maxRadius = maxRadius;
            this.period = period;
            started = now;
            origin = center;
        } else {
            long duration = (long) (period * 1_000_000_000.0);
            long elapsed = now - started;
            if (elapsed >= duration || elapsed < 0) {
                started = elapsed < 0 ? now : started + elapsed / duration * duration;
                origin = center;
            }
        }
    }

    Vec3d origin() { return origin; }
    double phase(long now) { return Math.clamp((now - started) / (period * 1_000_000_000.0), 0, 1); }
    float radius(long now) { return (float) (Math.min(1, phase(now) / 0.82) * maxRadius); }

    float opacity(long now) {
        double phase = phase(now);
        double envelope = Math.min(Math.min(1, phase / 0.10), Math.min(1, (1 - phase) / 0.23));
        return (float) (envelope * envelope * (3 - 2 * envelope));
    }

    void reset() { origin = null; world = null; }
}
