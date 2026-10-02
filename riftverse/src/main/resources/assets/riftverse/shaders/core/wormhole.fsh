#version 150

#moj_import <riftverse:rv_common.glsl>

uniform float RvTime;
uniform vec3 ColorA;
uniform vec3 ColorB;
// x aspect, y progress 0..1, z mode (0 short rift, 1 black hole journey), w intensity
uniform vec4 WormParams;
// x flash, y speed boost, z roll, w seed
uniform vec4 WormExtra;

in vec2 texCoord;
out vec4 fragColor;

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

vec3 tunnel(vec2 uv, float t, float speed, vec3 ca, vec3 cb, float twist) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float z = 0.45 / max(r, 0.015);
    float zt = z + t * speed;
    float at = a / RV_PI + twist * z * 0.12 + t * 0.08;
    float A = at * RV_PI;
    float walls = rvFbm3(vec3(cos(A) * 0.95, sin(A) * 0.95, zt * 0.55), 5);
    float fine = rvFbm3(vec3(cos(A) * 3.5, sin(A) * 3.5, zt * 2.2), 3);
    float bands = 0.5 + 0.5 * sin(A * 2.0 + zt * 1.3);
    vec3 col = mix(ca, cb, walls) * (0.2 + walls * 1.3) * (0.55 + 0.45 * bands) * (0.8 + fine * 0.4);

    vec2 sp = vec2(at * 52.0, zt * 1.6);
    vec2 cell = floor(sp);
    vec2 f = fract(sp);
    float h = rvHash12(cell);
    float streak = step(0.82, h) * smoothstep(0.14, 0.0, abs(f.x - 0.5)) * smoothstep(0.0, 0.5, f.y) * smoothstep(1.0, 0.5, f.y);
    col += mix(vec3(1.0), cb, 0.3) * streak * (0.8 + h) * smoothstep(0.02, 0.2, r);

    for (int k = 0; k < 4; k++) {
        float idx = floor(t * speed * 0.35) + float(k);
        float gh = rvHash11(idx * 7.13 + WormExtra.w);
        if (gh > 0.55) continue;
        float zk = (idx + 0.5) / 0.35;
        float rel = zk - t * speed;
        if (rel < 0.08) continue;
        float rpos = 0.45 / rel;
        if (rpos > 1.7) continue;
        float ang = rvHash11(idx * 3.71) * RV_TAU;
        vec2 gp = vec2(cos(ang), sin(ang)) * rpos;
        float size = 0.12 + rpos * 0.35;
        vec2 q = (uv - gp) / size;
        q = rvRot(ang + t * 0.4) * q;
        q.y /= 0.45;
        float gr = length(q);
        float garm = 0.5 + 0.5 * sin(2.0 * atan(q.y, q.x) - log(gr + 0.02) * 5.0);
        vec3 gcol = mix(ca, vec3(1.0, 0.9, 0.8), 0.5) * exp(-gr * 2.4) * (0.4 + garm) + vec3(1.0) * exp(-gr * 16.0);
        col += gcol * smoothstep(1.7, 1.1, rpos);
    }
    col *= smoothstep(0.0, 0.3, r) * 0.85 + 0.15;
    col += mix(vec3(1.0), cb, 0.25) * exp(-r * 6.0) * 1.1;
    return col;
}

vec3 accretion(vec2 uv, float t) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float spiral = sin(a * 3.0 + log(r + 0.01) * 9.0 - t * 7.0);
    float sa = a + log(r + 0.01) * 1.5 - t * 1.0;
    float turb = rvFbm3(vec3(cos(sa) * 0.7, sin(sa) * 0.7, r * 4.0), 5);
    vec3 hot = vec3(1.0, 0.85, 0.6);
    vec3 cool = vec3(1.0, 0.35, 0.08);
    vec3 col = mix(cool, hot, smoothstep(0.9, 0.1, r)) * (0.4 + 0.6 * max(spiral, 0.0)) * (0.5 + turb);
    col *= smoothstep(0.05, 0.22, r);
    col += vec3(1.0, 0.7, 0.4) * pow(max(spiral, 0.0), 6.0) * 0.6 * smoothstep(1.2, 0.3, r);
    float horizon = smoothstep(0.16, 0.2, r);
    return col * horizon;
}

