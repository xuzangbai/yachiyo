#version 330
#moj_import <yachiyoc:menu_scene.glsl>

// Difference between the nearest two moving wave centers locates the light folds.
float causticGap(vec2 p, float t) {
    vec2 id = floor(p), f = fract(p);
    float nearest = 8.0, next = 8.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 offset = vec2(float(x), float(y));
            vec2 seed = vec2(hash2(id + offset), hash2(id + offset + 17.0));
            vec2 pos = offset + 0.5 + 0.32 * sin(TAU * seed + vec2(t, -t));
            vec2 delta = pos - f;
            float dist = dot(delta, delta);
            next = min(next, max(nearest, dist));
            nearest = min(nearest, dist);
        }
    }
    return sqrt(next) - sqrt(nearest);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = phase();
    vec2 drift = vec2(cos(t), sin(t));
    vec2 floorPos = vec2(p.x, p.y * 1.16) * 4.1 / (1.22 - 0.43 * p.y);
    vec2 warp = vec2(sin(floorPos.y * 2.6 + 1.5 * sin(floorPos.x + t)),
                     cos(floorPos.x * 2.0 + 1.4 * cos(floorPos.y - t)));
    vec2 water = floorPos + 0.24 * warp + 0.11 * sin(floorPos.yx * 4.4 + drift);
    float gap = causticGap(water + 0.35 * drift, t);
    float echo = causticGap(water * 1.13 + 0.46 * warp + 3.6, -t);
    float focus = menuNoise(floorPos * 0.76 + 0.39 * drift);
    float width = 0.028 + 0.042 * focus;
    float light = exp(-gap * gap / (width * width));
    float scattered = exp(-gap * 6.5);
    float patches = menuNoise(water * 1.6) * 0.6 + menuNoise(water * 5.0) * 0.4;
    float sun = exp(-dot((uv - vec2(0.73, 0.86)) * vec2(1.1, 0.9), uv - vec2(0.73, 0.86)) * 3.7);
    float shafts = menuNoise(vec2((p.x - 0.46 * p.y) * 6.0 + 0.28 * cos(t), 2.9 + 0.2 * sin(t)));
    vec3 color = mix(vec3(0.012, 0.11, 0.19), vec3(0.032, 0.38, 0.45), sun * (0.41 + 0.59 * shafts));
    color += vec3(0.019, 0.075, 0.088) * patches;
    float power = (0.32 + 0.68 * sun) * (0.30 + 1.15 * focus);
    color += power * (vec3(0.30, 0.93, 0.99) * light + vec3(0.02, 0.31, 0.33) * scattered);
    color += vec3(0.07, 0.30, 0.42) * exp(-echo * 16.0) * (0.22 + focus) * (1.0 + light);
    fragColor = finishColor(color, uv, fragCoord);
}
