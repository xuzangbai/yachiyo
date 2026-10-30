package dev.yachiyoc.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public final class DoubleSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public DoubleSetting(String owner, String id, double defaultValue, double min, double max, double step) {
        super(owner, id, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.01 : step;
        this.value = normalize(defaultValue);
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
