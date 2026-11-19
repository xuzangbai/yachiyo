#version 330
#moj_import <yachiyoc:menu_scene.glsl>

// Each sheet has a broad brushed face, a dark underside and a narrow polished lip.
vec3 goldSheet(vec3 under, vec2 p, float t, float offset, float seed, float thickness) {
    float x = p.x, travel = 0.33 * sin(t + seed);
    float curl = 3.35 * x + seed + travel;
    float wave = offset + 0.10 * x + 0.122 * sin(curl + 0.64 * sin(2.5 * x - seed))
               + 0.044 * sin(6.4 * x - seed + 0.61 * cos(t));
    float height = thickness * (0.84 + 0.30 * sin(curl - 0.5 * cos(t)));
    float v = (wave - p.y) / height;
    float mask = smoothstep(-0.012, 0.012, v) * (1.0 - smoothstep(0.97, 1.05, v));
    float sheen = 0.5 + 0.5 * cos(curl - 1.6 * v + 0.8 * sin(t - seed));
    float brushed = menuNoise(vec2(x * 3.1 + 0.35 * cos(t), v * 65.0 + seed));
    float striation = pow(0.5 + 0.5 * sin(76.0 * v + 1.6 * sin(6.1 * x + t) + 4.2 * brushed), 5.0);
    vec3 metal = mix(vec3(0.16, 0.065, 0.028), vec3(1.15, 0.55, 0.15), sheen * sheen);
    metal *= (0.55 + 0.5 * brushed + 0.30 * striation) * exp(-max(v, 0.0) * 1.9);
    float lip = abs(wave - p.y);
    float spec = 0.30 + 1.50 * pow(sheen, 3.0);
    vec3 body = mix(under, metal, mask);
    body += vec3(1.5, 0.93, 0.42) * spec * exp(-lip / 0.0031);
    body += vec3(0.46, 0.18, 0.055) * spec * exp(-lip / 0.031);
    return body;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = phase();
    float amber = exp(-2.6 * length((p - vec2(-0.48, 0.24)) * vec2(0.9, 1.4)));
    vec3 color = vec3(0.068, 0.033, 0.037) + vec3(0.27, 0.105, 0.023) * amber;
    color += vec3(0.033, 0.021, 0.013) * menuNoise(p * 4.0);
    color = goldSheet(color, p, t,  0.42, 0.7, 0.21);
    color = goldSheet(color, p, t,  0.23, 1.8, 0.24);
    color = goldSheet(color, p, t,  0.08, 2.2, 0.22);
    color = goldSheet(color, p, t, -0.12, 3.4, 0.28);
    color = goldSheet(color, p, t, -0.27, 4.7, 0.32);
    fragColor = finishColor(color, uv, fragCoord);
}
