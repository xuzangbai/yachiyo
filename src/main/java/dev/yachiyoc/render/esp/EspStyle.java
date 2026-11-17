package dev.yachiyoc.render.esp;

/**
 * 一套目标渲染参数。
 *
 * <p>纯数据：由模块的设置拼出来，渲染层只读、不反查设置。
 * 这样任何模块都能自己拼一份（比如以后的水晶光环用不同的颜色/粗细）。
 *
 * @param sonarColor     声纳波颜色（ARGB，alpha 是最大亮度）
 * @param haloColor      光环颜色（ARGB，alpha 是最大亮度）
 * @param thickness      线条粗细，单位≈"4 格处的一个像素"（渲染时按距离等比放大）
 * @param sonarRadius    声纳波最大半径（格）
 * @param sonarPeriod    一次扫描的周期（秒），波前向外扩散，尾迹渐隐
 * @param sonarSpacing   表面扫描网格间距（格）
 * @param sonarSpokes    网格细节，沿用现有配置字段
 * @param haloAmplitude  光环上下行程，占目标身高一半的比例（0 = 只在中腰，1 = 到头顶/脚底）
 * @param haloTail       光环拖尾层数（0 = 无拖尾）
 */
public record EspStyle(int sonarColor,
                       int haloColor,
                       float thickness,
                       float sonarRadius,
                       float sonarPeriod,
                       float sonarSpacing,
                       int sonarSpokes,
                       float haloAmplitude,
                       int haloTail) {
}
