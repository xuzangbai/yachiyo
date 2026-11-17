#version 330
in vec3 scanPosition;
in vec4 scanParameters;
in vec4 vertexColor;
out vec4 fragColor;

float gridLine(float position, float pixels) {
    float distanceToLine = abs(fract(position - 0.5) - 0.5);
    float derivative = max(fwidth(position), 0.00001);
    float line = 1.0 - smoothstep(derivative * max(0.2, pixels - 0.5), derivative * (pixels + 0.5), distanceToLine);
    // Fade subpixel grids before they turn into moire or shimmer at shallow viewing angles.
    return line * (1.0 - smoothstep(0.18, 0.5, derivative));
}

float grid(vec2 uv, float spacing, float pixels) {
    vec2 position = uv / spacing;
    float square = max(gridLine(position.x, pixels), gridLine(position.y, pixels));
    float diagonal = gridLine(position.x - position.y, pixels * 0.8);
    return max(square, diagonal * 0.8);
}

void main() {
    float spacing = max(0.1, scanParameters.z);
    float detail = scanParameters.w;
    float bandWidth = max(0.015, scanParameters.y);
    float lag = (scanParameters.x - length(scanPosition)) / bandWidth;
    if (lag < -2.0 || lag > 7.0) discard;

    // One continuous sphere intersects every face. No per-face phase or circle sampling.
    float front = exp(-lag * lag * 2.4);
    float echo = lag > 0.0 ? exp(-lag * 0.85) : 0.0;
    vec3 normal = abs(normalize(cross(dFdx(scanPosition), dFdy(scanPosition))));
    vec2 uv = normal.x > 0.5 ? scanPosition.yz : normal.y > 0.5 ? scanPosition.xz : scanPosition.xy;
    float pixels = clamp(bandWidth / spacing * 10.0, 0.5, 6.0);
    float majorGrid = grid(uv, spacing, pixels * 0.55);
    float minorGrid = grid(uv, spacing / max(2.0, floor(detail / 4.0) + 1.0), pixels * 0.28) * 0.32;
    float wire = max(majorGrid, minorGrid);
    float strength = front * (0.12 + wire * 0.88) + echo * wire * 0.38;
    float alpha = vertexColor.a * min(1.0, strength);
    if (alpha < 0.003) discard;
    vec3 color = mix(vertexColor.rgb, vec3(1.0), front * 0.28);
    fragColor = vec4(color, alpha);
}
