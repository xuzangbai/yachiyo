#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sampleAlpha(vec2 uv) {
    return texture(Sampler0, uv).a;
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);

    // Packed controls from vertex color: phase, glow, outline.
    float phase = vertexColor.r * 6.28318530718;
    float glowStrength = vertexColor.g * 2.0;
    float outlineWidth = vertexColor.b * 4.0;

    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    float radius = max(outlineWidth, 0.0);

    float neighbourAlpha = 0.0;
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(px.x, 0.0) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(-px.x, 0.0) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(0.0, px.y) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(0.0, -px.y) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(px.x, px.y) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(-px.x, px.y) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(px.x, -px.y) * radius));
    neighbourAlpha = max(neighbourAlpha, sampleAlpha(texCoord0 + vec2(-px.x, -px.y) * radius));

    float edge = max(neighbourAlpha - texel.a, 0.0);
    float glow = edge * clamp(glowStrength, 0.0, 2.0);

    // Icy blue/white gradient with an animated horizontal highlight.
    float sweep = 0.5 + 0.5 * sin(texCoord0.x * 8.0 - phase);
    vec3 topBlue = vec3(0.72, 0.91, 1.00);
    vec3 iceWhite = vec3(0.96, 0.99, 1.00);
    vec3 deepBlue = vec3(0.20, 0.62, 1.00);
    vec3 gradient = mix(topBlue, iceWhite, clamp(texCoord0.y * 0.75 + 0.2, 0.0, 1.0));
    gradient = mix(gradient, vec3(1.0), smoothstep(0.92, 1.0, sweep) * 0.18);

    vec3 logoRgb = texel.rgb * gradient;
    vec3 glowRgb = deepBlue * glow * 0.85;
    float outAlpha = max(texel.a, glow * 0.72);

    // Premultiplied output; the pipeline uses TRANSLUCENT_PREMULTIPLIED_ALPHA.
    fragColor = vec4(logoRgb * texel.a + glowRgb, outAlpha);
}
