#version 150

#moj_import <riftverse:rv_common.glsl>

uniform float RvTime;
uniform vec3 ColorA;
uniform vec3 ColorB;
// x aspect, y progress 0..1, z overlay alpha, w seed
uniform vec4 GenParams;

in vec2 texCoord;
out vec4 fragColor;

float band(float p, float a, float b) {
    return smoothstep(a, a + 0.015, p) * (1.0 - smoothstep(b - 0.015, b, p));
}

vec3 nebula(vec2 uv, float t, vec3 ca, vec3 cb, float seed) {
    float n1 = rvFbm2(uv * 1.3 + vec2(seed, t * 0.02), 6);
    float n2 = rvFbm2(uv * 2.7 - vec2(t * 0.03, seed * 0.7), 5);
    vec3 col = mix(ca, cb, n2) * smoothstep(0.45, 0.95, n1) * 1.4;
    col += mix(cb, vec3(1.0, 0.6, 0.9), n1) * smoothstep(0.65, 1.0, n2) * 0.6;
    return col;
}

vec3 stars(vec2 uv, float density, float t) {
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float sc = 60.0 + float(layer) * 70.0;
        vec2 g = uv * sc + float(layer) * 13.1;
        vec2 cell = floor(g);
        vec2 f = fract(g) - 0.5;
        float h = rvHash12(cell);
        if (h > 1.0 - density) {
            vec2 off = (rvHash22(cell) - 0.5) * 0.6;
            float d = length(f - off);
            float tw = 0.6 + 0.4 * sin(t * (2.0 + h * 5.0) + h * 40.0);
            col += vec3(0.9, 0.95, 1.0) * exp(-d * d * 220.0) * tw * (0.5 + h);
        }
    }
    return col;
}

vec3 galaxy(vec2 uv, vec2 c, float size, float rot, vec3 tint, float t) {
    vec2 q = rvRot(rot + t * 0.05) * (uv - c) / size;
    q.y /= 0.55;
    float r = length(q);
    float a = atan(q.y, q.x);
    float arms = 0.5 + 0.5 * sin(2.0 * a - log(r + 0.03) * 6.0);
    float dust = rvFbm2(q * 4.0 + t * 0.02, 4);
    vec3 col = tint * exp(-r * 2.6) * (0.35 + arms * 0.9) * (0.6 + dust * 0.6);
    col += vec3(1.0, 0.95, 0.85) * exp(-r * 18.0) * 1.5;
    return col;
}

// ---------------------------------------------------------------- the planet

