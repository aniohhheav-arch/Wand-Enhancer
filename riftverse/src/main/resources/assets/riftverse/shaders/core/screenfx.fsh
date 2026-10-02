#version 150

#moj_import <riftverse:rv_common.glsl>

uniform sampler2D Sampler0;
uniform float RvTime;
// x aberration, y warp, z zoom blur, w vignette
uniform vec4 FxA;
// x swirl, y glitch, z dream wave, w grain
uniform vec4 FxB;
// xy effect centre (uv), z flash, w saturation
uniform vec4 FxC;
// rgb grade tint, a tint strength
uniform vec4 FxTint;
uniform vec3 FlashColor;
uniform vec3 VignetteColor;
uniform vec2 ScreenSize;
// x kaleidoscope, y recursion (droste), z gravitational lens + Einstein ring, w hue cycling
uniform vec4 FxD;
// x ghost echoes, y spaghettification stretch, z mirror fold, w inversion
uniform vec4 FxE;
// x art style A, y art style B (0 none), z blend 0..1, w transformation (0..1, peaks mid-transition)
uniform vec4 FxArt;

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 tap(vec2 uv) {
    return texture(Sampler0, clamp(uv, 0.001, 0.999)).rgb;
}

float edgeAt(vec2 uv, vec2 px) {
    float tl = lum(tap(uv + px * vec2(-1.0, 1.0)));
    float t = lum(tap(uv + px * vec2(0.0, 1.0)));
    float tr = lum(tap(uv + px * vec2(1.0, 1.0)));
    float l = lum(tap(uv + px * vec2(-1.0, 0.0)));
    float r = lum(tap(uv + px * vec2(1.0, 0.0)));
    float bl = lum(tap(uv + px * vec2(-1.0, -1.0)));
    float b = lum(tap(uv + px * vec2(0.0, -1.0)));
    float br = lum(tap(uv + px * vec2(1.0, -1.0)));
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    return sqrt(gx * gx + gy * gy);
}

vec3 posterize(vec3 c, float levels) {
    return floor(c * levels + 0.5) / levels;
}

float paperGrain(vec2 uv, float t) {
    return 0.92 + 0.08 * rvFbm2(uv * vec2(ScreenSize.x, ScreenSize.y) * 0.02, 3) + (rvHash12(floor(uv * ScreenSize * 0.5)) - 0.5) * 0.04;
}

vec3 kuwahara(vec2 uv, vec2 px) {
    vec3 best = vec3(0.0);
    float bestVar = 1e9;
    for (int q = 0; q < 4; q++) {
        vec2 dir = vec2((q & 1) == 0 ? -1.0 : 1.0, (q & 2) == 0 ? -1.0 : 1.0);
        vec3 m = vec3(0.0);
        vec3 m2 = vec3(0.0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                vec3 c = tap(uv + px * dir * vec2(float(i), float(j)) * 1.6);
                m += c;
                m2 += c * c;
            }
        }
        m /= 9.0;
        vec3 v = m2 / 9.0 - m * m;
        float var = v.r + v.g + v.b;
        if (var < bestVar) {
            bestVar = var;
            best = m;
        }
    }
    return best;
}

