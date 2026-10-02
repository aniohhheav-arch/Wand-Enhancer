#version 150

#moj_import <mysticarts:ma_common.glsl>

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
// x mirror-dimension kaleidoscope, y iridescent hue drift, z time-stop grade, w snap whiteout
uniform vec4 FxD;
uniform vec3 FlashColor;
uniform vec3 VignetteColor;
uniform vec2 ScreenSize;

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

    if (FxB.y > 0.0) {
        float g = FxB.y;
        float row = floor(uv.y * mix(18.0, 60.0, rvHash11(floor(t * 9.0))));
        float hit = step(1.0 - g * 0.45, rvHash12(vec2(row, floor(t * 14.0))));
        uv.x += hit * (rvHash12(vec2(row * 1.7, floor(t * 20.0))) - 0.5) * 0.12 * g;
        float block = step(1.0 - g * 0.12, rvHash12(floor(uv * vec2(14.0, 9.0)) + floor(t * 11.0)));
        uv += block * (rvHash22(floor(uv * 8.0) + t) - 0.5) * 0.04;
    }

    if (FxD.x > 0.0) {
        // mirror dimension: crystalline facets and folded, reflected edges
        vec2 facet = floor(uv * vec2(14.0 * aspect, 14.0));
        uv += (rvHash22(facet + floor(t * 0.5)) - 0.5) * 0.012 * FxD.x;
        float e = 0.12 * FxD.x;
        if (uv.x < e) uv.x = 2.0 * e - uv.x;
        if (uv.x > 1.0 - e) uv.x = 2.0 * (1.0 - e) - uv.x;
        if (uv.y < e) uv.y = 2.0 * e - uv.y;
        if (uv.y > 1.0 - e) uv.y = 2.0 * (1.0 - e) - uv.y;
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

    if (FxB.y > 0.0) {
        float scan = 0.92 + 0.08 * sin(texCoord.y * ScreenSize.y * 1.4);
        col *= mix(1.0, scan, FxB.y);
        float inv = step(1.0 - FxB.y * 0.05, rvHash11(floor(t * 17.0)));
        col = mix(col, vec3(1.0) - col, inv * 0.8);
    }

    col = rvSaturate(col, FxC.w);
    if (FxD.y > 0.0) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, rvHueShift(col, fract(t * 0.05 + lum * 0.6 + uv.x * 0.3)), FxD.y * 0.6);
    }
    if (FxD.z > 0.0) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 frozen = vec3(lum) * vec3(0.72, 1.12, 0.82);
        col = mix(col, frozen, FxD.z * 0.8);
        float pulse = 0.5 + 0.5 * sin(length((texCoord - 0.5) * vec2(aspect, 1.0)) * 40.0 - t * 3.0);
        col += vec3(0.05, 0.25, 0.1) * pulse * FxD.z * 0.15;
    }
    if (FxD.w > 0.0) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum) * vec3(1.15, 1.0, 0.82), FxD.w * 0.7);
    }
    col = mix(col, col * FxTint.rgb * 1.6, FxTint.a);
    float vig = smoothstep(0.35, 1.25, length((texCoord - 0.5) * vec2(aspect, 1.0)) * 1.4);
    col = mix(col, VignetteColor, vig * FxA.w);
    col += FlashColor * FxC.z;
    if (FxB.w > 0.0) col += (rvHash12(texCoord * ScreenSize + fract(t * 7.3) * 100.0) - 0.5) * FxB.w * 0.12;
    fragColor = vec4(col, 1.0);
}
