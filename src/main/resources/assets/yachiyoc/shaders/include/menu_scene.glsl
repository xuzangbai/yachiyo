#define MENU_LOOP_MS 65536
#moj_import <yachiyoc:menu_scene_input.glsl>

const float TAU = 6.28318530718;
float phase() {
    return TAU * fract(iTime / 65.536);
}

float hash2(vec2 p) {
    vec3 q = fract(p.xyx * vec3(0.1031, 0.11369, 0.13787));
    q += dot(q, q.yzx + 19.19);
    return fract((q.x + q.y) * q.z);
}

float menuNoise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(cell), hash2(cell + vec2(1, 0)), f.x),
               mix(hash2(cell + vec2(0, 1)), hash2(cell + vec2(1)), f.x), f.y);
}

vec4 finishColor(vec3 color, vec2 uv, vec2 pixel) {
    vec2 menu = (uv - vec2(0.5, 0.47)) / vec2(0.23, 0.31);
    color *= 1.0 - 0.19 * exp(-dot(menu, menu));
    color *= 1.0 - 0.12 * smoothstep(0.23, 0.78, length(uv - 0.5));
    color = 1.0 - exp(-max(color, vec3(0.0)));
    color += (hash2(pixel) - 0.5) / 255.0;
    return vec4(clamp(color, 0.0, 1.0), 1.0);
}

float stars(vec2 p, float scale, float time) {
    vec2 cell = floor(p * scale);
    vec2 center = 0.15 + 0.7 * vec2(hash2(cell), hash2(cell + 13.0));
    vec2 delta = fract(p * scale) - center;
    float seed = hash2(cell + 5.7);
    float size = max(0.025 + 0.052 * seed, scale * 0.64 / iResolution.y);
    float core = exp(-dot(delta, delta) / (size * size));
    return step(0.77, seed) * core * (0.55 + 0.25 * sin(time + TAU * seed));
}
