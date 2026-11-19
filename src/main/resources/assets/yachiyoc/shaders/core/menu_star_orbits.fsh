#version 330
#moj_import <yachiyoc:menu_scene.glsl>

vec3 orbit(vec2 p, vec2 radii, float tilt, float head, vec3 tint) {
    float c = cos(tilt), s = sin(tilt);
    vec2 local = vec2(dot(p, vec2(c, s)), dot(p, vec2(-s, c)));
    vec2 ellipse = local / radii;
    float radius = max(length(ellipse), 0.001);
    vec2 normal = ellipse / radius;
    float dist = abs(radius - 1.0) / max(length(normal / radii), 0.01);
    float angle = atan(ellipse.y, ellipse.x + 0.000001);
    float behind = mod(head - angle, TAU);
    float tail = exp(-1.55 * behind) * smoothstep(0.0, 0.042, behind);
    float front = 0.5 + 0.5 * normal.y;
    float aa = max(0.8 / iResolution.y, 0.00085);
    float core = exp(-dist * dist / (aa * aa * (1.0 + 3.0 * tail)));
    float halo = exp(-dist * (88.0 - 26.0 * front));
    vec3 color = tint * ((0.048 + 0.081 * front + 1.95 * tail) * core + (0.018 + 0.54 * tail) * halo);
    vec2 headPos = radii * vec2(cos(head), sin(head));
    vec2 d = local - headPos;
    float r = dot(d, d);
    color += exp(-r / 0.00031) * tint * 0.64;
    color += exp(-r / 0.000014) * vec3(1.9, 2.0, 2.3);
    return color;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 p = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = phase();
    float dust = menuNoise(p * 3.1 + 1.3) * 0.6 + menuNoise(p * 9.0) * 0.4;
    float cloud = exp(-3.7 * abs(p.y - 0.28 * p.x - 0.30 + 0.15 * dust));
    vec3 color = vec3(0.018, 0.025, 0.062) + vec3(0.10, 0.072, 0.21) * cloud * dust;
    color += vec3(0.73, 0.81, 1.02) * (0.80 * stars(p, 29.0, t) + 0.39 * stars(p + 4.0, 68.0, -t));
    color += orbit(p - vec2(-0.025, 0.015), vec2(0.76, 0.245), 0.36, t - 0.32, vec3(0.52, 0.81, 1.28));
    color += orbit(p - vec2(0.065, -0.04), vec2(0.91, 0.33), -0.19, 2.0 * t + 2.85, vec3(0.74, 0.51, 1.20));
    color += orbit(p + vec2(0.04, 0.015), vec2(0.65, 0.37), 0.63, t + 4.12, vec3(0.61, 0.76, 1.09));
    color += orbit(p + vec2(0.02, 0.02), vec2(1.03, 0.43), 0.14, t + 1.91, vec3(0.41, 0.48, 0.94));
    fragColor = finishColor(color, uv, fragCoord);
}
