#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in vec4 Color;
out vec3 scanPosition;
out vec4 scanParameters;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    scanPosition = vec3(UV0, float(UV1.x) / 2048.0);
    scanParameters = vec4(float(UV1.y) / 4096.0, float(UV2.y) / 16384.0,
            float(UV2.x) / 4096.0, round(Normal.x * 32.0));
    vertexColor = Color;
}
