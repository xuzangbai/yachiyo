#version 330
#define MENU_LOOP_MS 32768
#moj_import <yachiyoc:menu_scene_input.glsl>

// Yachiyo menu shader. The host supplies iTime in seconds and iResolution.xy in pixels.
const float TAU = 6.28318530718;

float hash2(vec2 p) {
    vec3 q = fract(p.xyx * vec3(.1031,.11369,.13787));
    q += dot(q,q.yzx+19.19);
    return fract((q.x+q.y)*q.z);
}

float mistNoise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(hash2(cell),hash2(cell+vec2(1,0)),f.x),
               mix(hash2(cell+vec2(0,1)),hash2(cell+vec2(1)),f.x),f.y);
}

float starlight(vec2 p, float freq, float t) {
    vec2 cell = floor(p*freq);
    vec2 seed = vec2(hash2(cell),hash2(cell+33.0));
    vec2 v = fract(p*freq) - (.12+.76*seed);
    float d = dot(v,v);
    float r = max(.022+.021*seed.x, .52*freq/iResolution.y);
    return step(.91,seed.y)*(.55+.45*cos(t+TAU*seed.x)) * exp(-d/(r*r));
}

vec3 atmosphere(vec2 p, float t) {
    vec2 wind = vec2(cos(t),sin(t));
    float f = mistNoise(p*3.8+.15*wind);
    f = .69*f + .31*mistNoise(p*9.0+f+wind*.08);
    float plume = p.y - .26 - .19*p.x - .20*(f-.5);
    float wisp = exp(-6.0*abs(plume))*(.12+.88*f*f);
    vec3 col = vec3(.014,.022,.063);
    col += vec3(.034,.095,.16)*wisp;
    col += vec3(.065,.021,.14)*exp(-2.3*length(p-vec2(.65,.16)))*(.6+.4*f);
    col += vec3(.024,.104,.175)*exp(-2.5*length(p-vec2(-.55,.05)));
    col += vec3(.46,.66,1.0)*(starlight(p,37.0,t)+.40*starlight(p+5.6,91.0,-t));
    return col;
}

vec3 caustics(vec2 p, float t) {
    // An oblique view of the light refracted onto the surface below the crystal.
    vec2 q = (p - vec2(.015,-.359)) / vec2(.48,.066);
    float radius = length(q);
    if (radius > 2.7) return vec3(0);
    float a = atan(q.y,q.x+.000001);
    float r = radius + .084*sin(3.0*a+t) + .050*cos(7.0*a-t+2.0*radius);
    float light = .0;
    for (int i = 0; i < 3; ++i) {
        float k = float(i);
        float wave = r - (.47+.23*k+.064*sin(5.0*a + t + 2.0*k));
        light += exp(-abs(wave)*(22.0+8.0*k)) * (.43+.37*cos(3.0*a+k+t));
    }
    vec3 tint = mix(vec3(.08,.49,.84),vec3(.45,.16,.88),smoothstep(-1.1,1.0,q.x));
    return tint * (.46*light*exp(-.9*radius) + .14*exp(-1.5*radius*radius));
}

mat2 rotate2(float angle) {
    float c = cos(angle), s = sin(angle);
    return mat2(c,s,-s,c);
}

float cross2(vec2 u,vec2 v) { return u.x*v.y-u.y*v.x; }

struct Corner {
    vec3 pos;
    vec2 film;
    float w;
};

struct Glass {
    vec3 scatter;
    float opticalDepth;
    vec3 frontLight;
    float frontZ;
};

