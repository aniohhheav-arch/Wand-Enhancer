#version 150

#moj_import <riftverse:rv_common.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform mat4 ViewProj;
uniform float RvTime;
uniform vec3 Center;
uniform vec3 Normal;
uniform vec3 UpDir;
uniform vec2 HalfSize;
uniform vec3 ColorA;
uniform vec3 ColorB;
// x style, y open 0..1, z shape (0 tear, 1 ellipse, 2 rectangle), w intensity
uniform vec4 RiftParams;
// rgb destination sky colour, a window strength
uniform vec4 DestSky;

in vec2 texCoord;
out vec4 fragColor;

const int STYLE_AZURE = 0;
const int STYLE_CRIMSON = 1;
const int STYLE_VERDANT = 2;
const int STYLE_VOID = 3;
const int STYLE_PRISMATIC = 4;
const int STYLE_NEXUS = 5;
const int STYLE_GLITCH = 6;
const int STYLE_STELLAR = 7;
const int STYLE_RETURN = 8;
const int STYLE_PORTAL = 9;
const int STYLE_GATE = 10;

float shapeDistance(vec2 uv, int shape, float open, float seed) {
    if (shape == 2) {
        vec2 q = abs(uv) - vec2(1.0 - 0.06);
        float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
        return 1.0 + d * 8.0;
    }
    if (shape == 1) return length(uv) / max(open, 0.001);
    uv.x *= 1.0 + abs(uv.y) * 1.5 + (1.0 - open) * 2.0;
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float jag = rvFbm3(vec3(cos(a) * 1.0 + seed, sin(a) * 1.0, RvTime * 0.6), 4) - 0.5;
    float jag2 = rvNoise3(vec3(cos(a) * 4.5, sin(a) * 4.5 - seed, RvTime * 2.5)) - 0.5;
    float radius = (0.82 + jag * 0.45 + jag2 * 0.12) * open;
    return r / max(radius, 0.001);
}

vec3 destSky(vec3 dir, float swirlA) {
    vec3 d = rvRotate(dir, normalize(Normal), swirlA);
    float h = d.y;
    vec3 sky = mix(DestSky.rgb * 0.5, DestSky.rgb * 1.15 + 0.05, smoothstep(-0.2, 0.6, h));
    sky += vec3(rvStars(d, 90.0, 0.12, RvTime)) * 0.8;
    float neb = rvFbm3(d * 3.0 + RvTime * 0.02, 4);
    sky += mix(ColorA, ColorB, neb) * smoothstep(0.5, 0.85, neb) * 0.5;
    return sky;
}

vec3 interior(int style, vec2 uv, float d, vec3 rayDir, vec2 screenUv, vec2 centerUv) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float t = RvTime;
    float swirl = a + r * 3.5 - t * 1.2;
    float n1 = rvFbm3(vec3(cos(swirl) * 0.8, sin(swirl) * 0.8, r * 5.0 - t * 0.8), 5);
    float n2 = rvFbm3(vec3(cos(a - t * 0.5) * 0.7, sin(a - t * 0.5) * 0.7, log(r + 0.05) * 3.0 + t), 4);
    vec3 base = mix(ColorA, ColorB, n1);
    float core = exp(-r * 3.2);
    vec3 col = base * (0.35 + n2 * 1.1) + vec3(1.0) * core * 0.9;

    if (style == STYLE_CRIMSON) {
        float fire = rvFbm3(vec3(cos(a), sin(a), r * 6.0 - t * 3.0), 6);
        col = mix(vec3(0.15, 0.0, 0.0), ColorA, smoothstep(0.25, 0.6, fire)) + ColorB * smoothstep(0.6, 0.9, fire) * 1.5 + core * vec3(1.0, 0.8, 0.5);
    } else if (style == STYLE_VERDANT) {
        vec2 cellP = uv * 5.0 + vec2(0.0, -t * 0.4);
        vec2 cell = floor(cellP);
        float best = 9.0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = rvHash22(cell + g);
            o = 0.5 + 0.4 * sin(t + RV_TAU * o);
            best = min(best, length(g + o - fract(cellP)));
        }
        col = mix(ColorA * 0.3, ColorA, smoothstep(0.0, 0.5, best)) + ColorB * smoothstep(0.12, 0.0, best) * 1.2 + core * 0.6;
    } else if (style == STYLE_VOID) {
        col = mix(vec3(0.0), ColorA * 0.6, smoothstep(0.55, 0.95, r)) + ColorB * n2 * 0.25;
        col += vec3(rvStars(rayDir, 120.0, 0.08, t)) * 0.6 * (1.0 - r);
    } else if (style == STYLE_PRISMATIC) {
        vec2 facet = floor(uv * 6.0 + vec2(sin(t * 0.3), cos(t * 0.2)));
        float fh = rvHash12(facet);
        col = rvHueShift(mix(ColorA, ColorB, fh), fh * 6.28 + t * 0.4) * (0.6 + 0.6 * fh) + core * 0.8;
        vec2 dir = normalize(screenUv - centerUv + 1e-5);
        col += vec3(texture(Sampler0, screenUv + dir * 0.01).r, texture(Sampler0, screenUv).g, texture(Sampler0, screenUv - dir * 0.01).b) * 0.35;
    } else if (style == STYLE_NEXUS) {
        float rings = abs(sin(r * 18.0 - t * 2.0)) * smoothstep(0.95, 0.2, r);
        float runes = step(0.7, rvHash12(vec2(floor((a + t * 0.3) * 12.0), floor(r * 9.0)))) * step(0.15, fract(r * 9.0)) * step(fract(r * 9.0), 0.4);
        col = mix(ColorA * 0.25, ColorA, rings) + ColorB * runes * 0.9 + core * vec3(1.0, 0.9, 0.6) * 1.4;
    } else if (style == STYLE_GLITCH) {
        vec2 q = floor(uv * 10.0) / 10.0;
        float g = rvHash12(q + floor(t * 12.0));
        col = mix(ColorA, ColorB, step(0.5, g)) * step(0.35, g);
        col += vec3(texture(Sampler0, screenUv + vec2((g - 0.5) * 0.05, 0.0)).r, 0.0, texture(Sampler0, screenUv - vec2((g - 0.5) * 0.05, 0.0)).b) * 0.6;
    } else if (style == STYLE_STELLAR) {
        vec3 d = rvRotate(rayDir, normalize(Normal), -t * 0.2 + r * 2.0);
        col = vec3(rvStars(d, 70.0, 0.25, t) + rvStars(d, 150.0, 0.3, t * 1.4)) * 1.4;
        float gal = exp(-r * 4.0) * (0.5 + 0.5 * sin(2.0 * a - log(r + 0.01) * 5.0 + t * 0.5));
        col += mix(ColorA, ColorB, gal) * gal * 2.0 + mix(ColorB, ColorA, n1) * 0.25;
    } else if (style == STYLE_RETURN) {
        col = mix(vec3(0.75, 0.85, 1.0), ColorB, n1) * (0.4 + n2) + core * 1.2;
    } else if (style == STYLE_PORTAL) {
        float waves = 0.5 + 0.5 * sin(r * 25.0 - t * 4.0 + n1 * 4.0);
        col = mix(ColorA * 0.25, ColorA * 1.3, waves * smoothstep(1.0, 0.2, r)) + core * 0.7;
    }

    if (DestSky.a > 0.0) {
        vec3 sky = destSky(rayDir, sin(t * 0.7 + r * 4.0) * 0.08);
        float window = DestSky.a * smoothstep(0.98, 0.55, d);
        col = mix(col, sky + base * 0.15, window);
        col += ColorA * 0.25 * sin(r * 30.0 - t * 3.0) * window * 0.3;
    }
    return col;
}

