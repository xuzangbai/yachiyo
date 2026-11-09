package dev.yachiyoc.render;

/**
 * 画框时画哪部分。用自有枚举而不是 MC 的类型，方便以后换渲染实现。
 */
public enum ShapeMode {
    /** 只画 12 条棱。 */
    LINES,
    /** 只画 6 个半透明面。 */
    SIDES,
    /** 面和线都画。 */
    BOTH
}