vec3 planet(vec2 uv, float stage, float t, vec3 ca, vec3 cb, float seed, out float cover) {
    // stage: 0 molten -> 1 crust -> 2 oceans -> 3 forests -> 4 atmosphere/clouds -> 5 complete
    float R = 0.42;
    float r = length(uv);
    cover = smoothstep(R + 0.004, R - 0.004, r);
    vec3 col = vec3(0.0);
    vec3 light = normalize(vec3(-0.6, 0.45, 0.65));
    if (r < R) {
        float z = sqrt(R * R - r * r);
        vec3 n = normalize(vec3(uv, z));
        float spin = t * 0.08;
        vec3 p = vec3(n.x * cos(spin) + n.z * sin(spin), n.y, -n.x * sin(spin) + n.z * cos(spin));
        float h = rvFbm3(p * 2.2 + seed, 6);
        float mountains = pow(max(rvFbm3(p * 6.0 + seed * 1.7, 4) - 0.45, 0.0), 1.3) * 2.0;
        h += mountains * smoothstep(1.0, 1.6, stage) * 0.35;
        float lava = rvFbm3(p * 5.0 + t * 0.15, 4);
        vec3 molten = mix(vec3(0.25, 0.02, 0.0), vec3(1.0, 0.45, 0.05), smoothstep(0.45, 0.8, lava)) * 1.6;
        vec3 rock = mix(vec3(0.22, 0.18, 0.16), vec3(0.55, 0.5, 0.45), smoothstep(0.4, 0.8, h));
        rock = mix(rock, vec3(0.92, 0.94, 1.0), smoothstep(0.78, 0.86, h) * smoothstep(1.0, 1.6, stage));
        float crust = smoothstep(0.6, 1.4, stage);
        vec3 surf = mix(molten, rock, crust);
        // cracks of lava linger as the crust cools
        surf += vec3(1.0, 0.4, 0.05) * smoothstep(0.02, 0.0, abs(lava - 0.55)) * (1.0 - smoothstep(1.0, 1.8, stage)) * 1.4;
        float sea = 0.5;
        float water = smoothstep(1.6, 2.3, stage) * smoothstep(sea + 0.01, sea - 0.01, h);
        vec3 ocean = mix(vec3(0.02, 0.1, 0.3), mix(vec3(0.1, 0.45, 0.65), ca, 0.25), smoothstep(sea - 0.15, sea, h));
        surf = mix(surf, ocean, water);
        // rivers carve down from the highlands once the oceans exist
        float river = smoothstep(0.012, 0.0, abs(rvFbm3(p * 3.4 + seed * 2.3, 3) - 0.5)) * step(sea + 0.01, h) * smoothstep(2.1, 2.6, stage);
        surf = mix(surf, vec3(0.15, 0.4, 0.7), river * 0.8);
        // forests spread over the temperate land
        float lat = abs(p.y);
        float forest = smoothstep(2.6, 3.3, stage) * step(sea + 0.01, h) * smoothstep(0.75, 0.55, h) * smoothstep(0.85, 0.5, lat)
                * smoothstep(0.42, 0.6, rvFbm3(p * 7.0 + seed, 3));
        surf = mix(surf, mix(vec3(0.08, 0.3, 0.08), cb * 0.5 + vec3(0.05, 0.25, 0.05), 0.3), forest);
        float diff = max(dot(n, light), 0.0);
        col = surf * (0.08 + diff * 1.1);
        col += molten * (1.0 - crust) * 0.6;
        // specular glint on the oceans
        vec3 hv = normalize(light + vec3(0.0, 0.0, 1.0));
        col += vec3(1.0) * pow(max(dot(n, hv), 0.0), 60.0) * water * 0.8;
        // clouds
        float clouds = smoothstep(0.55, 0.75, rvFbm3(p * 3.0 + vec3(t * 0.03, 0.0, seed), 5)) * smoothstep(3.6, 4.3, stage);
        col = mix(col, vec3(1.0) * (0.2 + diff), clouds * 0.85);
        // city lights / life sparkles on the night side
        float life = step(0.985, rvHash12(floor(p.xy * 180.0 + seed))) * step(sea + 0.01, h) * smoothstep(4.4, 5.0, stage) * (1.0 - diff);
        col += mix(vec3(1.0, 0.85, 0.5), cb, 0.4) * life * 1.5;
    }
    // atmosphere rim
    float atmo = smoothstep(3.5, 4.2, stage);
    float rim = exp(-pow((r - R) * 40.0, 2.0)) + smoothstep(R + 0.06, R, r) * smoothstep(R - 0.05, R, r) * 0.6;
    col += mix(vec3(0.3, 0.6, 1.0), ca, 0.35) * rim * atmo * 1.3;
    // moons orbiting
    float moonsOn = smoothstep(4.4, 4.9, stage);
    for (int i = 0; i < 2; i++) {
        float ang = t * (0.25 + float(i) * 0.12) + float(i) * 2.4;
        vec2 mp = vec2(cos(ang) * (0.62 + float(i) * 0.12), sin(ang) * (0.16 + float(i) * 0.05));
        float mr = 0.04 - float(i) * 0.012;
        float md = length(uv - mp);
        float front = step(0.0, sin(ang));
        if (md < mr && (front > 0.5 || r > R)) {
            float mz = sqrt(mr * mr - md * md);
            vec3 mn = normalize(vec3(uv - mp, mz));
            float cr = rvFbm2((uv - mp) * 60.0 + float(i) * 9.0, 3);
            col = mix(col, vec3(0.75, 0.74, 0.72) * (0.15 + max(dot(mn, light), 0.0)) * (0.8 + cr * 0.3), moonsOn);
            cover = max(cover, moonsOn);
        }
    }
    // a ring of debris and floating islands catching the light
    float ringK = smoothstep(4.0, 4.6, stage);
    vec2 rq = vec2(uv.x, uv.y / 0.22);
    float rr = length(rq);
    float ring = smoothstep(0.03, 0.0, abs(rr - 0.62)) * step(0.0, uv.y + (r < R ? 1.0 : 0.0) * -10.0);
    float islands = step(0.93, rvHash12(floor(vec2(atan(rq.y, rq.x) * 40.0, rr * 30.0) + seed))) * smoothstep(0.05, 0.0, abs(rr - 0.62));
    col += mix(ca, vec3(1.0), 0.5) * (ring * 0.35 + islands * 1.2) * ringK * (r > R || uv.y < 0.0 ? 1.0 : 0.0);
    return col;
}

