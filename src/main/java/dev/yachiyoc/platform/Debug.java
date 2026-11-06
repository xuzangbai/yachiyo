package dev.yachiyoc.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 调试输出开关（按功能开，默认全关）。
 *
 * <p>用法：
 * <pre>
 * --debug                 列出当前开着的调试功能
 * --debug aura true       打开 aura 的调试输出
 * --debug aura false      关掉
 * --debug aura            省略值就是取反
 * --debug all true        全开
 * </pre>
 *
 * <p>新功能要打调试信息，用 {@link #log(String, String, Object...)}（第一个参数是功能名），
 * 例如 {@code Debug.log("aura", "wait — progress={}", p)}。功能名会在第一次调用时自动登记，
 * {@code --debug} 的补全里就会出现它；不要再直接 {@code LOGGER.info} —— 那样关不掉。
 */
public final class Debug {
    /** 全开用的特殊名字。 */
    public static final String ALL = "all";

    private static final Logger LOGGER = LoggerFactory.getLogger("yachiyoc");
    /** 当前开着的功能。 */
    private static final Set<String> ENABLED = new LinkedHashSet<>();
    /** 见过 / 登记过的功能名，给补全和提示用。 */
    private static final Set<String> KNOWN = new LinkedHashSet<>(List.of("aura", "ghosthand"));
    /** 限流用的时间戳。 */
    private static final Map<String, Long> LAST = new HashMap<>();

    private Debug() {
    }

    public static boolean enabled(String feature) {
        String key = normalise(feature);
        if (key == null) {
            return false;
        }
        return ENABLED.contains(key) || ENABLED.contains(ALL);
    }

    public static void set(String feature, boolean on) {
        String key = normalise(feature);
        if (key == null) {
            return;
        }
        KNOWN.add(key);
        if (ALL.equals(key)) {
            // all = 全开 / 全关：直接摊平成具体功能名，这样关个别功能也不会被通配符挡住
            ENABLED.clear();
            LAST.clear();
            if (on) {
                for (String name : KNOWN) {
                    if (!ALL.equals(name)) {
                        ENABLED.add(name);
                    }
                }
            }
            return;
        }
        if (on) {
            ENABLED.add(key);
        } else {
            ENABLED.remove(key);
            LAST.keySet().removeIf(k -> k.startsWith(key + ":"));
        }
    }

    /** 从配置恢复（{@code --config reload} 也会重新走一遍）。 */
    public static void setAll(Collection<String> features) {
        ENABLED.clear();
        LAST.clear();
        if (features == null) {
            return;
        }
        for (String feature : features) {
            set(feature, true);
        }
    }

    /** 当前开着的功能（按开启顺序）。 */
    public static Set<String> enabledFeatures() {
        return new LinkedHashSet<>(ENABLED);
    }

    /** 登记过的功能名 + {@code all}，给指令补全用。 */
    public static Set<String> knownFeatures() {
        Set<String> names = new LinkedHashSet<>(KNOWN);
        names.add(ALL);
        return names;
    }

    /** 一条调试日志。 */
    public static void log(String feature, String format, Object... args) {
        String key = normalise(feature);
        if (key == null) {
            return;
        }
        KNOWN.add(key);
        if (!enabled(key)) {
            return;
        }
        LOGGER.info("[debug/{}] {}", key, String.format(format, args));
    }

    /**
     * 限流版：同一功能 + 同一条文案在 {@code intervalMs} 内最多写一条。
     * 给“每 tick 都可能触发”的地方用。
     */
    public static void logThrottled(String feature, long intervalMs, String format, Object... args) {
        String key = normalise(feature);
        if (key == null) {
            return;
        }
        KNOWN.add(key);
        if (!enabled(key)) {
            return;
        }
        String throttleKey = key + ":" + format;
        long now = System.currentTimeMillis();
        Long last = LAST.get(throttleKey);
        if (last != null && now - last < intervalMs) {
            return;
        }
        LAST.put(throttleKey, now);
        LOGGER.info("[debug/{}] {}", key, String.format(format, args));
    }

    private static String normalise(String feature) {
        if (feature == null) {
            return null;
        }
        String key = feature.trim().toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : key;
    }
}