void main() {
    vec3 rd = rvRayDir(InvViewProj, texCoord);
    vec3 n = normalize(Normal);
    vec3 up = normalize(UpDir);
    vec3 right = normalize(cross(up, n));
    float denom = dot(rd, n);
    if (abs(denom) < 1e-4) discard;
    float t = dot(Center, n) / denom;
    if (t <= 0.0) discard;
    vec3 hit = rd * t;
    vec3 local = hit - Center;
    vec2 uv = vec2(dot(local, right) / HalfSize.x, dot(local, up) / HalfSize.y);

    int style = int(RiftParams.x + 0.5);
    int shape = int(RiftParams.z + 0.5);
    float open = RiftParams.y;
    float intensity = RiftParams.w;
    float seed = dot(Center, vec3(0.13, 0.17, 0.11));
    float d = shapeDistance(uv, shape, open, seed);
    if (d > 3.2) discard;

    float depth = texture(Sampler1, texCoord).r;
    float sceneDist = depth >= 1.0 ? 1e9 : length(rvWorldPos(InvViewProj, texCoord, depth));
    bool occluded = sceneDist < t - 0.35;

    vec3 centerProj = rvProject(ViewProj, Center);
    vec2 centerUv = centerProj.xy;
    vec2 toCenter = texCoord - centerUv;
    float glowFall = exp(-max(d - 1.0, 0.0) * 2.4);

    // Heat-haze distortion and swirl around the opening.
    float distortion = (style == STYLE_VOID ? 0.05 : 0.025) * intensity * open;
    float swirlAngle = distortion * 8.0 * glowFall * sin(RvTime * 0.7 + d * 4.0);
    vec2 offset = rvRot(swirlAngle) * toCenter - toCenter;
    offset += normalize(toCenter + 1e-5) * distortion * glowFall * (0.5 + 0.5 * sin(d * 18.0 - RvTime * 6.0));
    vec2 sampleUv = clamp(texCoord + (occluded ? vec2(0.0) : offset), 0.001, 0.999);
    vec3 scene = texture(Sampler0, sampleUv).rgb;

    if (occluded) {
        fragColor = vec4(texture(Sampler0, texCoord).rgb, 1.0);
        return;
    }

    vec3 col = scene;
    if (d < 1.0) {
        vec3 inner = interior(style, uv / max(open, 0.05), d, rd, texCoord, centerUv);
        float edge = smoothstep(1.0, 0.86, d);
        col = mix(scene, inner, edge);
    }
    float rim = exp(-abs(d - 1.0) * (shape == 2 ? 22.0 : 9.0));
    vec3 rimColor = mix(ColorB, vec3(1.0), 0.45);
    col += rimColor * rim * 1.3 * intensity * open;
    col += ColorA * glowFall * 0.35 * intensity * step(1.0, d) * open;

    // Lightning arcs crackling off the tear.
    if (shape == 0 && d > 1.0 && d < 2.6) {
        float a = atan(uv.y, uv.x);
        float arc = abs(rvNoise3(vec3(cos(a) * 0.8, sin(a) * 0.8, d * 3.0 - RvTime * 4.0)) - 0.5);
        float flick = step(0.6, rvNoise3(vec3(cos(a) * 0.32, sin(a) * 0.32, floor(RvTime * 8.0))));
        col += ColorB * smoothstep(0.03, 0.0, arc) * flick * (1.0 - (d - 1.0) / 1.6) * 1.2 * open;
    }
    fragColor = vec4(col, 1.0);
}
