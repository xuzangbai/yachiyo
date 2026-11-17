#version 330
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec3 rgb = clamp(abs(fract(texCoord0.x + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    fragColor = vec4(rgb, vertexColor.a);
}
