#version 330
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 uv = clamp(texCoord0, 0.0, 1.0);
    fragColor = vec4(mix(vec3(1.0), vertexColor.rgb, uv.x) * (1.0 - uv.y), vertexColor.a);
}
