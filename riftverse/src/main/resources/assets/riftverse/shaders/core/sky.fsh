#version 150

#moj_import <riftverse:rv_common.glsl>

uniform mat4 InvViewProj;
uniform float RvTime;
uniform vec3 SkyTop;
uniform vec3 SkyHorizon;
uniform vec3 SkyGround;
uniform vec3 NebulaA;
uniform vec3 NebulaB;
uniform vec3 SunColor;
uniform vec3 SunDir;
// x stars, y nebula, z galaxy, w aurora
uniform vec4 SkyP1;
// x moons, y planet size, z rings, w black hole
uniform vec4 SkyP2;
// x sun size, y binary sun, z storm, w glitch
uniform vec4 SkyP3;
// x day factor, y seed, z lightning flash, w saturation
uniform vec4 SkyP4;
// x nexus mode, y unused...
uniform vec4 SkyP5;

in vec2 texCoord;
out vec4 fragColor;

vec3 seedDir(float s) {
    float a = rvHash11(s * 13.7 + SkyP4.y) * RV_TAU;
    float e = mix(0.25, 1.1, rvHash11(s * 7.3 + SkyP4.y * 0.37));
    return normalize(vec3(cos(a) * cos(e), sin(e), sin(a) * cos(e)));
}

void basis(vec3 n, out vec3 u, out vec3 v) {
    vec3 up = abs(n.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    u = normalize(cross(up, n));
    v = cross(n, u);
}

vec3 nebula(vec3 d) {
    vec3 q = d * 2.4 + vec3(0.0, RvTime * 0.004, SkyP4.y * 0.01);
    float n1 = rvFbm3(q, 5);
    float n2 = rvFbm3(q * 1.7 + vec3(5.2, 1.3, 7.7), 5);
    float dust = rvFbm3(q * 3.1 + vec3(2.0), 4);
    float m1 = smoothstep(0.42, 0.85, n1);
    float m2 = smoothstep(0.48, 0.9, n2);
    vec3 c = NebulaA * m1 * m1 * 1.6 + NebulaB * m2 * m2 * 1.4;
    c += mix(NebulaA, NebulaB, n2) * smoothstep(0.55, 0.95, n1 * n2 * 1.8) * 0.8;
    c *= 1.0 - smoothstep(0.45, 0.75, dust) * 0.65;
    return c;
}

vec3 galaxyBand(vec3 d) {
    vec3 g = normalize(vec3(0.62 + 0.15 * sin(SkyP4.y), 0.42, 0.66));
    float h = dot(d, g);
    float band = exp(-h * h * 26.0);
    float clump = rvFbm3(d * 7.0 + vec3(SkyP4.y), 5);
    float lanes = smoothstep(0.45, 0.65, rvFbm3(d * 11.0 + vec3(3.3), 4)) * exp(-h * h * 200.0);
    vec3 warm = vec3(1.0, 0.86, 0.72);
    vec3 col = mix(warm, NebulaB + 0.3, 0.35) * band * (0.25 + clump * 0.6);
    col *= 1.0 - lanes * 0.85;
    col += vec3(1.0) * rvStars(d, 900.0, 0.35 * band, RvTime) * 0.6;
    return col;
}

vec3 spiralGalaxy(vec3 d, vec3 c, float size, float tilt, float spin, vec3 tint) {
    float cd = dot(d, c);
    if (cd < 0.9) return vec3(0.0);
    vec3 u, v;
    basis(c, u, v);
    vec3 q = d - c * cd;
    vec2 p = vec2(dot(q, u), dot(q, v)) / size;
    p = rvRot(spin) * p;
    p.y /= tilt;
    float r = length(p);
    if (r > 1.6) return vec3(0.0);
    float a = atan(p.y, p.x);
    float arms = 0.5 + 0.5 * sin(2.0 * a - log(r + 0.02) * 5.0 + RvTime * 0.03);
    float disk = exp(-r * 2.6) * (0.35 + arms * 0.85);
    float core = exp(-r * 18.0) * 2.2;
    float dust = rvNoise2(p * 9.0) * exp(-r * 2.0);
    return tint * (disk * (0.7 + dust * 0.6)) + vec3(1.0, 0.9, 0.8) * core;
}

vec3 moon(vec3 d, vec3 c, float radius, vec3 tint, vec3 light) {
    float cd = dot(d, c);
    float edge = cos(radius);
    vec3 col = vec3(0.0);
    float glow = smoothstep(cos(radius * 3.0), edge, cd) * 0.12;
    if (cd < edge) return tint * glow;
    vec3 u, v;
    basis(c, u, v);
    vec3 q = d - c * cd;
    vec2 p = vec2(dot(q, u), dot(q, v)) / sin(radius);
    float z = sqrt(max(0.0, 1.0 - dot(p, p)));
    vec3 n = normalize(u * p.x + v * p.y - c * z);
    float lit = clamp(dot(-n, light) * 0.9 + 0.12, 0.0, 1.0);
    float crater = rvFbm2(p * 4.0 + c.xz * 9.0, 4);
    col = tint * lit * (0.65 + crater * 0.5);
    float aa = smoothstep(edge, edge + 0.0006, cd);
    return mix(tint * glow, col, aa);
}

vec4 planet(vec3 d, vec3 c, float angular, vec3 light) {
    float sinR = sin(angular);
    float b = dot(d, c);
    float disc = b * b - (1.0 - sinR * sinR);
    vec4 result = vec4(0.0);
    float tPlanet = 1e9;
    vec3 n = vec3(0.0);
    if (disc > 0.0) {
        tPlanet = b - sqrt(disc);
        vec3 hit = d * tPlanet;
        n = normalize(hit - c);
    }
    vec3 ringN = normalize(vec3(0.25, 1.0, 0.35 + 0.3 * sin(SkyP4.y * 2.0)));
    vec4 ring = vec4(0.0);
    if (SkyP2.z > 0.5) {
        float denom = dot(d, ringN);
        if (abs(denom) > 1e-4) {
            float tr = dot(c, ringN) / denom;
            vec3 hr = d * tr - c;
            float rr = length(hr) / sinR;
            if (tr > 0.0 && rr > 1.35 && rr < 2.5) {
                float fine = rvNoise2(vec2(rr * 120.0, 0.5));
                float bands = 0.35 + 0.65 * fine * (0.6 + 0.4 * sin(rr * 23.0));
                float gap = smoothstep(1.86, 1.9, rr) * (1.0 - smoothstep(1.95, 1.99, rr));
                float a = bands * (1.0 - gap) * smoothstep(1.35, 1.45, rr) * (1.0 - smoothstep(2.35, 2.5, rr)) * 0.75;
                vec3 rc = mix(vec3(0.85, 0.78, 0.65), NebulaB, 0.25) * (0.5 + 0.5 * a);
                float shade = (tPlanet < tr) ? 0.0 : 1.0;
                ring = vec4(rc * (0.35 + 0.65 * shade), a * 0.85);
                if (tPlanet < tr) ring.a = 0.0;
            }
        }
    }
    if (disc > 0.0) {
        float lat = dot(n, normalize(vec3(0.1, 1.0, 0.2)));
        float bands = rvFbm2(vec2(lat * 9.0, rvNoise2(n.xz * 3.0) * 2.0 + RvTime * 0.002), 5);
        vec3 surf = mix(mix(NebulaA, vec3(0.95, 0.88, 0.78), 0.55), mix(NebulaB, vec3(0.55, 0.5, 0.6), 0.5), bands) * 1.25;
        float lit = clamp(dot(n, light) * 1.1 + 0.08, 0.0, 1.0);
        lit = max(lit, 0.06);
        float rim = pow(1.0 - clamp(dot(n, -d), 0.0, 1.0), 3.0);
        vec3 col = surf * lit + mix(NebulaA, vec3(0.6, 0.8, 1.0), 0.5) * rim * (0.4 + lit);
        result = vec4(col, 1.0);
        if (ring.a > 0.0) result.rgb = mix(result.rgb, ring.rgb, ring.a);
        return result;
    }
    float halo = exp(-max(0.0, acos(clamp(b, -1.0, 1.0)) - angular) * 30.0) * 0.25;
    result = vec4(mix(NebulaA, vec3(0.6, 0.8, 1.0), 0.5) * halo, 0.0);
    if (ring.a > 0.0) result = vec4(mix(result.rgb, ring.rgb, ring.a), ring.a);
    return result;
}

vec3 aurora(vec3 d) {
    if (d.y < 0.03) return vec3(0.0);
    float a = atan(d.z, d.x);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float patchMask = smoothstep(0.38, 0.72, rvFbm3(vec3(cos(a) * 0.45 + fi * 3.1, sin(a) * 0.45, RvTime * 0.015 + fi), 4));
        float wave = sin(a * (4.0 + fi) + rvFbm3(vec3(cos(a) * 0.65 + fi, sin(a) * 0.65, RvTime * 0.04), 3) * 6.0 + RvTime * 0.08);
        float height = 0.22 + 0.12 * fi + 0.07 * wave;
        float curtain = exp(-max(0.0, d.y - height) * (14.0 - fi * 3.0)) * smoothstep(height - 0.18, height, d.y);
        float rays = 0.75 + 0.25 * sin(a * 55.0 + rvNoise3(vec3(cos(a) * 1.9, sin(a) * 1.9, RvTime * 0.25)) * 5.0);
        vec3 c = mix(vec3(0.15, 1.0, 0.5), vec3(0.7, 0.3, 1.0), clamp((d.y - height) * 6.0 + 0.3 + fi * 0.15, 0.0, 1.0));
        col += c * curtain * rays * patchMask * (0.6 - fi * 0.12);
    }
    return col;
}

