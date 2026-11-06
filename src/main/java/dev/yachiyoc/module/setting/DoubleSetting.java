package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public final class DoubleSetting extends Setting<Double> {
    private static DoubleSetting editing;

    private final double min;
    private double max;
    private final double step;
    private String buffer = "";

    public DoubleSetting(String owner, String id, double defaultValue, double min, double max, double step) {
        this(owner, id, defaultValue, min, max, step, false);
    }

    /**
     * @param defaultAtMax when true the default value follows the current max;
     *                     used by the drop range so it starts at the full loaded range.
     */
    public DoubleSetting(String owner, String id, double defaultValue, double min, double max,
                         double step, boolean defaultAtMax) {
        super(owner, id, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.01 : step;
        this.value = defaultAtMax ? max : normalize(defaultValue);
    }

    public void setMax(double newMax) {
        double clampedMax = Math.max(min, newMax);
        boolean wasAtMax = Math.abs(value - max) < 1.0E-6;
        max = clampedMax;
        value = wasAtMax ? max : normalize(value);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public double ratio() {
        return (get() - min) / (max - min);
    }

    public void setFromRatio(double ratio) {
        set(min + MathHelper.clamp(ratio, 0.0, 1.0) * (max - min));
    }

    // --- 数值输入框（点一下直接输入想要的数值） ---

    public boolean editing() {
        return editing == this;
    }

    /** 开始编辑：缓冲区先填当前值，回车提交、ESC 取消。 */
    public void startEditing() {
        if (editing != null && editing != this) {
            editing.commit();
        }
        editing = this;
        buffer = plainValue();
    }

    /** 回车 / 失焦：解析缓冲区并写入。 */
    public void commit() {
        parseBuffer();
        if (editing == this) {
            editing = null;
        }
    }

    /** ESC：丢弃缓冲区，不改变原值。 */
    public void cancel() {
        if (editing == this) {
            editing = null;
        }
        buffer = "";
    }

    public static DoubleSetting active() {
        return editing;
    }

    public static void stopAllEditing() {
        if (editing != null) {
            editing.commit();
        }
    }

    public String buffer() {
        return buffer;
    }

    public void typeChar(char c) {
        if (buffer.length() >= 12) {
            return;
        }
        // 只收数字、小数点和负号（逗号当小数点处理，方便小键盘）
        if (c == ',') {
            c = '.';
        }
        if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
            buffer += c;
        }
    }

    public void backspace() {
        if (!buffer.isEmpty()) {
            buffer = buffer.substring(0, buffer.length() - 1);
        }
    }

    private void parseBuffer() {
        String text = buffer.trim();
        if (text.isEmpty() || text.equals("-") || text.equals(".")) {
            return;
        }
        try {
            set(Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
            // 输入不合法就保持原值
        }
    }

    /** 当前值的纯数字文本（不带单位），用于填充输入框。 */
    private String plainValue() {
        if (step >= 1.0) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public void set(Double newValue) {
        super.set(normalize(newValue));
    }

    private double normalize(double raw) {
        double clamped = MathHelper.clamp(raw, min, max);
        double snapped = Math.round(clamped / step) * step;
        return MathHelper.clamp(snapped, min, max);
    }

    @Override
    public String displayValue() {
        if (step >= 1.0) {
            return String.format(Locale.ROOT, "%.0f", get());
        }
        return String.format(Locale.ROOT, "%.1f", get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            set(element.getAsDouble());
        }
    }
}
