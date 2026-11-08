#version 330

// 连续曲率圆角的一个角：uv 覆盖整个 r×r 角方块，圆弧圆心在 quad 内侧那个角上。
// UV 的镜像（负的 uWidth / vHeight）由 Java 侧处理，所以这里统一按「圆心在 (1,1)」算。
// 抗锯齿用屏幕空间导数 fwidth：无论 guiScale 多少、半径多大，过渡永远只占 1 个屏幕像素。

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// 必须和 Render2D.SQUIRCLE_POWER 保持一致：2.0 = 正圆，3.0 = 现在这样，4~5 = 越方
const float POWER = 3.0;

void main() {
    vec2 c = vec2(1.0) - texCoord0;
    float f = pow(abs(c.x), POWER) + pow(abs(c.y), POWER) - 1.0;
    float aa = max(fwidth(f), 1e-4);
    float coverage = 1.0 - smoothstep(-aa, aa, f);
    if (coverage <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
