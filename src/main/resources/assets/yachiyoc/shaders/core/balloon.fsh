#version 330

in vec3 viewPosition;
in vec3 viewNormal;
in vec4 material;
out vec4 fragColor;

float reflection(vec3 n, vec3 v, vec3 light, float sharpness) {
    return pow(max(0.0, dot(n, normalize(v + light))), sharpness);
}

float pane(vec3 ray, vec3 light) {
    vec3 horizontal = normalize(cross(vec3(0.0, 1.0, 0.0), light));
    vec3 vertical = cross(light, horizontal);
    float facing = dot(ray, light);
    vec2 p = vec2(dot(ray, horizontal), dot(ray, vertical)) / max(0.43, facing);
    float window = (1.0 - smoothstep(0.15, 0.23, abs(p.x))) * (1.0 - smoothstep(0.15, 0.31, abs(p.y)));
    float frame = mix(0.57, 1.0, smoothstep(0.006, 0.026, abs(p.y + 0.035)));
    return window * frame * smoothstep(0.76, 0.90, facing);
}

void main() {
    vec3 v = normalize(-viewPosition);
    vec3 n = normalize(viewNormal);
    if (dot(n, v) < 0.0) n = -n;
    vec3 key = normalize(vec3(-0.56, 0.71, 0.48));
    vec3 fill = normalize(vec3(0.62, 0.23, 0.42));
    vec3 ray = reflect(-v, n);
    float latex = smoothstep(0.25, 0.95, material.a);
    float diffuse = max(0.0, dot(n, key));
    float fresnel = pow(1.0 - max(0.0, dot(n, v)), 3.2);
    float transmission = max(0.0, dot(n, normalize(vec3(0.75, 0.55, -0.38))));
    vec3 hue = material.rgb * (0.39 + 0.67 * diffuse);
    hue += material.rgb * vec3(0.62, 0.27, 0.46) * transmission * latex;
    float window = pane(ray, key) * 0.69 + reflection(n, v, key, 13.0) * 0.17;
    float bounce = reflection(n, v, fill, 32.0) * 0.20;
    float brightness = clamp(length(material.rgb), 0.22, 1.0);
    hue += (window * vec3(0.95, 0.89, 1.0) + bounce * vec3(0.75, 0.61, 0.95))
            * brightness * mix(0.60, 1.0, latex);
    hue += vec3(0.20, 0.27, 0.42) * smoothstep(-0.16, 0.58, ray.y) * fresnel * latex;
    hue += vec3(0.37, 0.24, 0.50) * fresnel * brightness * mix(0.34, 0.73, latex);
    fragColor = vec4(hue, 1.0);
}
