#version 330
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    ivec3 controls = ivec3(round(vertexColor.rgb * 255.0));
    ivec2 atlas = textureSize(Sampler0, 0);
    ivec2 size = ivec2(atlas.x * 4 - (controls.b & 3), atlas.y * 2 / 3);
    vec2 uv = texCoord0;
    int turns = controls.b >> 2;
    if (turns == 1) uv = vec2(uv.y, 1.0 - uv.x);
    else if (turns == 2) uv = 1.0 - uv;
    else if (turns == 3) uv = vec2(1.0 - uv.y, uv.x);
    ivec2 p = clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1);
    vec4 luma = texelFetch(Sampler0, ivec2(p.x / 4, p.y), 0);
    vec4 chroma = texelFetch(Sampler0, ivec2(p.x / 4, size.y + p.y / 2), 0);
    float y = luma[p.x & 3];
    vec2 uvSample = (p.x & 2) == 0 ? chroma.rg : chroma.ba;
    uvSample -= vec2(128.0 / 255.0);
    if (controls.g == 0) {
        y = (y - 16.0 / 255.0) * (255.0 / 219.0);
        uvSample *= 255.0 / 224.0;
    }
    vec3 rgb = vec3(y) + (controls.r == 1
        ? vec3(1.402 * uvSample.y, -0.344136 * uvSample.x - 0.714136 * uvSample.y, 1.772 * uvSample.x)
        : controls.r == 2
        ? vec3(1.4746 * uvSample.y, -0.164553 * uvSample.x - 0.571353 * uvSample.y, 1.8814 * uvSample.x)
        : vec3(1.5748 * uvSample.y, -0.187324 * uvSample.x - 0.468124 * uvSample.y, 1.8556 * uvSample.x));
    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
