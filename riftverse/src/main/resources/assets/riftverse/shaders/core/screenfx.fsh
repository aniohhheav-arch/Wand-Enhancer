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