vec3 artStyle(float sid, vec2 uv, vec3 col, float t) {
    int id = int(sid + 0.5);
    vec2 px = 1.0 / max(ScreenSize, vec2(1.0));
    float l = lum(col);
    float e = edgeAt(uv, px * 1.2);
    if (id == 1) {
        // hand-drawn: pencil outlines that re-draw themselves, cross-hatched shading on paper
        float jitter = floor(t * 6.0);
        float e2 = edgeAt(uv + (rvHash22(floor(uv * 90.0) + jitter) - 0.5) * px * 2.0, px * 1.5);
        float lines = smoothstep(0.12, 0.35, e2);
        vec2 sp = uv * ScreenSize;
        float h1 = step(0.5, fract((sp.x + sp.y) * 0.11));
        float h2 = step(0.5, fract((sp.x - sp.y) * 0.11));
        float hatch = (l < 0.45 ? h1 : 1.0) * (l < 0.25 ? h2 : 1.0);
        float unfinished = smoothstep(0.6, 0.75, rvFbm2(uv * 3.0 + jitter * 0.03, 3));
        vec3 paper = vec3(0.96, 0.93, 0.86) * paperGrain(uv, t);
        vec3 ink = vec3(0.12, 0.11, 0.14);
        vec3 c = mix(paper, ink, max(lines, (1.0 - hatch) * 0.55) * (1.0 - unfinished * 0.8));
        return mix(c, c * mix(vec3(1.0), col * 1.4 + 0.3, 0.12), 1.0);
    }
    if (id == 2) {
        // black & white film: hard contrast, grain, flicker; red alone survives
        float g = smoothstep(0.08, 0.85, l);
        g = g * g * (3.0 - 2.0 * g);
        float flicker = 0.93 + 0.07 * rvHash11(floor(t * 18.0));
        vec3 c = vec3(g) * flicker + (rvHash12(uv * ScreenSize + fract(t) * 91.0) - 0.5) * 0.12;
        float red = smoothstep(0.15, 0.35, col.r - max(col.g, col.b));
        c = mix(c, vec3(0.85, 0.08, 0.1) * (0.4 + l), red);
        float scratch = step(0.997, rvHash11(floor(uv.x * 300.0) + floor(t * 12.0) * 13.0));
        return c + scratch * 0.25;
    }
    if (id == 3) {
        // psychedelic: flowing hue, breathing warp, supersaturation
        vec2 w = uv + vec2(sin(uv.y * 14.0 + t * 1.3), cos(uv.x * 11.0 - t * 1.1)) * 0.006;
        vec3 c = tap(w);
        c = rvHueShift(c, t * 0.4 + uv.x * 2.0 + uv.y * 1.5 + lum(c) * 3.0);
        return rvSaturate(c, 2.2) * 1.1;
    }
    if (id == 4 || id == 24) {
        // comic: flat inks, heavy outlines, halftone shadows (24 adds panel gutters)
        vec3 c = posterize(rvSaturate(col, 1.5), 4.0);
        vec2 sp = uv * ScreenSize / 5.0;
        float dotd = length(fract(sp) - 0.5);
        float halftone = step(dotd, (1.0 - l) * 0.55) * step(l, 0.45);
        c = mix(c, c * 0.35, halftone);
        c = mix(c, vec3(0.04), smoothstep(0.18, 0.32, e));
        if (id == 24) {
            vec2 cell = uv * vec2(3.0, 2.0);
            vec2 fp = fract(cell);
            float gut = step(fp.x, 0.012) + step(0.988, fp.x) + step(fp.y, 0.02) + step(0.98, fp.y);
            c = mix(c, vec3(0.97), clamp(gut, 0.0, 1.0));
        }
        return c;
    }
    if (id == 5) {
        // pixel art: chunky pixels, limited palette
        float block = 5.0 + 3.0 * step(0.5, fract(floor(uv.y * 4.0) * 0.5));
        vec2 q = (floor(uv * ScreenSize / block) + 0.5) * block / ScreenSize;
        return posterize(tap(q), 5.0);
    }
    if (id == 6) {
        // painted: brush-flattened colour with canvas weave
        vec3 c = kuwahara(uv, px * 2.0);
        float canvas = 0.94 + 0.06 * sin(uv.x * ScreenSize.x * 0.9) * sin(uv.y * ScreenSize.y * 0.9);
        return rvSaturate(c, 1.25) * canvas;
    }
    if (id == 7) {
        // paper craft: cut-out flats with drop shadows along the folds
        vec3 c = posterize(col, 5.0) * paperGrain(uv, t);
        float sh = smoothstep(0.1, 0.3, edgeAt(uv - px * 3.0, px * 1.5));
        return mix(c, c * 0.55, sh * 0.7) + smoothstep(0.15, 0.3, e) * 0.08;
    }
    if (id == 8) {
        // neon cyber: the world goes dark and every edge burns
        vec3 glow = mix(vec3(0.0, 0.95, 1.0), vec3(1.0, 0.15, 0.85), 0.5 + 0.5 * sin(uv.y * 8.0 + t));
        vec3 c = col * 0.25 + glow * smoothstep(0.08, 0.3, e) * 1.6;
        return c + glow * 0.04 * step(0.5, fract(uv.y * ScreenSize.y * 0.25));
    }
    if (id == 9) {
        // dream: soft bloom, drifting warm and cold light
        vec3 blur = (tap(uv + px * vec2(4.0, 0.0)) + tap(uv - px * vec2(4.0, 0.0)) + tap(uv + px * vec2(0.0, 4.0)) + tap(uv - px * vec2(0.0, 4.0))) * 0.25;
        vec3 c = mix(col, blur, 0.5) + max(blur - 0.55, 0.0) * 1.2;
        vec3 tint = mix(vec3(1.08, 0.95, 0.9), vec3(0.88, 0.95, 1.12), 0.5 + 0.5 * sin(t * 0.3));
        return c * tint;
    }
    if (id == 10) {
        // glitch: corrupted blocks, torn channels, scrambled tiles
        vec2 blk = floor(uv * vec2(24.0, 14.0));
        float h = rvHash12(blk + floor(t * 7.0));
        vec2 off = step(0.86, h) * (rvHash22(blk + t) - 0.5) * 0.08;
        vec3 c;
        c.r = tap(uv + off + px * vec2(4.0, 0.0)).r;
        c.g = tap(uv + off).g;
        c.b = tap(uv + off - px * vec2(4.0, 0.0)).b;
        if (h > 0.97) c = tap(fract(uv * 3.0 + h));
        return c;
    }
    if (id == 11) {
        // blueprint: white linework on drafting blue with a measured grid
        vec3 c = vec3(0.07, 0.24, 0.55);
        vec2 g = fract(uv * ScreenSize / 24.0);
        float grid = step(g.x, 0.04) + step(g.y, 0.04);
        c += vec3(0.12, 0.2, 0.35) * clamp(grid, 0.0, 1.0);
        return mix(c, vec3(0.92, 0.96, 1.0), smoothstep(0.1, 0.3, e));
    }
    if (id == 12) {
        // claymation: soft modelled lumps that twitch at stop-motion frame rate
        vec2 jitter = (rvHash22(vec2(floor(t * 8.0))) - 0.5) * px * 2.0;
        vec3 c = kuwahara(uv + jitter, px * 1.5);
        c = posterize(c, 7.0);
        return rvSaturate(c, 1.35) * (0.9 + 0.1 * smoothstep(0.0, 0.2, e));
    }
    if (id == 13) {
        // wireframe: only the edges of things exist
        return vec3(0.25, 1.0, 0.9) * smoothstep(0.06, 0.22, e) * 1.4 + vec3(0.0, 0.03, 0.05);
    }
    if (id == 14) {
        // ink: the world in sumi ink bleeding into rice paper
        float bleed = rvFbm2(uv * 40.0, 3) * 0.12;
        float ink = smoothstep(0.42 + bleed, 0.32 + bleed, l) + smoothstep(0.15, 0.4, e);
        return mix(vec3(0.95, 0.93, 0.88) * paperGrain(uv, t), vec3(0.03, 0.03, 0.05), clamp(ink, 0.0, 1.0));
    }
    if (id == 15) {
        // glass: everything refracts, edges flare
        vec2 n = (vec2(rvFbm2(uv * 6.0 + t * 0.05, 3), rvFbm2(uv * 6.0 - t * 0.04, 3)) - 0.5) * 0.02;
        vec3 c = rvSaturate(tap(uv + n), 0.35) * vec3(0.85, 0.95, 1.05);
        return c + vec3(0.8, 0.95, 1.0) * smoothstep(0.1, 0.4, e) * 0.6;
    }
    if (id == 16) {
        // watercolour: pigment pools and darkened wet edges on cold-press paper
        vec3 c = kuwahara(uv + (vec2(rvFbm2(uv * 9.0, 3), rvFbm2(uv * 9.0 + 4.0, 3)) - 0.5) * 0.01, px * 2.5);
        c = mix(vec3(1.0), c, 0.85);
        c *= 1.0 - smoothstep(0.1, 0.35, e) * 0.35;
        return c * paperGrain(uv, t) * (0.95 + rvFbm2(uv * 18.0, 3) * 0.1);
    }
    if (id == 17) {
        return vec3(1.0) - col;
    }
    if (id == 18) {
        // silhouette: shapes cut in black against a burning sky
        float s = smoothstep(0.48, 0.38, l);
        vec3 sky = mix(vec3(1.0, 0.55, 0.25), vec3(0.45, 0.15, 0.45), uv.y);
        return mix(sky, vec3(0.02), s);
    }
    if (id == 19) {
        // VHS: tracking tear, scanlines, chroma bleed, a band of tape noise
        float line = floor(uv.y * ScreenSize.y);
        float tear = step(0.985, rvHash11(line * 0.13 + floor(t * 20.0))) * 0.02;
        float band = smoothstep(0.04, 0.0, abs(fract(uv.y + t * 0.12) - 0.5));
        vec2 q = uv + vec2(tear + band * 0.01 * sin(line), 0.0);
        vec3 c = vec3(tap(q + px * vec2(3.0, 0.0)).r, tap(q).g, tap(q - px * vec2(3.0, 0.0)).b);
        c *= 0.9 + 0.1 * sin(uv.y * ScreenSize.y * 1.5);
        c += (rvHash12(uv * ScreenSize + t) - 0.5) * 0.08 + band * 0.15;
        return rvSaturate(c, 0.8) * vec3(1.02, 0.98, 1.05);
    }
    if (id == 20) {
        // astral: wherever it is dark, the cosmos shows through the world
        vec2 g = uv * ScreenSize / 3.0;
        float star = step(0.993, rvHash12(floor(g))) * (0.6 + 0.4 * sin(t * 3.0 + rvHash12(floor(g)) * 40.0));
        vec3 neb = mix(vec3(0.15, 0.05, 0.35), vec3(0.05, 0.3, 0.5), rvFbm2(uv * 3.0 + t * 0.01, 5)) * rvFbm2(uv * 5.0 - t * 0.01, 4);
        float dark = smoothstep(0.35, 0.1, l);
        return mix(col, neb + vec3(star), dark) + smoothstep(0.15, 0.4, e) * vec3(0.5, 0.6, 1.0) * 0.4;
    }
    if (id == 21) {
        // origami: folded triangular facets of flat colour
        vec2 sp = uv * ScreenSize / 22.0;
        vec2 f = fract(sp);
        vec2 cell = floor(sp) + (f.x > f.y ? vec2(0.66, 0.33) : vec2(0.33, 0.66));
        vec3 c = posterize(tap(cell * 22.0 / ScreenSize), 6.0);
        float fold = f.x > f.y ? 1.05 : 0.88;
        return c * fold * paperGrain(uv, t);
    }
    if (id == 22) {
        // x-ray: inverted luminance in cold blue, hidden edges glowing
        float v = 1.0 - l;
        return vec3(0.15, 0.55, 1.0) * v * 1.1 + vec3(0.7, 0.9, 1.0) * smoothstep(0.1, 0.35, e);
    }
    if (id == 23) {
        // fractal: the view repeats into itself in mirrored tiles
        vec2 q = uv - 0.5;
        float k = 1.0 + 0.5 * sin(t * 0.2);
        q = abs(fract(q * k * 2.0 + 0.5) - 0.5) * 2.0;
        vec3 c = tap(q * 0.5 + 0.25);
        return mix(col, c, 0.55);
    }
    return col;
}

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 c = FxC.xy;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    float t = RvTime;

    if (FxB.z > 0.0) {
        uv += vec2(sin(uv.y * 22.0 + t * 1.9), cos(uv.x * 17.0 + t * 1.6)) * 0.0045 * FxB.z;
    }

    vec2 d = uv - c;
    vec2 da = d * vec2(aspect, 1.0);
    float r = length(da);

    if (FxB.x != 0.0) {
        float ang = FxB.x * exp(-r * 2.2);
        d = rvRot(ang) * d;
    }
    if (FxA.y != 0.0) {
        d *= 1.0 + FxA.y * dot(da, da);
    }
    uv = c + d;

    // ---- mind-bending layer (black holes): lensing, stretch, folds, kaleidoscope
    vec2 q = (uv - c) * vec2(aspect, 1.0);
    float rq = length(q);
    if (FxD.z > 0.0) {
        float R = 0.16 + 0.04 * sin(t * 1.3);
        q *= 1.0 + FxD.z * (R * R) / (rq * rq + 0.0025);
    }
    if (FxE.y > 0.0) {
        float stretch = 1.0 + FxE.y * (rq - 0.25) * (1.6 + 0.6 * sin(t * 2.1));
        q *= max(stretch, 0.15);
    }
    if (FxE.z > 0.0) {
        vec2 f = vec2(abs(q.x), q.y * sign(sin(t * 0.9) + 0.0001));
        q = mix(q, f, FxE.z);
    }
    if (FxD.x > 0.0) {
        float seg = RV_TAU / 6.0;
        float a = atan(q.y, q.x) + t * 0.35;
        a = mod(a, seg);
        a = abs(a - seg * 0.5);
        vec2 kq = vec2(cos(a), sin(a)) * length(q);
        q = mix(q, kq, FxD.x);
    }
    uv = c + q / vec2(aspect, 1.0);

    if (FxB.y > 0.0) {
        float g = FxB.y;
        float row = floor(uv.y * mix(18.0, 60.0, rvHash11(floor(t * 9.0))));
        float hit = step(1.0 - g * 0.45, rvHash12(vec2(row, floor(t * 14.0))));
        uv.x += hit * (rvHash12(vec2(row * 1.7, floor(t * 20.0))) - 0.5) * 0.12 * g;
        float block = step(1.0 - g * 0.12, rvHash12(floor(uv * vec2(14.0, 9.0)) + floor(t * 11.0)));
        uv += block * (rvHash22(floor(uv * 8.0) + t) - 0.5) * 0.04;
    }

    vec2 dir = uv - c;
    float ab = FxA.x * (0.35 + length(dir * vec2(aspect, 1.0)));
    vec3 col = vec3(0.0);
    float zoom = FxA.z;
    const int SAMPLES = 10;
    float total = 0.0;
    for (int i = 0; i < SAMPLES; i++) {
        float fi = float(i) / float(SAMPLES - 1);
        float s = 1.0 - zoom * fi * 0.12;
        float w = 1.0 - fi * 0.5 * step(0.001, zoom);
        vec2 su = c + dir * s;
        vec3 smp;
        smp.r = texture(Sampler0, clamp(su + dir * ab * 0.02, 0.001, 0.999)).r;
        smp.g = texture(Sampler0, clamp(su, 0.001, 0.999)).g;
        smp.b = texture(Sampler0, clamp(su - dir * ab * 0.02, 0.001, 0.999)).b;
        col += smp * w;
        total += w;
        if (zoom <= 0.001) break;
    }
    col /= total;

    vec2 cq = (uv - c) * vec2(aspect, 1.0);
    float cr = length(cq);
    if (FxE.x > 0.0) {
        // time ghosts: the world smeared into rotated, scaled copies of itself
        vec3 g1 = texture(Sampler0, clamp(c + rvRot(0.22 + 0.1 * sin(t)) * (uv - c) * 1.12, 0.001, 0.999)).rgb;
        vec3 g2 = texture(Sampler0, clamp(c + rvRot(-0.3) * (uv - c) * 0.86, 0.001, 0.999)).rgb;
        vec3 g3 = texture(Sampler0, clamp(c + rvRot(0.6 * sin(t * 0.7)) * (uv - c) * 1.35, 0.001, 0.999)).rgb;
        col = mix(col, (col + g1 + g2 + g3) * 0.25 * vec3(1.05, 0.95, 1.1), FxE.x);
    }
    if (FxD.y > 0.0) {
        // recursion: the whole picture repeats inside its own centre, spinning, forever
        vec3 inner = vec3(0.0);
        float wsum = 0.0;
        for (int i = 1; i <= 3; i++) {
            float sc = pow(3.2, float(i));
            vec2 iu = c + rvRot(t * 0.4 * float(i)) * (uv - c) * sc;
            float wgt = smoothstep(0.32 / pow(3.2, float(i - 1)), 0.02, cr);
            inner += texture(Sampler0, fract(iu)).rgb * wgt;
            wsum += wgt;
        }
        if (wsum > 0.0) col = mix(col, inner / wsum, FxD.y * clamp(wsum, 0.0, 1.0));
    }
    if (FxD.z > 0.0) {
        float R = 0.16 + 0.04 * sin(t * 1.3);
        float ring = exp(-pow((cr - R) * 55.0, 2.0));
        col += vec3(1.0, 0.75, 0.45) * ring * FxD.z * 1.6;
        col *= mix(1.0, smoothstep(R * 0.55, R * 0.9, cr), FxD.z);
    }
    if (FxArt.z > 0.0) {
        vec3 styled = col;
        if (FxArt.x > 0.5) styled = artStyle(FxArt.x, uv, styled, t);
        if (FxArt.y > 0.5) styled = mix(styled, artStyle(FxArt.y, uv, styled, t), 0.6);
        // transformation: colour drains, the picture glitches and redraws itself between styles
        float tr = FxArt.w;
        if (tr > 0.0) {
            float scan = smoothstep(0.0, 0.02, abs(fract(texCoord.y * 3.0 - t * 1.5) - 0.5));
            styled = mix(vec3(lum(styled)), styled, 1.0 - tr * 0.8) * mix(1.0, scan, tr);
        }
        col = mix(col, styled, clamp(FxArt.z, 0.0, 1.0));
    }
    if (FxD.w > 0.0) col = mix(col, rvHueShift(col, t * 0.45 + cr * 4.0), FxD.w);
    if (FxE.w > 0.0) col = mix(col, vec3(1.0) - col, clamp(FxE.w, 0.0, 1.0));

    if (FxB.y > 0.0) {
        float scan = 0.92 + 0.08 * sin(texCoord.y * ScreenSize.y * 1.4);
        col *= mix(1.0, scan, FxB.y);
        float inv = step(1.0 - FxB.y * 0.05, rvHash11(floor(t * 17.0)));
        col = mix(col, vec3(1.0) - col, inv * 0.8);
    }

    col = rvSaturate(col, FxC.w);
    col = mix(col, col * FxTint.rgb * 1.6, FxTint.a);
    float vig = smoothstep(0.35, 1.25, length((texCoord - 0.5) * vec2(aspect, 1.0)) * 1.4);
    col = mix(col, VignetteColor, vig * FxA.w);
    col += FlashColor * FxC.z;
    if (FxB.w > 0.0) col += (rvHash12(texCoord * ScreenSize + fract(t * 7.3) * 100.0) - 0.5) * FxB.w * 0.12;
    fragColor = vec4(col, 1.0);
}