void pane(vec2 p, float aa, vec3 view, Corner a, Corner b, Corner c, Corner d, inout Glass glass) {
    vec2 e0=b.film-a.film, e1=c.film-b.film, e2=d.film-c.film, e3=a.film-d.film;
    float winding = cross2(e0,-e3);
    if (abs(winding)<.000003) return;
    vec4 areas = sign(winding)*vec4(cross2(e0,p-a.film),cross2(e1,p-b.film),
                                    cross2(e2,p-c.film),cross2(e3,p-d.film));
    if (min(min(areas.x,areas.y),min(areas.z,areas.w)) < -.002) return;
    vec4 distances = areas * inversesqrt(max(vec4(dot(e0,e0),dot(e1,e1),
                                                   dot(e2,e2),dot(e3,e3)),vec4(.0000001)));
    float edge = min(min(distances.x,distances.y),min(distances.z,distances.w));
    float cover = smoothstep(-aa,aa,edge);
    if (cover <= .0) return;
    vec3 n = normalize(cross(b.pos-a.pos,d.pos-a.pos));
    if (dot(n,view)<0.0) n = -n;
    float incidence = max(.01,dot(n,view));
    vec3 reflected = reflect(-view,n);
    float fresnel = pow(1.0-incidence,2.5);
    float side = .5+.5*clamp(.82*n.x + .36*n.z - .23*(a.w+c.w),-1.0,1.0);
    vec3 tint = mix(vec3(.052,.55,.93),vec3(.51,.16,.97),side);
    // Broad studio reflections and a narrow companion strip across the bevel.
    float band = dot(reflected,normalize(vec3(.69,.62,.37)));
    float panel = smoothstep(.08,.26,band)*(1.0-smoothstep(.47,.65,band));
    float strip = exp(-38.0*abs(band-.69));
    float bevel = exp(-max(edge,0.0)/.0075);
    float pathLength = dot(n,vec3(0,0,4.9)-a.pos)/incidence;
    vec3 hitPos = vec3(0,0,4.9) - view*pathLength;
    vec3 bent = refract(-view,n,.69);
    // The narrow spectral bands are images of a light strip seen through the glass.
    vec3 exitPos = hitPos + bent*(.65+.24*(1.0-incidence));
    float path = dot(exitPos,normalize(vec3(.82,-.49,.62)))
               + .71*dot(bent,normalize(vec3(.27,.86,-.44)));
    float ribbon = smoothstep(-.17,-.045,path)*(1.0-smoothstep(.0,.18,path));
    vec3 fringe = exp(-abs(vec3(path)-vec3(.176,.152,.129))*29.0);
    float echo = exp(-abs(path+.34)*23.0);
    float glancing = .32+.68*pow(1.0-incidence,.65);
    vec3 caustic = glancing*(ribbon*mix(vec3(.09,.69,.95),vec3(.73,.20,.99),side)
                   + .63*fringe+.37*echo*tint);
    vec3 light = tint*(.18+.38*fresnel+.24*bevel)
               + mix(vec3(.42,.83,1),vec3(.92,.64,1),side)
                 * (.31*panel+.50*strip+.25*fresnel) + .80*caustic;
    float density = cover*(.115+.20*fresnel+.12*panel);
    glass.scatter += light*density;
    glass.opticalDepth += density;
    // Intersect the viewing ray with this face to place its reflected highlight in front.
    float z = hitPos.z;
    if (cover>.45 && z>glass.frontZ) {
        glass.frontZ = z;
        glass.frontLight = cover*(.24*panel+.33*strip+.16*fresnel)*mix(tint,vec3(.74,.89,1.0),panel*.5) + .46*cover*caustic;
    }
}

