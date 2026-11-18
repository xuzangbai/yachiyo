// TIDAL WALTZ -- an original procedural three-sun menu background.
// GLSL 330 Core. The host supplies iTime, iResolution, #version and main().
// No textures, history buffers, ray marching or numerical integration.
// The bounded choreography is art-directed, not an N-body simulation.
// Coordinates follow gl_FragCoord: the lower-left corner is (0, 0).

const float TW_SPEED = 1.0;          // Animation speed; 0.0 freezes the scene.
const float TW_EXPOSURE = 1.12;      // Overall exposure.
const float TW_MENU_SHADE = 0.26;    // Central menu dimming, from 0.0 to 1.0.
const float TW_TAU = 6.28318530718;

float twHash12(vec2 p)
{
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float twHash13(vec3 p)
{
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float twNoise2(vec2 p)
{
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(twHash12(cell), twHash12(cell + vec2(1, 0)), f.x),
               mix(twHash12(cell + vec2(0, 1)),
                   twHash12(cell + vec2(1, 1)), f.x), f.y);
}

float twNoise3(vec3 p)
{
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(twHash13(cell), twHash13(cell + vec3(1, 0, 0)), f.x),
            mix(twHash13(cell + vec3(0, 1, 0)),
                twHash13(cell + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(twHash13(cell + vec3(0, 0, 1)),
                twHash13(cell + vec3(1, 0, 1)), f.x),
            mix(twHash13(cell + vec3(0, 1, 1)),
                twHash13(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

mat2 twRotate(float angle)
{
    float s = sin(angle), c = cos(angle);
    return mat2(c, s, -s, c);
}

vec3 twOrbit(float phase, float time)
{
    float a = time * 0.105 + phase;
    vec3 p = vec3(2.65 * cos(a) + 0.38 * sin(2.0 * a + 0.4),
                  0.70 * sin(a) + 0.75 * sin(2.0 * a),
                  1.85 * sin(a) - 0.35 * cos(2.0 * a + 0.65));
    p *= 1.0 + 0.055 * sin(time * 0.17);
    p.xy = twRotate(0.22 + 0.12 * sin(time * 0.033)) * p.xy;
    p.xz = twRotate(0.14 * sin(time * 0.041)) * p.xz;
    p.y *= 0.62;
    return p;
}

vec3 twStarLayer(vec2 sky, float scale, float threshold, float seed,
                 float pixelAngle, float time)
{
    vec2 p = sky * scale;
    vec2 cell = floor(p);
    float id = twHash12(cell + seed);
    // Positions stay inside their cells, so no neighboring-cell search is needed.
    vec2 center = 0.20 + 0.60 * vec2(twHash12(cell + seed + 17.3),
                                     twHash12(cell + seed + 71.9));
    vec2 d = fract(p) - center;
    float aa = max(pixelAngle * scale, 0.001);
    float radius = 0.025 + 0.035 * twHash12(cell + seed + 9.7);
    float point = 1.0 - smoothstep(max(0.0, radius - aa), radius + aa, length(d));
    // Coverage keeps subpixel stars from turning into flashing single pixels.
    point *= min(1.0, 2.5 * radius * radius / (aa * aa));
    float glow = exp(-dot(d, d) * 100.0) * 0.035;
    float brightness = step(threshold, id) * (0.30 + 1.10 * id * id);
    float shimmer = 0.94 + 0.06 * sin(time * 0.65 + id * 190.0);
    vec3 tint = mix(vec3(0.53, 0.69, 1.0), vec3(1.0, 0.76, 0.49),
                    twHash12(cell + seed + 4.2));
    return tint * (point + glow) * brightness * shimmer;
}

vec3 twSpace(vec3 ray, float pixelAngle, float time)
{
    vec2 sky = ray.xy / max(0.22, -ray.z);
    vec2 p = twRotate(-0.38) * sky;
    float n0 = twNoise2(p * 3.4 + vec2(8.1, 2.7));
    float n1 = twNoise2(p * 8.7 + vec2(21.8, 8.3));
    float n2 = twNoise2(p * 21.0 + vec2(4.7, 17.1));
    float cloud = 0.57 * n0 + 0.29 * n1 + 0.14 * n2;
    float bandCoordinate = (p.y + 0.19 + 0.10 * sin(p.x * 4.0)) * 4.2;
    float band = exp(-bandCoordinate * bandCoordinate);
    float dust = smoothstep(0.30, 0.75, cloud) * band;
    vec3 color = vec3(0.0016, 0.0024, 0.0060);
    color += mix(vec3(0.016, 0.009, 0.029), vec3(0.008, 0.025, 0.040),
                 n0) * dust;
    color *= 0.72 + 0.28 * n1;
    color += twStarLayer(sky, 76.0, 0.989, 11.2, pixelAngle, time);
    color += twStarLayer(sky, 151.0, 0.993, 47.8, pixelAngle, time) * 0.67;
    color += twStarLayer(sky, 263.0, 0.995, 93.4, pixelAngle, time) * 0.40;
    return color;
}

vec3 twSurface(vec3 normal, vec3 ray, int sun, float time)
{
    vec3 p = normal;
    float seed = float(sun) * 13.7;
    p.xz = twRotate(time * (0.043 + float(sun) * 0.009) + seed) * p.xz;
    p.xy = twRotate(0.31 + float(sun) * 0.53) * p.xy;
    float flow = twNoise3(p * 3.8 + vec3(seed, time * 0.028, 0.0));
    vec3 q = p * 12.0 + (flow - 0.5) * 2.7 + vec3(seed, time * 0.035, 0.0);
    float cells = 0.57 * twNoise3(q)
                + 0.28 * twNoise3(q * 2.03 + 7.1)
                + 0.15 * twNoise3(q * 4.07 + 19.2);
    float grain = twNoise3(p * 79.0 + seed + vec3(0.0, time * 0.09, 0.0));
    float heat = smoothstep(0.17, 0.85, cells);
    vec3 cool, hot;
    if (sun == 0) {
        cool = vec3(0.67, 0.065, 0.006);
        hot = vec3(3.80, 1.92, 0.53);
    } else if (sun == 1) {
        cool = vec3(0.025, 0.15, 0.46);
        hot = vec3(0.83, 1.96, 3.70);
    } else {
        cool = vec3(0.43, 0.009, 0.002);
        hot = vec3(3.10, 0.30, 0.045);
    }
    float spots = smoothstep(0.66, 0.85, flow);
    float facing = max(dot(normal, -ray), 0.0);
    float limb = 0.22 + 0.78 * pow(facing, 0.55);
    grain = mix(0.5, grain, smoothstep(0.10, 0.32, facing));
    vec3 color = mix(cool, hot, heat) * (0.74 + 0.52 * grain);
    color *= limb * (1.0 - 0.65 * spots);
    // Fine hot seams are part of the photosphere, with no extra geometry.
    color += hot * 0.12 * smoothstep(0.56, 0.72, cells) * limb;
    return color;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec2 resolution = max(iResolution.xy, vec2(1.0));
    vec2 uv = (fragCoord - 0.5 * resolution) / resolution.y;
    float time = iTime * TW_SPEED;
    // Widen the view on narrow windows rather than cutting off the outer suns.
    float lens = 1.68 * min(1.0, resolution.x / (1.50 * resolution.y));

    vec3 camera = vec3(0.56 * sin(time * 0.027),
                       0.30 + 0.18 * sin(time * 0.019), 10.4);
    vec3 target = vec3(0.0, -0.18, 0.0);
    vec3 forward = normalize(target - camera);
    vec3 right = normalize(cross(forward, vec3(0, 1, 0)));
    vec3 up = cross(right, forward);
    vec3 ray = normalize(right * uv.x + up * uv.y + forward * lens);
    float pixelAngle = 1.0 / (lens * resolution.y);

    vec3 positions[3];
    positions[0] = twOrbit(0.16, time);
    positions[1] = twOrbit(0.16 + TW_TAU / 3.0, time);
    positions[2] = twOrbit(0.16 + TW_TAU * 2.0 / 3.0, time);
    float radii[3];
    radii[0] = 0.65;
    radii[1] = 0.49;
    radii[2] = 0.43;
    vec3 tints[3];
    tints[0] = vec3(1.0, 0.43, 0.095);
    tints[1] = vec3(0.20, 0.57, 1.0);
    tints[2] = vec3(1.0, 0.12, 0.025);

    // Three analytic ray/sphere tests give exact front-to-back visibility.
    float nearest = 1e20;
    float coverage = 0.0;
    int hit = -1;
    for (int i = 0; i < 3; ++i) {
        vec3 toSun = positions[i] - camera;
        float along = dot(toSun, ray);
        float d = length(toSun - ray * along);
        float aa = max(along * pixelAngle, 0.00001);
        if (along > 0.0 && d < radii[i] + aa) {
            float entry = along - sqrt(max(0.0, radii[i] * radii[i] - d * d));
            if (entry > 0.0 && entry < nearest) {
                nearest = entry;
                hit = i;
                coverage = 1.0 - smoothstep(radii[i] - aa, radii[i] + aa, d);
            }
        }
    }

    vec3 color = twSpace(ray, pixelAngle, time);
    if (hit >= 0) {
        vec3 normal = normalize(camera + ray * nearest - positions[hit]);
        color = mix(color, twSurface(normal, ray, hit, time), coverage);
    }

    // Analytic light scattering; noise is evaluated only close to a solar limb.
    for (int i = 0; i < 3; ++i) {
        vec3 toSun = positions[i] - camera;
        float along = dot(toSun, ray);
        if (along <= 0.0 || (hit >= 0 && hit != i && along > nearest))
            continue;
        vec3 radial = ray * along - toSun;
        float r = length(radial) / radii[i];
        float outside = max(r - 1.0, 0.0);
        float aa = max(along * pixelAngle / radii[i], 0.0001);
        float limbMask = smoothstep(1.0 - aa, 1.0 + aa, r);
        float halo = 0.105 * exp(-outside * 2.6)
                   + 0.006 / (0.25 + r * r);
        color += tints[i] * halo * limbMask;

        if (r > 0.94 && r < 2.5) {
            vec3 direction = radial / max(length(radial), 0.0001);
            direction.xz = twRotate(time * 0.035 + float(i)) * direction.xz;
            float threads = twNoise3(direction * 11.0 + float(i) * 9.2
                                    + vec3(0.0, time * 0.075, 0.0));
            float fine = twNoise3(direction * 27.0 - time * 0.04);
            float reach = 0.055 + 0.19 * threads * threads;
            float corona = exp(-outside / reach) * (0.20 + 0.62 * threads);
            corona += 0.12 * exp(-outside * 11.0) * fine;
            color += tints[i] * corona * limbMask;
        }

        // Subtle horizontal optical streak, suppressed behind opaque spheres.
        float depth = dot(toSun, forward);
        vec2 projected = lens * vec2(dot(toSun, right), dot(toSun, up))
                         / max(depth, 0.01);
        vec2 delta = uv - projected;
        float streak = exp(-abs(delta.x) * 7.5)
                       * exp(-abs(delta.y) * 620.0) * 0.018;
        color += tints[i] * streak * limbMask;
    }

    // Broad, soft shading keeps the central Minecraft menu readable.
    vec2 menuDistance = vec2(uv.x / 0.31, (uv.y + 0.08) / 0.35);
    menuDistance *= menuDistance;
    float menu = exp(-dot(menuDistance, menuDistance));
    color *= 1.0 - TW_MENU_SHADE * menu;
    float vignette = 1.0 - 0.18 * smoothstep(0.25, 1.12, length(uv * vec2(0.8, 1.0)));
    color *= vignette;
    color = vec3(1.0) - exp(-max(color, vec3(0.0)) * TW_EXPOSURE);
    color = pow(clamp(color, 0.0, 1.0), vec3(1.0 / 2.2));
    // Sub-LSB dither prevents banding in the dark background. Alpha stays opaque.
    color += (twHash12(fragCoord) - 0.5) / 255.0;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
