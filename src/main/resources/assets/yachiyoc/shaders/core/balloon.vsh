#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
out vec3 viewPosition;
out vec3 viewNormal;
out vec4 material;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    viewPosition = pos.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    material = Color;
}
