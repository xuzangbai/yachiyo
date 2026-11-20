#version 330
#define MENU_LOOP_MS 262144
#moj_import <yachiyoc:menu_scene_input.glsl>

// Yachiyo menu shader. The host supplies iTime in seconds and iResolution.xy in pixels.
const float TAU = 6.28318530718;
const float SPACING = 15.0;
const float FAR = 70.0;

float hash2(vec2 p) {
    vec3 q = fract(p.xyx * vec3(.1031, .1030, .0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec2 flight(float z) {
    float a = z * TAU / (2.0 * SPACING);
    return vec2(1.24, .70) * sin(a) + vec2(.26, -.21) * cos(a);
}

mat3 tunnelFrame(float side, float roll) {
    vec3 forward = normalize(vec3(vec2(1.24, .70) * (side * TAU / (2.0 * SPACING)), 1.0));
    vec3 u = normalize(vec3(forward.z, 0, -forward.x));
    vec3 v = cross(forward, u);
    float c = cos(roll), s = sin(roll);
    return mat3(c*u + s*v, c*v - s*u, forward);
}

// Each empty ternary cell opens along the axis that was not cut.
// Skip the entire empty prism before testing the next occupied cell.
bool traceSponge(vec3 eye, vec3 ray, float limit, out float hit, out vec3 normal) {
    vec3 dir = mix(vec3(-1), vec3(1), greaterThanEqual(ray, vec3(0)));
    vec3 inv = dir / max(abs(ray), vec3(.000001));
    vec3 entry = (-3.0*dir - eye) * inv;
    vec3 leave = ( 3.0*dir - eye) * inv;
    float t = max(0.0, max(entry.x, max(entry.y, entry.z)));
    float end = min(limit, min(leave.x, min(leave.y, leave.z)));
    if (t >= end) return false;
    normal = -dir * step(entry.yzx, entry) * step(entry.zxy, entry);
    for (int hop = 0; hop < 56; ++hop) {
        vec3 p = eye + ray * (t + .00012);
        vec3 parent = vec3(-3);
        float size = 6.0;
        bool solid = true;
        for (int level = 0; level < 3; ++level) {
            float child = size / 3.0;
            vec3 digit = clamp(floor((p - parent) / child), 0.0, 2.0);
            vec3 middle = 1.0 - abs(digit - 1.0);
            if (dot(middle, vec3(1)) >= 2.0) {
                vec3 nearSide = parent + child * middle;
                vec3 extent = size - 2.0 * child * middle;
                vec3 wall = nearSide + step(vec3(0), ray) * extent;
                vec3 exitAt = (wall - eye) * inv;
                float next = min(exitAt.x, min(exitAt.y, exitAt.z));
                normal = -dir * step(exitAt, exitAt.yzx) * step(exitAt, exitAt.zxy);
                t = max(t + .00012, next);
                solid = false;
                break;
            }
            parent += digit * child;
            size = child;
        }
        if (solid) { hit = t; return true; }
        if (t + .00012 >= end) break;
    }
    return false;
}

float median3(vec3 v) { return max(min(v.x, v.y), min(max(v.x, v.y), v.z)); }

// Distance along the face to an actual cut, including the rims of all three scales.
float edgeDistance(vec3 p, vec3 n) {
    vec3 mask = abs(n);
    vec3 outer = 3.0 - abs(p) + 100.0 * mask;
    float edge = min(outer.x, min(outer.y, outer.z));
    float size = 6.0;
    for (int level = 0; level < 3; ++level) {
        vec3 band = abs(fract((p + 3.0) / size) - .5) * size - size / 6.0;
        band = mix(band, mix(vec3(100), vec3(-100), lessThan(band, vec3(0))), mask);
        edge = min(edge, median3(band));
        size /= 3.0;
    }
    return max(edge, 0.0);
}

float spongeField(vec3 p) {
    vec3 box = abs(p) - 3.0;
    float field = max(box.x,max(box.y,box.z));
    float size = 6.0;
    for (int i=0; i<3; ++i) {
        vec3 bands = abs(fract((p+3.0)/size)-.5)*size - size/6.0;
        field = max(field,-median3(bands));
        size /= 3.0;
    }
    return field;
}

vec3 stone(vec3 p, vec3 n, mat3 axes, vec3 ray, float travel, float size) {
    vec3 worldN = axes*n;
    vec3 inside = clamp(p - n * .0008, vec3(-2.9999), vec3(2.9999));
    float edge = edgeDistance(inside, n);
    vec2 face = abs(n.x) > .5 ? p.zy : (abs(n.y) > .5 ? p.xz : p.xy);
    float plate = hash2(floor((face+3.0) * 4.499) + 41.0*dot(n,vec3(1,3,7)));
    float exposure = smoothstep(.0, 2.3, max(abs(p.x), max(abs(p.y),abs(p.z))) - .35);
    vec3 sun = normalize(vec3(-.54,.69,-.54));
    float diffuse = max(dot(worldN,sun),0.0);
    float obstruction = .0; vec3 wallNormal;
    float sunlit = 1.0;
    if (diffuse > .0 && traceSponge(p+n*.001, sun*axes, 9.0, obstruction, wallNormal))
        sunlit = .14 + .12*smoothstep(.0,2.0,obstruction);
    float ao = .30 + .70*clamp(.53*spongeField(p+.27*n)/.27
                                + .47*spongeField(p+.84*n)/.84,0.0,1.0);
    vec3 reflected = reflect(ray, worldN);
    float softbox = exp(-5.6 * abs(dot(reflected, normalize(vec3(.36,.83,.42))) - .23));
    float warmBox = pow(max(dot(reflected, normalize(vec3(-.63,.29,-.72))), 0.0), 9.0);
    // Fine lamination in volcanic glass, visible mostly where a reflection grazes it.
    float band = dot(face,vec2(3.6,1.9)) + 1.4*sin(face.y*1.7+.6*sin(face.x*4.0));
    float flow = .5+.5*cos(4.0*band+.7*sin(6.1*band));
    float grain = .80+.13*flow+.07*plate;
    vec3 body = vec3(.052,.070,.096) * (.53 + .47*exposure);
    body += vec3(.09,.12,.165)*softbox*grain;
    float spill = .40+.60*smoothstep(-2.8,2.9,dot(p,vec3(-.60,.74,-.26)));
    body += vec3(.30,.151,.060)*diffuse*sunlit*grain*spill;
    body += vec3(.55,.29,.12)*warmBox*sunlit;
    body *= ao;
    float footprint = max(.002, travel / (iResolution.y * size));
    float rim = 1.0 - smoothstep(.003, .010 + .65*footprint, edge);
    float bevel = exp(-edge / .023);
    float grazing = 1.0 - max(dot(-ray, worldN), 0.0);
    float alloy = .71 + .29*sin(dot(p,vec3(1.7,-1.4,.91))+.4*flow);
    vec3 copper = mix(vec3(.52,.175,.031), vec3(1.35,.69,.18), diffuse*sunlit);
    body += alloy*(.42+.58*ao)*(copper*bevel*.84
            + vec3(1.0,.47,.105)*rim*(.49+.32*sunlit));
    body += vec3(.064,.086,.117)*grazing*grazing*ao;
    return body;
}

void consider(vec3 eye, vec3 ray, vec3 center, mat3 axes, float size,
              inout float best, inout vec3 color) {
    vec3 localEye = (eye - center) * axes / size;
    vec3 localRay = ray * axes;
    float t; vec3 n;
    if (traceSponge(localEye, localRay, best / size, t, n)) {
        best = size * t;
        color = stone(localEye + t * localRay, n, axes, ray, best, size);
    }
}

float dust(vec2 p, float scale, float phase) {
    vec2 g = p*scale;
    vec2 cell = floor(g);
    vec2 seed = vec2(hash2(cell+3.8), hash2(cell+19.1));
    vec2 delta = fract(g) - (.16 + .68*seed);
    float r = max(.026, .60*scale/iResolution.y);
    return step(.87, seed.x) * exp(-dot(delta,delta) / (r*r)) * (.7+.3*cos(phase+TAU*seed.y));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    // Eleven flights fit one 262144 ms block of the client's wrapped clock.
    float cycle = fract(mod(iTime, 262.144) * (11.0 / 262.144));
    float a = TAU * cycle;
    float z = 2.0*SPACING*cycle - 9.2;
    vec3 eye = vec3(flight(z), z);
    vec2 aim = (flight(z+2.2) - eye.xy)/2.2 + vec2(.072*sin(a+.8),.047*cos(a));
    // Slide past the outer corner in the open space, then align with the next bore.
    float gap = smoothstep(.66,1.0,abs(sin(z*TAU/(2.0*SPACING))));
    eye.xy += vec2(4.55,1.85)*gap;
    float nextZ = (floor(z/SPACING)+1.0)*SPACING;
    float nextKind = mod(floor(z/SPACING)+1.0,2.0);
    vec2 target = flight(nextZ) - nextKind*vec2(.72,.62);
    float along = SPACING - (nextZ-z);
    float lead = smoothstep(.0,5.5,along)*(1.0-smoothstep(10.0,14.8,along));
    aim = mix(aim,(target-eye.xy)/max(nextZ-z,1.0),.94*lead);
    vec3 forward = normalize(vec3(aim,1));
    vec3 right = normalize(cross(vec3(0,1,0), forward));
    vec3 up = cross(forward, right);
    float roll = .105*sin(a+.45);
    vec2 screen = (fragCoord-.5*iResolution.xy)/iResolution.y;
    vec2 lens = mat2(cos(roll),sin(roll),-sin(roll),cos(roll)) * screen;
    vec3 ray = normalize(.85*forward + lens.x*right + lens.y*up);

    float amber = exp(-2.8*length(ray.xy-vec2(-.52,.29)));
    float haze = exp(-7.0*abs(ray.y-.22*ray.x-.11));
    vec3 sky = vec3(.023,.030,.049) + vec3(.25,.092,.020)*amber + vec3(.066,.039,.020)*haze;
    vec2 sphere = ray.xy / max(.25,ray.z);
    sky += vec3(.83,.49,.22) * (dust(sphere,36.0,a) + .45*dust(sphere+3.2,78.0,-a));

    float best = FAR;
    vec3 color = sky;
    float base = floor((eye.z + 5.0) / SPACING);
    for (int j = 0; j < 4; ++j) {
        float idx = base + float(j);
        float centerZ = idx * SPACING;
        if (centerZ - eye.z > best + 6.0) break;
        float kind = mod(idx, 2.0);
        mat3 axes = tunnelFrame(1.0-2.0*kind, mix(-.16,.38,kind));
        vec3 center = vec3(mix(vec2(.26,-.21),vec2(-.26,.21),kind),centerZ);
        center -= 2.0 * kind * (axes[0] + axes[1]);
        consider(eye, ray, center, axes, 1.0, best, color);
        vec3 side = vec3(mix(-5.65,5.15,kind), mix(1.8,-1.7,kind), centerZ+1.5);
        consider(eye, ray, side, tunnelFrame(-.8,.55-kind), .43, best, color);
    }
    if (best < FAR) color = mix(sky, color, exp(-.021*best));
    color *= 1.0 - .23*smoothstep(.24,1.02,length(screen));
    color = 1.0 - exp(-color * 1.38);
    color += (hash2(fragCoord)-.5)/255.0;
    fragColor = vec4(clamp(color,0.0,1.0),1);
}
