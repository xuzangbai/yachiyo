#version 330
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vec4(vertexColor.rgb, clamp(texCoord0.x, 0.0, 1.0) * vertexColor.a);
}
