package dev.yachiyoc.ui.anim;

/**
 * Time-based scalar animation. Unlike frame counters this is frame-rate
 * independent and survives pauses correctly.
 */
public final class Anim {
    private float value;
    private float start;
    private float target;
    private long startNanos = System.nanoTime();
    private long durationMs;
    private Easing easing;

    public Anim(float initial) {
        this(initial, 160L, Easing.OUT_CUBIC);
    }

    public Anim(float initial, long durationMs, Easing easing) {
        this.value = initial;
        this.start = initial;
        this.target = initial;
        this.durationMs = durationMs;
        this.easing = easing;
    }

    public float value() {
        update();
        return value;
    }

    public float target() {
        return target;
    }

    public void set(float next) {
        set(next, durationMs, easing);
    }

    public void set(float next, long ms, Easing ease) {
        float current = value();
        if (Math.abs(next - current) < 1.0E-4F && target == next) {
            return;
        }
        this.start = current;
        this.target = next;
        this.durationMs = Math.max(1L, ms);
        this.easing = ease;
        this.startNanos = System.nanoTime();
    }

    public void snap(float next) {
        this.value = next;
        this.start = next;
        this.target = next;
        this.startNanos = System.nanoTime();
    }

    private void update() {
        if (durationMs <= 0L) {
            value = target;
            return;
        }
        float t = (System.nanoTime() - startNanos) / (durationMs * 1_000_000.0F);
        if (t >= 1.0F) {
            value = target;
            return;
        }
        value = start + (target - start) * easing.apply(t);
    }
}