vec3 tesseract(vec2 uv, float t, vec3 ca, vec3 cb) {
    vec2 pts[16];
    for (int i = 0; i < 16; i++) {
        vec4 v = vec4((i & 1) != 0 ? 1.0 : -1.0, (i & 2) != 0 ? 1.0 : -1.0, (i & 4) != 0 ? 1.0 : -1.0, (i & 8) != 0 ? 1.0 : -1.0);
        float a = t * 0.55;
        v = vec4(v.x * cos(a) - v.w * sin(a), v.y, v.z, v.x * sin(a) + v.w * cos(a));
        float b = t * 0.37;
        v = vec4(v.x, v.y * cos(b) - v.z * sin(b), v.y * sin(b) + v.z * cos(b), v.w);
        float c = t * 0.29;
        v = vec4(v.x, v.y, v.z * cos(c) - v.w * sin(c), v.z * sin(c) + v.w * cos(c));
        float w = 1.0 / (2.7 - v.w);
        vec3 p3 = v.xyz * w;
        float d = t * 0.45;
        p3 = vec3(p3.x * cos(d) + p3.z * sin(d), p3.y, -p3.x * sin(d) + p3.z * cos(d));
        float z = 1.0 / (3.2 - p3.z);
        pts[i] = p3.xy * z * 2.4;
    }
    float glow = 0.0;
    for (int i = 0; i < 16; i++) {
        for (int k = 0; k < 4; k++) {
            int j = i ^ (1 << k);
            if (j > i) {
                float dd = segDist(uv, pts[i], pts[j]);
                glow += exp(-dd * 140.0) * 0.9 + 0.0025 / (dd + 0.012);
            }
        }
    }
    float vertexGlow = 0.0;
    for (int i = 0; i < 16; i++) vertexGlow += exp(-length(uv - pts[i]) * 60.0);
    vec3 col = mix(ca, cb, 0.5 + 0.5 * sin(t + length(uv) * 6.0)) * glow * 0.55 + vec3(1.0) * vertexGlow * 0.8;
    return col;
}

vec2 kaleido(vec2 uv, float n, float rot) {
    float r = length(uv);
    float a = atan(uv.y, uv.x) + rot;
    float seg = RV_TAU / n;
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    return vec2(cos(a), sin(a)) * r;
}

vec3 dimensional(vec2 uv, float t, vec3 ca, vec3 cb) {
    vec2 k = kaleido(uv, 8.0, t * 0.15);
    vec2 q = k;
    vec3 col = vec3(0.0);
    float scale = 1.0;
    for (int i = 0; i < 5; i++) {
        q = abs(q * 1.55) - vec2(0.55, 0.32) + 0.05 * sin(t * 0.6 + float(i));
        q = rvRot(t * 0.07 + float(i) * 0.6) * q;
        scale *= 1.55;
        float d = length(q) / scale;
        col += mix(ca, cb, float(i) / 4.0) * exp(-d * 90.0) * 0.6;
    }
    float frames = step(0.92, fract(k.x * 9.0 + t * 0.6)) + step(0.92, fract(k.y * 9.0 - t * 0.4));
    col += mix(cb, vec3(1.0), 0.4) * frames * 0.15 * smoothstep(1.2, 0.2, length(uv));
    col += tesseract(uv, t, ca, cb);
    col += tunnel(uv, t, 3.0, ca * 0.4, cb * 0.4, 0.4) * 0.35;
    return col;
}

vec3 birth(vec2 uv, float t, vec3 ca, vec3 cb, float p) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float rays = pow(abs(sin(a * 13.0 + rvNoise3(vec3(cos(a) * 1.3, sin(a) * 1.3, t)) * 2.0)), 10.0) * exp(-r * 1.2);
    float neb = rvFbm2(uv * 2.5 + vec2(t * 0.1, 0.0), 6);
    vec3 col = mix(ca, cb, neb) * smoothstep(0.35, 0.9, neb) * 1.4 * (0.6 + p);
    col += mix(cb, vec3(1.0), 0.6) * rays * (1.0 + p * 2.0);
    col += vec3(1.0, 0.97, 0.92) * exp(-r * mix(6.0, 1.2, p)) * (0.5 + p * 1.5);
    return col;
}