void main() {
    float aspect = GenParams.x;
    float p = GenParams.y;
    float alpha = GenParams.z;
    float seed = GenParams.w;
    float t = RvTime;
    vec2 uv = (texCoord - 0.5) * vec2(aspect, 1.0) * 2.0;
    vec3 col = vec3(0.0);

    // STAGE 1 (0.00-0.10) absolute void; a single point of light appears and breathes
    float pointK = smoothstep(0.05, 0.09, p) * (1.0 - smoothstep(0.17, 0.19, p));
    float pulse = 0.7 + 0.3 * sin(t * mix(2.0, 9.0, smoothstep(0.1, 0.17, p)));
    float r0 = length(uv);
    col += vec3(1.0, 0.98, 0.95) * exp(-r0 * mix(160.0, 40.0, smoothstep(0.1, 0.17, p))) * 3.0 * pointK * pulse;
    col += vec3(0.8, 0.85, 1.0) * exp(-r0 * 12.0) * 0.15 * pointK * pulse;

    // STAGE 2 (0.17-0.32) primordial expansion: shockwaves, a ballooning sphere of energy, nebulae ignite
    float ex = smoothstep(0.17, 0.30, p);
    float exOn = band(p, 0.17, 0.36);
    if (exOn > 0.0) {
        float R = ex * 2.6;
        float sphere = smoothstep(R, R * 0.6, r0);
        vec3 energy = mix(vec3(1.0), mix(ColorA, ColorB, 0.5 + 0.5 * sin(r0 * 8.0 - t * 3.0)), smoothstep(0.0, 0.8, ex));
        float turb = rvFbm2(uv * 3.0 + vec2(t * 0.4, -t * 0.3), 5);
        col += energy * sphere * (0.6 + turb * 0.8) * (1.0 - ex * 0.5) * exOn;
        for (int i = 0; i < 4; i++) {
            float wr = fract(ex * 1.6 - float(i) * 0.22) * 2.8;
            col += mix(vec3(1.0), ColorB, float(i) / 3.0) * exp(-pow((r0 - wr) * 30.0, 2.0)) * (1.0 - wr / 2.8) * 1.4 * exOn;
        }
        float rays = pow(abs(sin(atan(uv.y, uv.x) * 11.0 + turb * 2.0)), 14.0) * exp(-r0 * 1.2);
        col += vec3(1.0, 0.95, 0.85) * rays * 1.5 * exOn * (1.0 - ex);
        col += nebula(uv * (1.5 - ex * 0.5), t, ColorA, ColorB, seed) * smoothstep(0.22, 0.32, p) * exOn;
        col += vec3(1.0) * exp(-pow((p - 0.185) * 90.0, 2.0)) * 3.0; // the flash of creation
    }

    // THE BIG BANG (0.13-0.27): the point implodes under its own weight, everything goes white, then a fireball of
    // primordial plasma tears outward while inflation streaks race past the camera
    float implode = smoothstep(0.13, 0.168, p) * (1.0 - step(0.168, p));
    if (implode > 0.0) {
        float ang = atan(uv.y, uv.x);
        float infall = pow(abs(sin(ang * 23.0 + r0 * 30.0 + t * 12.0)), 30.0) * smoothstep(1.4, 0.05, r0) * implode;
        col += vec3(0.85, 0.9, 1.0) * infall * 1.5;
        col *= 1.0 - implode * smoothstep(0.05, 0.6, r0) * 0.9;
        col += vec3(1.0) * exp(-r0 * mix(40.0, 400.0, implode)) * 6.0 * implode;
    }
    float bang = smoothstep(0.1675, 0.1700, p) * (1.0 - smoothstep(0.172, 0.205, p));
    col = mix(col, vec3(1.6, 1.55, 1.45), bang);
    float fireOn = band(p, 0.168, 0.27);
    if (fireOn > 0.0) {
        float f = smoothstep(0.168, 0.25, p);
        float FR = 0.15 + f * 3.2;
        float ang = atan(uv.y, uv.x);
        float turb = rvFbm2(vec2(ang * 3.0, r0 * 4.0 - t * 2.5) + seed, 6);
        float edge = FR * (0.85 + turb * 0.3);
        float inside = smoothstep(edge, edge * 0.55, r0);
        float heat = clamp(1.0 - r0 / max(edge, 0.001), 0.0, 1.0);
        vec3 plasma = mix(vec3(0.6, 0.05, 0.02), vec3(1.0, 0.55, 0.1), smoothstep(0.0, 0.5, heat));
        plasma = mix(plasma, vec3(1.2, 1.15, 1.0), smoothstep(0.55, 1.0, heat));
        plasma = mix(plasma, mix(ColorA, ColorB, turb), f * 0.5);
        col += plasma * inside * (0.8 + turb) * fireOn * 1.3;
        // the shock front, split into colours like light through a prism
        for (int k = 0; k < 3; k++) {
            float sr = edge * (1.0 + float(k) * 0.015);
            vec3 chan = k == 0 ? vec3(1.0, 0.2, 0.2) : k == 1 ? vec3(0.2, 1.0, 0.3) : vec3(0.3, 0.4, 1.0);
            col += chan * exp(-pow((r0 - sr) * 45.0, 2.0)) * 1.6 * fireOn;
        }
        // inflation: streaks of matter flung outward faster than light
        vec2 cell = vec2(floor(ang * 60.0), 0.0);
        float lane = rvHash12(cell + seed);
        float sp = fract(lane * 7.0 + f * (2.0 + lane * 3.0));
        float streak = smoothstep(0.02, 0.0, abs(fract(ang * 60.0 / 6.2831853) - 0.5) - 0.45) * exp(-pow((r0 - sp * 3.0) * 6.0, 2.0)) * step(0.55, lane);
        col += vec3(1.0, 0.9, 0.8) * streak * 2.0 * fireOn;
    }

    // STAGE 3 (0.30-0.52) the birth of space: we fly through forming galaxies, stars and nebulae
    float spaceOn = band(p, 0.30, 0.56);
    if (spaceOn > 0.0) {
        float fly = (p - 0.30) / 0.26;
        vec2 cam = uv / (1.0 + fly * 1.6) + vec2(fly * 0.4, -fly * 0.15);
        vec3 sc = nebula(cam * 1.1, t, ColorA, ColorB, seed) * 0.9 + stars(cam, mix(0.05, 0.35, smoothstep(0.30, 0.42, p)), t);
        for (int g = 0; g < 6; g++) {
            vec2 gc = (rvHash22(vec2(float(g), seed)) - 0.5) * vec2(3.2, 1.8);
            float gs = 0.18 + rvHash11(float(g) + seed) * 0.35;
            float born = smoothstep(0.32 + float(g) * 0.025, 0.40 + float(g) * 0.025, p);
            vec3 tint = mix(ColorA, ColorB, rvHash11(float(g) * 3.1 + seed));
            sc += galaxy(cam, gc, gs * born, float(g) * 1.3, tint, t) * born;
        }
        col += sc * spaceOn;
    }

    // STAGE 4 (0.52-0.78) planetary formation, staged: molten, crust & mountains, oceans & rivers, forests, sky
    float planetOn = band(p, 0.52, 0.86);
    if (planetOn > 0.0) {
        float stage = clamp((p - 0.54) / 0.24 * 5.0, 0.0, 5.0);
        float approach = smoothstep(0.52, 0.60, p);
        float dive = smoothstep(0.78, 0.86, p);
        vec2 pu = uv / mix(0.25, 1.0, approach) / mix(1.0, 6.0, dive * dive);
        float cover;
        vec3 bg = stars(uv * 0.7, 0.2, t) + nebula(uv * 0.6, t, ColorA, ColorB, seed) * 0.35;
        vec3 pl = planet(pu, stage, t, ColorA, ColorB, seed, cover);
        // formation sparks: matter raining onto the young world
        float sparks = step(0.996, rvHash12(floor(uv * 120.0) + floor(t * 6.0))) * (1.0 - smoothstep(2.0, 4.0, stage));
        col += (mix(bg, pl, cover) + pl * (1.0 - cover) + vec3(1.0, 0.8, 0.5) * sparks) * planetOn;
    }

    // STAGE 5 (0.78-0.88) first life: the descent through cloud and atmosphere, glowing motes of life rising
    float lifeOn = band(p, 0.78, 0.92);
    if (lifeOn > 0.0) {
        float k = smoothstep(0.78, 0.88, p);
        vec3 skyc = mix(vec3(0.35, 0.6, 1.0), ColorA, 0.3);
        vec3 sky = mix(skyc * 1.1, vec3(1.0, 0.95, 0.9), smoothstep(-0.2, 0.9, -uv.y)) ;
        float clouds = smoothstep(0.45, 0.75, rvFbm2(uv * 2.0 + vec2(0.0, t * 0.5 + k * 4.0), 5));
        vec3 c = mix(sky, vec3(1.0), clouds * (1.0 - k) * 0.9);
        float motes = step(0.992, rvHash12(floor(uv * 90.0 + vec2(0.0, -t * 3.0)))) * k;
        c += mix(ColorB, vec3(0.6, 1.0, 0.6), 0.5) * motes * 2.0;
        col = mix(col, c, smoothstep(0.80, 0.84, p) * lifeOn);
    }

    // STAGE 6 (0.86-1.00) the gateway: a ring of light opens and the new world shows through it
    float gateOn = smoothstep(0.86, 0.90, p);
    if (gateOn > 0.0) {
        float open = smoothstep(0.86, 0.96, p) * 1.6;
        float a = atan(uv.y, uv.x);
        float ring = exp(-pow((r0 - open) * 18.0, 2.0)) * (0.8 + 0.4 * sin(a * 12.0 + t * 6.0));
        col += mix(ColorA, vec3(1.0), 0.4) * ring * 2.0 * gateOn;
        col += mix(ColorB, vec3(1.0), 0.6) * pow(abs(sin(a * 7.0 - t * 2.0)), 20.0) * smoothstep(open + 0.6, open, r0) * 0.6 * gateOn;
    }

    // alpha: opaque for the whole sequence, the inside of the gateway reveals the real world at the end
    float hole = smoothstep(0.88, 0.97, p) * smoothstep(smoothstep(0.86, 0.96, p) * 1.6, smoothstep(0.86, 0.96, p) * 1.6 - 0.25, r0);
    float a = alpha * (1.0 - hole) * (1.0 - smoothstep(0.97, 1.0, p));
    col += vec3(1.0) * exp(-pow((p - 0.965) * 60.0, 2.0)) * 1.2;
    col = vec3(1.0) - exp(-col * 1.25);
    fragColor = vec4(col, a);
}
