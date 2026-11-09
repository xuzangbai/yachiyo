package dev.yachiyoc.platform;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 药水粒子抓取：服务端可能既不给效果包、又清掉元数据，但**粒子包一般还在**。
 * 这里只记 {@code ENTITY_EFFECT} 粒子的位置与颜色，供 {@code NameTags} 兜底反推。
 */
public final class EffectParticles {
    private static final long WINDOW_MS = 1200L;
    private static final int MAX_ENTRIES = 512;

    private record Entry(double x, double y, double z, int rgb, long time) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private EffectParticles() {
    }

    public static void record(double x, double y, double z, int rgb) {
        long now = System.currentTimeMillis();
        if (ENTRIES.size() >= MAX_ENTRIES) {
            prune(now);
        }
        ENTRIES.add(new Entry(x, y, z, rgb & 0xFFFFFF, now));
    }

    /** 某个位置附近、最近窗口内出现过的粒子颜色（去重，最多 4 个）。 */
    public static List<Integer> colorsNear(Vec3d pos, double radius) {
        long now = System.currentTimeMillis();
        double radiusSq = radius * radius;
        List<Integer> colors = new ArrayList<>(4);
        for (Iterator<Entry> it = ENTRIES.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (now - entry.time() > WINDOW_MS) {
                continue;
            }
            double dx = entry.x() - pos.x;
            double dy = entry.y() - (pos.y + 1.0D);
            double dz = entry.z() - pos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            if (!colors.contains(entry.rgb())) {
                colors.add(entry.rgb());
            }
        }
        return colors;
    }

    private static void prune(long now) {
        ENTRIES.removeIf(entry -> now - entry.time() > WINDOW_MS);
        if (ENTRIES.size() >= MAX_ENTRIES) {
            ENTRIES.subList(0, Math.min(64, ENTRIES.size())).clear();
        }
    }
}