void main() {
    float aspect = WormParams.x;
    float p = WormParams.y;
    float mode = WormParams.z;
    float intensity = WormParams.w;
    vec2 uv = (texCoord - 0.5) * vec2(aspect, 1.0) * 2.0;
    uv = rvRot(WormExtra.z) * uv;
    float t = RvTime;
    vec3 col;
    if (mode < 0.5) {
        float speed = 3.0 + WormExtra.y * 6.0 + p * 4.0;
        col = tunnel(uv, t, speed, ColorA, ColorB, 0.6);
        col += birth(uv, t, ColorA, ColorB, p) * smoothstep(0.6, 1.0, p) * 0.6;
    } else {
        // reality comes apart before the stages even begin: shattered shards, radial spaghettification, mirror folds
        float chaos = smoothstep(0.03, 0.3, p) * (1.0 - smoothstep(0.84, 0.95, p));
        vec2 cell = floor(uv * 2.6 + vec2(sin(t * 0.31), cos(t * 0.27)) * 2.0);
        float sh = rvHash12(cell);
        uv = mix(uv, rvRot((sh - 0.5) * 1.6) * uv * (1.0 + (sh - 0.5) * 0.5), step(0.4, sh) * chaos * 0.8);
        float rr0 = length(uv);
        uv = uv / max(rr0, 1e-3) * pow(max(rr0, 1e-3), 1.0 + 0.55 * chaos * sin(t * 1.7));
        uv = mix(uv, abs(uv), smoothstep(0.3, 0.5, p) * (1.0 - smoothstep(0.66, 0.74, p)) * (0.5 + 0.5 * sin(t * 2.3)));
        float w1 = 1.0 - smoothstep(0.12, 0.22, p);
        float w2 = smoothstep(0.12, 0.22, p) * (1.0 - smoothstep(0.4, 0.5, p));
        float w3 = smoothstep(0.4, 0.5, p) * (1.0 - smoothstep(0.66, 0.74, p));
        float w4 = smoothstep(0.66, 0.74, p);
        float speed = 2.0 + p * 9.0;
        col = vec3(0.0);
        if (w1 > 0.0) col += accretion(uv, t) * w1 + tunnel(uv, t, speed, vec3(1.0, 0.45, 0.1), vec3(1.0, 0.85, 0.6), 1.2) * w1 * smoothstep(0.0, 0.2, p) * 0.6;
        if (w2 > 0.0) col += tunnel(uv, t, speed, mix(vec3(0.15, 0.25, 1.0), ColorA, 0.3), mix(vec3(0.7, 0.3, 1.0), ColorB, 0.3), 0.8) * w2;
        if (w3 > 0.0) col += dimensional(uv, t, ColorA, ColorB) * w3;
        if (w4 > 0.0) col += birth(uv, t, ColorA, ColorB, (p - 0.66) / 0.34) * w4;
        // time echoes and an infinite tunnel nested in the centre of the picture
        col += tunnel(rvRot(1.3) * uv * 1.15, t - 0.35, speed, ColorB, ColorA, -0.9) * 0.3 * chaos;
        col += tunnel(rvRot(t * 0.6) * uv * 4.0, t * 1.7, speed * 2.0, vec3(1.0), ColorB, 1.5) * 0.35 * chaos * smoothstep(0.35, 0.0, length(uv));
        col = mix(col, rvHueShift(col, t * 0.5 + length(uv) * 3.0), chaos * 0.65);
        float inv = exp(-pow((p - 0.22) * 45.0, 2.0)) + exp(-pow((p - 0.5) * 45.0, 2.0)) + exp(-pow((p - 0.74) * 45.0, 2.0));
        col = mix(col, vec3(1.2) - col, clamp(inv, 0.0, 1.0) * 0.85);
    }
    float whiteout = smoothstep(0.86, 1.0, p);
    col = mix(col, vec3(1.0, 0.98, 0.95), whiteout);
    col += vec3(WormExtra.x);
    float vig = smoothstep(1.9, 0.6, length(uv));
    col *= 0.55 + 0.45 * vig;
    col = vec3(1.0) - exp(-col * 1.3);
    fragColor = vec4(col, intensity);
}
