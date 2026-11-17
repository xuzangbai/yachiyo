package dev.yachiyoc.render.esp;

/** Time-based world-effect opacity; independent of the interface animation preference. */
final class EspFade {
    private static final long FADE_IN = 220_000_000L;
    private static final long FADE_OUT = 260_000_000L;
    private boolean visible;
    private float from;
    private long started;

    void setVisible(boolean next, long now) {
        if (visible == next) return;
        from = value(now);
        visible = next;
        started = now;
    }

    float value(long now) {
        double t = Math.clamp((now - started) / (double) (visible ? FADE_IN : FADE_OUT), 0, 1);
        float eased = (float) (t * t * (3 - 2 * t));
        return from + ((visible ? 1 : 0) - from) * eased;
    }

    boolean finished(long now) { return !visible && value(now) <= 0.001F; }
    boolean visible() { return visible; }
}
