#version 330

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 size = 1.0 / max(fwidth(texCoord0), vec2(0.00001));
    float radius = max(1.0, vertexColor.b * min(size.x, size.y));
    vec2 q = abs((texCoord0 - 0.5) * size) - size * 0.5 + radius;
    vec2 outer = max(q, 0.0);
    float d = pow(pow(outer.x, 3.0) + pow(outer.y, 3.0), 1.0 / 3.0)
            + min(max(q.x, q.y), 0.0) - radius;
    float coverage = 1.0 - smoothstep(-1.0, 0.6, d);
    if (coverage < 0.002) discard;
    float rim = exp(-abs(d + 1.0) * 0.65);
    float topLight = 0.45 + 0.55 * (1.0 - texCoord0.y);
    vec2 light = texCoord0 - vertexColor.rg;
    float specular = exp(-dot(light * vec2(1.0, 1.8), light * vec2(1.0, 1.8)) * 9.0);
    float sheen = pow(max(0.0, 1.0 - texCoord0.y), 4.0);
    float alpha = vertexColor.a * coverage * (rim * topLight * 0.68 + specular * 0.055 + sheen * 0.045);
    fragColor = vec4(mix(vec3(0.85, 0.82, 1.0), vec3(1.0), topLight), alpha);
}
