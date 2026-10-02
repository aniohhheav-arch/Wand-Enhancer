#version 150

#moj_import <mysticarts:ma_common.glsl>

uniform float RvTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// UV packing (see MaDraw.runeQuad): u = local u + seed * 2, v = local v + pattern * 2.
// Local uv is 0..1 across the quad; p is -1..1 from the centre.

const float PI = 3.14159265;

float band(float x, float c, float w) {
    return smoothstep(w, 0.0, abs(x - c));
}

float line2(vec2 p, vec2 a, vec2 b, float w) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return smoothstep(w, 0.0, length(pa - ba * h));
}

// Pseudo-glyphs: a 3x4 grid of strokes chosen by hash, drawn in a cell.
float glyph(vec2 cell, float id) {
    float g = 0.0;
    vec2 q = cell * vec2(3.0, 4.0);
    vec2 iq = floor(q);
    vec2 fq = fract(q);
    float h = rvHash12(iq + id * 7.31);
    if (h > 0.45) g = max(g, smoothstep(0.18, 0.05, abs(fq.x - 0.5)) * step(0.2, fq.y) * step(fq.y, 0.8));
    float h2 = rvHash12(iq + id * 3.17 + 11.0);
    if (h2 > 0.55) g = max(g, smoothstep(0.18, 0.05, abs(fq.y - 0.5)) * step(0.2, fq.x) * step(fq.x, 0.8));
    return g;
}

float runeRing(vec2 p, float r0, float r1, float count, float speed, float seed) {
    float r = length(p);
    if (r < r0 || r > r1) return 0.0;
    float a = atan(p.y, p.x) / (2.0 * PI) + 0.5 + RvTime * speed;
    float idx = floor(a * count);
    vec2 cell = vec2(fract(a * count), (r - r0) / (r1 - r0));
    cell.x = (cell.x - 0.1) / 0.8;
    if (cell.x < 0.0 || cell.x > 1.0) return 0.0;
    return glyph(cell, idx + seed * 13.0);
}

float polygon(vec2 p, float n, float r, float rot, float w) {
    float g = 0.0;
    for (int i = 0; i < 12; i++) {
        if (float(i) >= n) break;
        float a0 = rot + float(i) / n * 2.0 * PI;
        float a1 = rot + float(i + 1) / n * 2.0 * PI;
        g = max(g, line2(p, vec2(cos(a0), sin(a0)) * r, vec2(cos(a1), sin(a1)) * r, w));
    }
    return g;
}

float starPolygon(vec2 p, float n, float step_, float r, float rot, float w) {
    float g = 0.0;
    for (int i = 0; i < 12; i++) {
        if (float(i) >= n) break;
        float a0 = rot + float(i) / n * 2.0 * PI;
        float a1 = rot + (float(i) + step_) / n * 2.0 * PI;
        g = max(g, line2(p, vec2(cos(a0), sin(a0)) * r, vec2(cos(a1), sin(a1)) * r, w));
    }
    return g;
}

