package dev.yachiyoc.ui.widget;

/**
 * Eased open/close progress shared by expandable panels. The panel owns its
 * layout; this only animates a 0..1 factor so the same helper can be reused for
 * HUD config, module settings sections and future collapsible widgets.
 */
public final class ExpandAnimation {
    private float progress;
    private float target;

    public void setOpen(boolean open) {
        target = open ? 1.0F : 0.0F;
    }

    public void toggle() {
        target = target > 0.5F ? 0.0F : 1.0F;
    }

    public void update(float deltaSeconds) {
        float delta = Math.min(Math.max(deltaSeconds, 0.0F), 0.1F);
        float factor = 1.0F - (float) Math.exp(-12.0F * delta);
        progress += (target - progress) * factor;
        if (Math.abs(target - progress) < 0.001F) {
            progress = target;
        }
    }

    public float progress() {
        return progress;
    }

    public float apply(float fullHeight) {
        return fullHeight * Math.max(0.0F, Math.min(1.0F, progress));
    }

    public boolean visible() {
        return progress > 0.001F;
    }

    public boolean open() {
        return target > 0.5F;
    }
}