vec3 stormClouds(vec3 d, vec3 base) {
    if (d.y < -0.05) return base;
    vec2 uv = d.xz / max(d.y + 0.08, 0.05) * 0.6;
    float t = RvTime * 0.02;
    float n = rvFbm2(uv * 0.8 + vec2(t, t * 0.6), 6);
    float n2 = rvFbm2(uv * 2.3 - vec2(t * 1.7, 0.0), 4);
    float cover = smoothstep(0.3, 0.7, n * 0.75 + n2 * 0.35) * SkyP3.z;
    vec3 cloud = mix(SkyHorizon * 0.18, SkyHorizon * 0.75 + 0.05, n * n2 * 1.6);
    cloud += vec3(0.75, 0.8, 1.0) * SkyP4.z * (0.5 + n);
    float fade = smoothstep(-0.05, 0.12, d.y);
    return mix(base, cloud, cover * fade);
}

vec3 skyAt(vec3 d) {
    float h = d.y;
    vec3 col = mix(SkyHorizon, SkyTop, smoothstep(-0.02, 0.55, h));
    col = mix(col, SkyGround, smoothstep(0.0, -0.35, h));
    float dayGlow = SkyP4.x;
    float horizonBand = exp(-abs(h) * 9.0);
    col += SkyHorizon * horizonBand * 0.12;

    float night = 1.0 - dayGlow * 0.85;
    float above = smoothstep(-0.08, 0.08, h);
    if (SkyP1.y > 0.0) col += nebula(d) * SkyP1.y * night * above;
    if (SkyP1.z > 0.0) col += galaxyBand(d) * SkyP1.z * night * above;
    if (SkyP1.x > 0.0) {
        float s1 = rvStars(d, 140.0, 0.10 * SkyP1.x, RvTime);
        float s2 = rvStars(d, 320.0, 0.16 * SkyP1.x, RvTime * 1.3) * 0.55;
        float s3 = rvStars(d, 60.0, 0.025 * SkyP1.x, RvTime * 0.7) * 1.6;
        vec3 starTint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.7), rvHash13(floor(d * 140.0)));
        col += (s1 + s2 + s3) * starTint * night * above;
    }
    if (SkyP1.z > 0.2) {
        col += spiralGalaxy(d, seedDir(3.0), 0.09, 0.45, 0.6, mix(NebulaA, vec3(0.9, 0.8, 1.0), 0.4)) * SkyP1.z * night * above;
        col += spiralGalaxy(d, seedDir(5.0), 0.05, 0.8, 2.1, mix(NebulaB, vec3(1.0), 0.3)) * SkyP1.z * night * above;
    }
    if (SkyP1.w > 0.0) col += aurora(d) * SkyP1.w * night;
    return col;
}