void main() {
    float pattern = floor(texCoord0.y * 0.5);
    float seed = floor(texCoord0.x * 0.5);
    vec2 uv = vec2(texCoord0.x - seed * 2.0, texCoord0.y - pattern * 2.0);
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float a = atan(p.y, p.x);
    float t = RvTime + seed * 1.37;
    float v = 0.0;
    float core = 0.0;

    if (pattern < 0.5) {
        // 0: sling ring portal rim - a ring of churning sparks
        float n = rvFbm2(vec2(a * 3.0 + t * 7.0, r * 6.0 - t * 2.0), 3);
        float ring = band(r, 0.9 + (n - 0.5) * 0.08, 0.06 + n * 0.05);
        float spark = step(0.86, rvHash12(floor(vec2(a * 40.0 + t * 30.0, r * 30.0)))) * band(r, 0.92, 0.12);
        float inner = band(r, 0.78, 0.015) * 0.6 + runeRing(p, 0.8, 0.88, 28.0, 0.15, seed) * 0.7;
        v = ring * (0.8 + n) + spark * 1.4 + inner;
        core = ring;
    } else if (pattern < 1.5) {
        // 1: eldritch shield mandala
        float rot = t * 0.4;
        v += band(r, 0.96, 0.025) + band(r, 0.9, 0.012);
        v += runeRing(p, 0.74, 0.88, 22.0, 0.05, seed);
        v += band(r, 0.72, 0.012) + band(r, 0.48, 0.012);
        vec2 pr = mat2(cos(-rot), -sin(-rot), sin(-rot), cos(-rot)) * p;
        v += starPolygon(pr, 6.0, 2.0, 0.7, 0.0, 0.012);
        v += polygon(pr, 6.0, 0.7, 0.0, 0.01) * 0.7;
        v += runeRing(p, 0.3, 0.44, 12.0, -0.12, seed + 3.0) * 0.8;
        float spokes = smoothstep(0.03, 0.0, abs(sin(a * 6.0 + rot * 2.0))) * step(0.48, r) * step(r, 0.72);
        v += spokes * 0.6;
        v += smoothstep(0.98, 0.0, r) * 0.18;
        core = band(r, 0.96, 0.025);
    } else if (pattern < 2.5) {
        // 2: casting sigil
        float rot = t * 0.6;
        vec2 pr = mat2(cos(rot), -sin(rot), sin(rot), cos(rot)) * p;
        v += band(r, 0.95, 0.02) + band(r, 0.85, 0.012);
        v += runeRing(p, 0.86, 0.94, 30.0, -0.08, seed);
        v += polygon(pr, 4.0, 0.84, 0.0, 0.012) + polygon(pr, 4.0, 0.84, PI / 4.0, 0.012);
        v += band(r, 0.42, 0.015) + polygon(pr * -1.0, 3.0, 0.4, 0.0, 0.012) * 0.8;
        v += smoothstep(0.25, 0.0, r) * 0.5;
        core = band(r, 0.95, 0.02);
    } else if (pattern < 3.5) {
        // 3: clock face (time)
        v += band(r, 0.95, 0.02) + band(r, 0.82, 0.01) + band(r, 0.25, 0.01);
        float ticks = smoothstep(0.04, 0.0, abs(fract(a / (2.0 * PI) * 12.0 + 0.5) - 0.5)) * step(0.82, r) * step(r, 0.95);
        float minor = smoothstep(0.015, 0.0, abs(fract(a / (2.0 * PI) * 60.0 + 0.5) - 0.5)) * step(0.88, r) * step(r, 0.95);
        v += ticks + minor * 0.5;
        v += runeRing(p, 0.6, 0.78, 12.0, 0.02, seed) * 0.6;
        float ha = t * 0.35, ma = t * 4.2;
        v += line2(p, vec2(0.0), vec2(sin(ha), cos(ha)) * 0.45, 0.025);
        v += line2(p, vec2(0.0), vec2(sin(ma), cos(ma)) * 0.75, 0.015);
        v += smoothstep(0.06, 0.0, r);
        core = band(r, 0.95, 0.02);
    } else if (pattern < 4.5) {
        // 4: binding band - runes running along u
        float across = abs(uv.y - 0.5) * 2.0;
        float edge = smoothstep(0.25, 0.0, abs(across - 0.85));
        vec2 cell = vec2(fract(uv.x * 6.0 + t * 0.6), uv.y);
        float g = glyph(vec2(cell.x, (cell.y - 0.2) / 0.6), floor(uv.x * 6.0 + t * 0.6) + seed);
        v = edge + g * step(0.2, uv.y) * step(uv.y, 0.8) + smoothstep(1.0, 0.0, across) * 0.15;
        core = edge;
    } else if (pattern < 5.5) {
        // 5: ripple disc - expanding rings (shield impacts, pulses)
        float ph = fract(t * 1.5);
        v = band(r, ph, 0.08) * (1.0 - ph) + band(r, fract(ph + 0.5), 0.06) * (1.0 - fract(ph + 0.5)) * 0.6;
        v += smoothstep(1.0, 0.0, r) * 0.25;
        v *= step(r, 1.0);
        core = v;
    } else if (pattern < 6.5) {
        // 6: spatial lattice (prisons, space barriers)
        vec2 g = abs(fract(uv * 4.0 + vec2(t * 0.2, 0.0)) - 0.5);
        float grid = smoothstep(0.06, 0.0, min(g.x, g.y) - 0.0);
        float frame = smoothstep(0.06, 0.0, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
        float shimmer = rvNoise2(uv * 6.0 + t);
        v = grid * (0.5 + shimmer * 0.5) + frame * 1.2 + 0.08;
        core = frame;
    } else if (pattern < 7.5) {
        // 7: portal interior swirl
        float sw = a + r * 4.0 - t * 2.5;
        float n = rvFbm2(vec2(cos(sw), sin(sw)) * r * 3.0 + t * 0.3, 4);
        v = (0.35 + n * 0.8) * smoothstep(1.0, 0.6, r);
        v += smoothstep(0.35, 0.0, r) * 0.6;
        core = smoothstep(0.3, 0.0, r);
    } else if (pattern < 8.5) {
        // 8: soft halo
        v = pow(max(0.0, 1.0 - r), 2.2);
        core = pow(max(0.0, 1.0 - r), 6.0);
    } else {
        // 9: radiant star (infinity stones, the snap)
        float rays = pow(abs(cos(a * 3.0 + t * 0.5)), 24.0) * smoothstep(1.0, 0.1, r);
        float rays2 = pow(abs(cos(a * 2.0 - t * 0.3 + 0.5)), 40.0) * smoothstep(1.0, 0.2, r) * 0.6;
        v = rays + rays2 + pow(max(0.0, 1.0 - r), 3.0) * 1.2;
        core = pow(max(0.0, 1.0 - r * 1.5), 4.0);
    }

    v = clamp(v, 0.0, 3.0);
    vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.75, 0.0, 0.8));
    float alpha = vertexColor.a;
    fragColor = vec4(col * v * alpha, clamp(v * alpha, 0.0, 1.0));
}
