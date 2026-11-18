#version 330

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

const float TAU = 6.2831853;

float hash(vec2 cell) {
    vec3 p = fract(cell.xyx * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float dust(vec2 p, float phase) {
    p *= 21.0;
    float aa = max(length(fwidth(p)), 0.004);
    vec2 cell = floor(p);
    float seed = hash(cell);
    vec2 center = 0.24 + 0.52 * vec2(seed, hash(cell + 1.71));
    float d = length(fract(p) - center);
    float light = 1.0 - smoothstep(0.012, 0.012 + aa, d);
    return light * smoothstep(0.52, 1.0, seed) * (0.65 + 0.35 * cos(phase * 8.0 + seed * TAU));
}

float band(float distance, float falloff) {
    return exp(-falloff * distance * distance);
}

void main() {
    vec3 controls = round(vertexColor.rgb * 255.0);
    float phase = (controls.r * 256.0 + controls.g) * TAU / 65536.0;
    vec2 uv = texCoord0;
    float aspect = max(abs(dFdy(uv.y)), 0.000001) / max(abs(dFdx(uv.x)), 0.000001);
    vec2 p = (uv - vec2(controls.b / 255.0, 0.43)) * vec2(clamp(aspect, 0.55, 3.4), 1.0);

    float tilt = sin(p.x * 1.7 + phase * 3.0) * 0.12;
    float flow = p.y + p.x * 0.30 + tilt + cos(p.x * 3.1 - phase * 2.0) * 0.075;
    float satin = flow + sin(p.x * 5.5 + p.y * 3.0 - phase * 5.0) * 0.034;
    float violet = band(satin - 0.34, 17.0);
    float pink = band(satin - 0.47, 68.0);
    float upper = band(satin + 0.47, 26.0);
    vec3 color = mix(vec3(0.075, 0.050, 0.146), vec3(0.128, 0.080, 0.224), 1.0 - uv.y);
    color += violet * vec3(0.22, 0.13, 0.37);
    color += pink * vec3(0.22, 0.092, 0.19) * (0.54 + 0.46 * cos(p.x * 2.0 - phase * 2.0));
    color += upper * vec3(0.09, 0.058, 0.18);

    // Thin streams follow the same curvature as the broad bands of light.
    float strands = 0.0;
    for (int i = 0; i < 7; i++) {
        float offset = float(i);
        float path = satin - 0.17 - offset * 0.046;
        path += sin(p.x * 3.6 + offset * 0.52 + phase * 5.0) * 0.025;
        float coverage = 1.0 - smoothstep(0.0009, 0.0015 + fwidth(path), abs(path));
        float filament = 0.52 + 0.48 * cos(p.x * 4.0 - offset * 1.01 - phase * 4.0);
        strands += coverage * filament;
    }
    color += strands * vec3(0.21, 0.16, 0.31);

    // A soft halo keeps the transparent title legible across the flow.
    float halo = exp(-dot(p * vec2(0.97, 1.9), p * vec2(0.97, 1.9)) * 3.7);
    color += halo * vec3(0.135, 0.094, 0.22);
    vec2 drift = vec2(cos(phase * 2.0), sin(phase * 2.0)) * 0.035;
    color += dust(p + drift, phase) * vec3(0.32, 0.25, 0.42);
    float vignette = smoothstep(0.31, 0.73, length((uv - 0.5) * vec2(1.0, 1.23)));
    color *= 1.0 - 0.40 * vignette;
    color += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(color, 1.0) * texture(Sampler0, vec2(0.5));
}