void main() {
    vec3 d = rvRayDir(InvViewProj, texCoord);
    vec3 sun = normalize(SunDir);

    // Gravitational lensing of the whole sky around a distant black hole.
    vec3 bhDir = seedDir(11.0);
    float bhRadius = 0.05;
    float lensTheta = 0.0;
    vec3 sd = d;
    if (SkyP2.w > 0.5) {
        float th = acos(clamp(dot(d, bhDir), -1.0, 1.0));
        lensTheta = th;
        if (th < 0.6 && th > 1e-4) {
            vec3 axis = normalize(cross(bhDir, d));
            float defl = bhRadius * bhRadius * 3.5 / th;
            sd = rvRotate(d, axis, min(defl, 1.4));
        }
    }

    // Glitched realities quantise the sky into broken blocks.
    if (SkyP3.w > 0.0) {
        float g = SkyP3.w;
        float row = floor(texCoord.y * 40.0);
        float jump = step(1.0 - g * 0.25, rvHash12(vec2(row, floor(RvTime * 6.0))));
        sd = normalize(sd + vec3(jump * 0.15 * (rvHash11(row) - 0.5), 0.0, 0.0));
    }

    vec3 col = skyAt(sd);

    // Sun(s)
    float sunSize = 0.035 * SkyP3.x;
    float sd1 = dot(d, sun);
    col += SunColor * (pow(max(sd1, 0.0), 900.0 / max(SkyP3.x, 0.3)) * 2.5 + pow(max(sd1, 0.0), 12.0) * 0.25 * (0.4 + SkyP4.x)) * smoothstep(-0.1, 0.05, d.y);
    col = mix(col, SunColor * 3.0, smoothstep(cos(sunSize), cos(sunSize) + 0.0004, sd1));
    if (SkyP3.y > 0.5) {
        vec3 sun2 = normalize(sun + vec3(0.18, 0.05, -0.12));
        float s2 = dot(d, sun2);
        vec3 c2 = mix(SunColor, vec3(0.6, 0.75, 1.0), 0.6);
        col += c2 * pow(max(s2, 0.0), 1400.0) * 2.0;
        col = mix(col, c2 * 2.5, smoothstep(cos(sunSize * 0.6), cos(sunSize * 0.6) + 0.0004, s2));
    }

    // Giant planet with rings
    if (SkyP2.y > 0.01) {
        vec3 pc = seedDir(7.0);
        pc = normalize(vec3(pc.x, max(0.18, pc.y * 0.5), pc.z));
        vec3 planetLight = normalize(sun * 0.5 - pc * 0.6 + vec3(0.0, 0.35, 0.0));
        vec4 pl = planet(d, pc, 0.05 + SkyP2.y * 0.33, planetLight);
        col = mix(col + pl.rgb * (1.0 - pl.a), pl.rgb, pl.a);
    }

    // Moons
    for (int i = 0; i < 5; i++) {
        if (float(i) >= SkyP2.x) break;
        vec3 mc = seedDir(20.0 + float(i) * 3.0);
        float mr = mix(0.025, 0.07, rvHash11(float(i) * 3.3 + SkyP4.y));
        vec3 tint = mix(vec3(0.92, 0.92, 0.96), mix(NebulaA, NebulaB, rvHash11(float(i))), 0.15 + 0.2 * rvHash11(float(i) + 9.0)) * 1.15;
        vec3 moonLight = sun.y > 0.0 ? normalize(sun + vec3(0.0, 0.3, 0.0)) : normalize(vec3(0.4, 0.7, 0.3) + mc * -0.6);
        vec3 m = moon(d, mc, mr, tint, moonLight);
        float inside = step(cos(mr), dot(d, mc));
        col = mix(col + m, m, inside);
    }

    // Distant black hole: shadow, photon ring and accretion glow.
    if (SkyP2.w > 0.5) {
        float th = lensTheta;
        float ring = exp(-abs(th - bhRadius * 1.5) * 220.0);
        vec3 axisN = normalize(vec3(0.15, 1.0, 0.25));
        vec3 u, v;
        basis(bhDir, u, v);
        vec3 q = d - bhDir * dot(d, bhDir);
        vec2 p = vec2(dot(q, u), dot(q, v)) / bhRadius;
        float diskR = length(vec2(p.x, p.y * 4.5));
        float disk = smoothstep(4.2, 2.2, diskR) * smoothstep(1.2, 1.8, diskR);
        float swirl = 0.6 + 0.4 * sin(atan(p.y * 4.5, p.x) * 6.0 - RvTime * 0.8 + diskR * 3.0);
        vec3 hot = vec3(1.0, 0.75, 0.45);
        col += hot * disk * swirl * 1.6 * (0.6 + 0.4 * sign(p.x) * 0.5 + 0.2);
        col += vec3(1.0, 0.9, 0.75) * ring * 2.0;
        col *= smoothstep(bhRadius * 0.98, bhRadius * 1.05, th);
    }

    if (SkyP3.z > 0.0) col = stormClouds(d, col);

    if (SkyP5.x > 0.5) {
        // The Nexus sky: countless universe-bubbles drifting in a golden void.
        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 5.0 : 11.0;
            vec3 dd = rvRotate(d, vec3(0.0, 1.0, 0.0), RvTime * (layer == 0 ? 0.004 : -0.007));
            vec3 cell = floor(dd * scale);
            vec3 f = fract(dd * scale) - 0.5;
            float h = rvHash13(cell + float(layer) * 31.0);
            if (h < 0.5) {
                vec3 off = (rvHash33(cell) - 0.5) * 0.35;
                float r = 0.12 + 0.22 * fract(h * 17.0);
                vec3 q = f - off;
                float dist = length(q);
                vec3 tint = 0.55 + 0.45 * cos(RV_TAU * (h * 3.0 + vec3(0.0, 0.33, 0.67)));
                float inside = smoothstep(r, r - 0.012, dist);
                float rim = inside * pow(dist / r, 6.0);
                float world = rvFbm3(q * 14.0 + cell, 3);
                vec3 interior = mix(tint * 0.15, tint * 0.6, world) * (1.0 - dist / r);
                vec3 irid = 0.5 + 0.5 * cos(RV_TAU * (dist / r * 2.0 + vec3(0.0, 0.33, 0.67) + RvTime * 0.05));
                col = mix(col, col * 0.6 + interior, inside * 0.85) + irid * rim * 0.9;
            }
        }
        float swirl = rvFbm3(vec3(d.x * 1.3 + RvTime * 0.01, d.y * 3.0, d.z * 1.3), 5);
        col += vec3(1.0, 0.75, 0.35) * smoothstep(0.55, 0.9, swirl) * 0.25 * smoothstep(-0.2, 0.4, d.y);
    }

    col = rvSaturate(col, SkyP4.w);
    col += SkyP4.z * vec3(0.25, 0.27, 0.35);
    fragColor = vec4(col, 1.0);
}
