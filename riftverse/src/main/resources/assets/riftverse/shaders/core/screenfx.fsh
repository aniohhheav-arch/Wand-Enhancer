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
    col = mix(col, col * FxTint.rgb * 1.6, FxTint.a);
    float vig = smoothstep(0.35, 1.25, length((texCoord - 0.5) * vec2(aspect, 1.0)) * 1.4);
    col = mix(col, VignetteColor, vig * FxA.w);
    col += FlashColor * FxC.z;
    if (FxB.w > 0.0) col += (rvHash12(texCoord * ScreenSize + fract(t * 7.3) * 100.0) - 0.5) * FxB.w * 0.12;
    fragColor = vec4(col, 1.0);
}
