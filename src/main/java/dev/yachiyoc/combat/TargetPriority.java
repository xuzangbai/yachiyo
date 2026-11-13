package dev.yachiyoc.combat;

/**
 * 多目标时怎么排优先级。
 */
public enum TargetPriority {
    /** 只按距离：谁的眼→碰撞箱最近点更近就打谁。 */
    NEAREST,
    /**
     * 只看准心（默认）：打<b>离准心最近</b>的那个目标，不看距离。
     *
     * <p>夹角打平（都在准心里/几乎一样偏）时才按距离决胜负，图个确定性。
     * 候选池本身已经按 {@code 范围} 和实体类型过滤过了，所以这里不会挑到打不着的。
     */
    CROSSHAIR
}
