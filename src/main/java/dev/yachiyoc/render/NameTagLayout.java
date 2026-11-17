package dev.yachiyoc.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Screen-space layout math shared by world billboards and headless regression checks. */
public final class NameTagLayout {
    private NameTagLayout() { }

    public static float unitsPerPixel(double viewDepth, double projectionY, int viewportHeight) {
        if (viewDepth <= 0.05 || projectionY <= 0 || viewportHeight <= 0) return 0;
        return (float) (2 * viewDepth / (projectionY * viewportHeight));
    }

    /** Wrap without dropping item names, enchantments, effects or Unicode code points. */
    public static List<String> wrap(String text, double width, ToDoubleFunction<String> measure) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            if (point == '\n') { lines.add(line.toString()); line.setLength(0); continue; }
            String next = line.toString() + new String(Character.toChars(point));
            if (!line.isEmpty() && measure.applyAsDouble(next) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.appendCodePoint(point);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public static final class Vitals {
        private long lastTime, holdUntil;
        private boolean initialized;
        private double target, fill, trail, absorption, maximum;

        public void update(double health, double maxHealth, double absorbed, long now) {
            double max = Math.max(1, maxHealth);
            double next = Math.clamp(health / max, 0, 1);
            double nextAbsorption = Math.clamp(absorbed / max, 0, 1);
            if (!initialized || now < lastTime || Math.abs(maximum - max) > 0.001) {
                fill = trail = target = next;
                absorption = nextAbsorption;
                initialized = true;
                holdUntil = now;
            } else {
                double dt = Math.max(0, (now - lastTime) / 1_000_000_000.0);
                if (next < target - 0.0001) {
                    trail = Math.max(trail, fill);
                    holdUntil = now + 350_000_000L;
                }
                fill += (next - fill) * (1 - Math.exp(-dt / 0.085));
                absorption += (nextAbsorption - absorption) * (1 - Math.exp(-dt / 0.10));
                if (next >= trail) trail = Math.max(next, fill);
                else if (now > holdUntil) {
                    double afterHold = Math.min(dt, (now - holdUntil) / 1_000_000_000.0);
                    trail += (fill - trail) * (1 - Math.exp(-afterHold / 0.22));
                }
                trail = Math.max(fill, trail);
                target = next;
            }
            maximum = max;
            lastTime = now;
        }

        public float fill() { return (float) fill; }
        public float trail() { return (float) trail; }
        public float absorption() { return (float) absorption; }
    }
}
