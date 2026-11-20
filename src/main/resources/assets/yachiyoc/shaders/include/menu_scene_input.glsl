uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float iTime;
vec3 iResolution;
void mainImage(out vec4 fragColor, in vec2 fragCoord);

void main() {
    ivec3 bytes = ivec3(round(vertexColor.rgb * 255.0));
    int millis = (bytes.r << 16) | (bytes.g << 8) | bytes.b;
    iTime = float(millis % MENU_LOOP_MS) * 0.001;
    vec2 pixel = vec2(abs(dFdx(texCoord0.x)), abs(dFdy(texCoord0.y)));
    iResolution = vec3(round(1.0 / max(pixel, vec2(0.000001))), 1.0);
    vec2 coord = vec2(texCoord0.x, 1.0 - texCoord0.y) * iResolution.xy;
    mainImage(fragColor, coord);
    fragColor *= texture(Sampler0, vec2(0.5));
}
