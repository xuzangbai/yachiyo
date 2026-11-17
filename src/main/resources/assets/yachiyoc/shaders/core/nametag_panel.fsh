#version 330
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 size = 1.0 / max(fwidth(texCoord0), vec2(0.00001));
    float radius = min(12.0, min(size.x, size.y) * 0.45);
    vec2 q = abs((texCoord0 - 0.5) * size) - size * 0.5 + radius;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage < 0.002) discard;
    float rim = exp(-abs(d + 0.8) * 1.5) * 0.14;
    float sheen = pow(1.0 - texCoord0.y, 3.0) * 0.035;
    fragColor = vec4(mix(vertexColor.rgb, vec3(1.0), rim + sheen), vertexColor.a * coverage);
}
