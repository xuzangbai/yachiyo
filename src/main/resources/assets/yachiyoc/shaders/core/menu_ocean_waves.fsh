#version 330
#moj_import <yachiyoc:menu_scene.glsl>

// Low-frequency swells displace the silhouette. Filtered ripples shape reflections.
vec3 swell(vec2 p, float t) {
    vec4 kx = vec4(0.61, -0.41, 1.19, 0.73);
    vec4 kz = vec4(0.34, 0.87, 0.69, -1.71);
    vec4 a = vec4(0.22, 0.13, 0.078, 0.048);
    vec4 s = p.x * kx + p.y * kz + vec4(-6, -9, -11, 13) * t + vec4(0.1, 2.2, 1.4, 4.0);
    vec4 slope = a * cos(s);
    return vec3(dot(a, sin(s)), dot(slope, kx), dot(slope, kz));
}

vec3 skyLight(vec3 ray, vec3 sun) {
    float up = max(ray.y, 0.0);
    float warm = exp(-3.3 * length(ray.xz - sun.xz));
    vec3 horizon = mix(vec3(0.47, 0.57, 0.68), vec3(1.14, 0.65, 0.44), warm);
    vec3 sky = mix(horizon, vec3(0.10, 0.25, 0.43), 1.0 - exp(-up * 3.8));
    float angle = length(ray - sun);
    return sky + vec3(0.75, 0.43, 0.24) * exp(-angle * 10.0)
               + vec3(2.7, 2.15, 1.45) * (1.0 - smoothstep(0.024, 0.030, angle));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = phase();
    vec3 ray = normalize(vec3(p.x, p.y - 0.154, 0.91));
    vec3 sun = normalize(vec3(0.41, 0.12, 0.91));
    vec3 color = skyLight(ray, sun);
    if (ray.y < -0.001) {
        float eye = 1.44 + 0.048 * sin(2.0 * t);
        float down = -ray.y;
        // The four wave amplitudes sum to less than 0.48 world units.
        float lo = (eye - 0.48) / down;
        float hi = (eye + 0.48) / down;
        float distanceToPlane = eye / down;
        float detail = 1.0 - smoothstep(13.0, 48.0, distanceToPlane);
        for (int i = 0; i < 7; ++i) {
            float mid = (lo + hi) * 0.5;
            float surface = swell(ray.xz * mid, t).x * detail;
            if (eye - mid * down > surface) lo = mid;
            else hi = mid;
        }
        vec2 sea = ray.xz * (0.5 * (lo + hi));
        vec3 wave = swell(sea, t);
        vec2 ripplePos = sea + 0.48 * vec2(sin(0.72 * sea.y + 4.0 * t), cos(0.58 * sea.x - 3.0 * t));
        vec4 kx = vec4(2.7, -3.6, 6.1, 9.8), kz = vec4(3.9, 1.6, -4.5, 6.8);
        vec4 ripple = cos(ripplePos.x * kx + ripplePos.y * kz + vec4(-17, 19, 26, -31) * t);
        vec4 footprint = distanceToPlane / (iResolution.y * max(down, 0.001)) * sqrt(kx * kx + kz * kz);
        vec4 slope = ripple * vec4(0.025, 0.016, 0.009, 0.004)
                   * (1.0 - smoothstep(vec4(0.5), vec4(2.7), footprint));
        vec2 gradient = wave.yz * (0.34 + 0.66 * detail) + vec2(dot(kx, slope), dot(kz, slope));
        vec3 normal = normalize(vec3(-gradient.x, 1.0, -gradient.y));
        vec3 reflected = reflect(ray, normal);
        float fresnel = 0.06 + 0.83 * pow(1.0 - max(0.0, dot(-ray, normal)), 3.1);
        float crest = smoothstep(-0.27, 0.36, wave.x) * (0.45 + 0.55 * max(dot(normal, sun), 0.0));
        vec3 deep = mix(vec3(0.016, 0.115, 0.17), vec3(0.044, 0.36, 0.39), crest);
        color = mix(deep, skyLight(reflected, sun), fresnel);
        float gleam = length(reflected - sun);
        color += vec3(1.65, 1.05, 0.56) * exp(-gleam * gleam / 0.004);
        color = mix(color, skyLight(normalize(vec3(ray.x, 0.0, ray.z)), sun) * 0.76,
                    smoothstep(35.0, 210.0, distanceToPlane));
    }
    fragColor = finishColor(color, uv, fragCoord);
}