vec3 crystal(vec2 p, float t, float aa, vec3 color) {
    Corner v[16];
    vec2 lower = vec2(2), upper = vec2(-2);
    mat2 xw = rotate2(t+.16), yw = rotate2(.26*sin(2.0*t+.4));
    mat2 zw = rotate2(.29*sin(t+.22)), yaw = rotate2(.51+.18*sin(t));
    mat2 pitch = rotate2(-.37+.13*cos(t)), roll = rotate2(.16+.12*sin(t-.4));
    for (int i=0; i<16; ++i) {
        vec4 q = 2.0*vec4(float(i&1),float((i>>1)&1),float((i>>2)&1),float((i>>3)&1))-1.0;
        q.xw = xw*q.xw;
        q.yw = yw*q.yw;
        q.zw = zw*q.zw;
        vec3 xyz = q.xyz*(2.42/(3.28-q.w));
        xyz.xz = yaw*xyz.xz;
        xyz.yz = pitch*xyz.yz;
        xyz.xy = roll*xyz.xy;
        v[i].pos = xyz;
        v[i].film = 1.035*xyz.xy/(4.9-xyz.z);
        v[i].w = q.w;
        lower = min(lower,v[i].film);
        upper = max(upper,v[i].film);
    }

    if (any(lessThan(p,lower-.070)) || any(greaterThan(p,upper+.070))) return color;

    Glass glass = Glass(vec3(0),0.0,vec3(0),-9.0);
    vec3 view = normalize(vec3(-p,1.035));
    // Each distinct pair of the four axes spans four square faces.
    for (int i=0; i<16; ++i) {
        for (int axis=0; axis<3; ++axis) {
            int u = 1<<axis;
            if ((i&u)!=0) continue;
            for (int other=axis+1; other<4; ++other) {
                int w = 1<<other;
                if ((i&w)==0) pane(p,aa,view,v[i],v[i+u],v[i+u+w],v[i+w],glass);
            }
        }
    }
    color *= exp(-glass.opticalDepth*vec3(.77,.51,.34));
    color += glass.scatter/(1.0+.25*glass.opticalDepth) + glass.frontLight;
    vec3 rims = vec3(0), halos = vec3(0), jewels = vec3(0);
    for (int i=0; i<16; ++i) {
        Corner a = v[i];
        vec2 fromNode = p-a.film;
        float pointDist = length(fromNode);
        float sparkle = pow(max(0.0,cos(t*2.0 + .91*float(i))),10.0);
        if (pointDist < .055) {
            vec2 streak = abs(rotate2(.17)*fromNode);
            float star = exp(-75.0*streak.x-980.0*streak.y) + exp(-185.0*streak.y-1200.0*streak.x);
            jewels = max(jewels,vec3(.73,.89,1.0)*(exp(-pointDist/.0038)+.61*sparkle*star));
        }
        for (int axis=0; axis<4; ++axis) {
            int bit = 1<<axis;
            if ((i&bit)!=0) continue;
            Corner b = v[i+bit];
            vec2 e = b.film-a.film;
            float h = clamp(dot(fromNode,e)/max(dot(e,e),.0000001),0.0,1.0);
            vec2 delta = fromNode-h*e;
            float dist = length(delta);
            if (dist > .069) continue;
            float z = mix(a.pos.z,b.pos.z,h);
            float depth = smoothstep(-1.75,1.75,z);
            float hue = smoothstep(-1.65,1.65,mix(a.w,b.w,h)*.65 + mix(a.pos.x,b.pos.x,h)*.74);
            vec3 tint = mix(vec3(.18,.84,1.0),vec3(.72,.32,1.0),hue);
            float width = .00056+.00056*depth;
            float core = 1.0 - smoothstep(max(.0,width-.62*aa),width+.70*aa,dist);
            float strength = .39+.71*depth;
            rims = max(rims, strength*(mix(tint,vec3(.90,.95,1),.45)*core
                  + .38*tint*exp(-dist/.0036)));
            halos = max(halos,.21*tint*strength*exp(-dist/.013));
        }
    }
    color += 1.48*rims + halos + jewels;
    return color;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    float t = TAU*fract(iTime/32.768);
    float span = min(iResolution.y, iResolution.x*1.09);
    vec2 screen = (fragCoord-.5*iResolution.xy)/span;
    float aa = 1.0/span;
    vec2 p = screen - vec2(.0,.025+.009*sin(2.0*t));
    vec3 color = atmosphere(screen,t) + caustics(screen,t);
    if (dot(p,p)<.315) {
        color = crystal(p,t,aa,color);
    }
    color *= 1.0-.18*smoothstep(.28,1.35,length(screen));
    color = 1.0-exp(-1.36*color);
    color += (hash2(fragCoord)-.5)/255.0;
    fragColor = vec4(clamp(color,.0,1.0),1);
}
