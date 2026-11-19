#version 330
#moj_import <yachiyoc:menu_scene.glsl>

vec3 veil(vec2 p, float t, float layer, float base) {
    // Height-dependent shear tilts the vertical filaments as the curtain folds.
    float x = p.x + (p.y - base) * (0.14 + 0.09 * sin(3.0 * p.x + t));
    float fold = 3.8 * x + layer + 0.40 * sin(t + layer);
    float bend = fold + 1.2 * sin(fold * 0.84 - 0.55 * cos(t));
    float hem = base + 0.12 * sin(bend) + 0.035 * sin(2.8 * bend + layer)
              + 0.014 * sin(14.0 * x + sin(t + layer));
    float h = p.y - hem;
    float lift = 0.11 + 0.18 * menuNoise(vec2(x * 4.1 + 0.5 * sin(t), layer));
    float glow = exp(-max(h, 0.0) / lift) * smoothstep(-0.026, 0.024, h);
    float field = x * 48.0 + 3.0 * sin(x * 7.0 + 0.7 * cos(t)) + 1.5 * cos(t + layer);
    float rays = menuNoise(vec2(field, layer)) * 0.58
               + menuNoise(vec2(field * 3.3, layer + 11.0)) * 0.42;
    float pleats = 0.42 + 0.67 * pow(0.5 + 0.5 * cos(bend * 2.1), 2.0);
    float intensity = glow * pleats * (0.35 + 1.8 * rays * rays);
    vec3 mint = mix(vec3(0.06, 1.25, 0.66), vec3(0.12, 0.91, 0.91),
                    0.5 + 0.5 * sin(fold * 0.74));
    vec3 tint = mix(mint, vec3(0.54, 0.24, 0.90), smoothstep(0.045, 0.27, h));
    return intensity * tint + 0.16 * mint * pleats * exp(-abs(h) * 55.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = vec2((uv.x - 0.5) * iResolution.x / iResolution.y, uv.y);
    float t = phase();
    vec3 sky = mix(vec3(0.072, 0.14, 0.18), vec3(0.018, 0.031, 0.095), uv.y);
    sky += vec3(0.035, 0.028, 0.095) * menuNoise(p * 4.5);
    sky += vec3(0.66, 0.82, 0.97) * (stars(p, 36.0, t) + 0.32 * stars(p + 3.2, 74.0, -t));
    sky += 0.48 * veil(p, t, 4.0, 0.56);
    sky += 1.05 * veil(p, t, 0.2, 0.37);
    float ridge = 0.07 + 0.042 * sin(p.x * 5.0) + 0.047 * menuNoise(vec2(p.x * 14.0, 11.0))
                + 0.012 * menuNoise(vec2(p.x * 52.0, 6.0));
    sky = mix(vec3(0.025, 0.051, 0.076), sky, smoothstep(ridge, ridge + 0.003, p.y));
    float nearRidge = 0.024 + 0.023 * sin(7.0 * p.x + 2.0) + 0.019 * menuNoise(vec2(32.0 * p.x, 2.0));
    sky = mix(vec3(0.012, 0.026, 0.049), sky, smoothstep(nearRidge, nearRidge + 0.002, p.y));
    fragColor = finishColor(sky, uv, fragCoord);
}
