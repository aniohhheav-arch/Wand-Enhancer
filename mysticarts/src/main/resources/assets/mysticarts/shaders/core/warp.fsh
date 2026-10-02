#version 150

#moj_import <mysticarts:ma_common.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float RvTime;
uniform vec2 ScreenSize;
// screen-space ellipse: centre and the two semi-axes, all in uv units
uniform vec2 WarpCenter;
uniform vec2 WarpU;
uniform vec2 WarpV;
// x mode, y strength, z progress 0..1, w depth of the centre (0..1, window depth)
uniform vec4 WarpParams;
uniform vec3 WarpColor;

in vec2 texCoord;
out vec4 fragColor;

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, 0.001, 0.999)).rgb;
}

void main() {
    vec2 uv = texCoord;
    vec2 d = uv - WarpCenter;
    mat2 basis = mat2(WarpU, WarpV);
    float det = determinant(basis);
    if (abs(det) < 1e-7) discard;
    vec2 local = inverse(basis) * d;
    float r = length(local);
    float mode = WarpParams.x;
    float strength = WarpParams.y;
    float progress = WarpParams.z;
    if (r > 1.25) discard;
    float sceneDepth = texture(Sampler1, uv).r;
    if (sceneDepth < WarpParams.w - 0.00002) discard;

    vec3 col;
    float t = RvTime;
    if (mode < 0.5) {
        // portal interior: a swirling lens into elsewhere, tinted with the destination's sky
        if (r > 1.0) discard;
        float k = 1.0 - r;
        float ang = k * k * 3.5 * strength + sin(t * 1.3 + r * 6.0) * 0.15 * k;
        vec2 rd = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * d;
        vec2 suv = WarpCenter + rd * (0.35 + 0.65 * r);
        vec2 ab = normalize(d + 1e-5) * 0.006 * k;
        col = vec3(scene(suv + ab).r, scene(suv).g, scene(suv - ab).b);
        float swirl = rvFbm2(vec2(atan(local.y, local.x) * 2.0 + t * 1.5, r * 5.0 - t * 2.0), 3);
        col = mix(col, WarpColor * (0.7 + swirl * 0.6), clamp(k * 0.75 + swirl * 0.2, 0.0, 0.85));
        col += WarpColor * smoothstep(0.75, 1.0, r) * 0.6;
    } else if (mode < 1.5) {
        // shock ripple: a refracting ring at the wave front (the radius grows on the CPU side)
        float ring = 0.92;
        float w = 0.12;
        float x = (r - ring) / w;
        float wave = exp(-x * x) * (1.0 - progress);
        vec2 offset = normalize(d + 1e-5) * wave * 0.03 * strength;
        col = vec3(scene(uv + offset * 1.2).r, scene(uv + offset).g, scene(uv + offset * 0.8).b);
        col += WarpColor * wave * 0.25 * strength;
    } else if (mode < 2.5) {
        // heat haze over charging power
        if (r > 1.0) discard;
        float k = smoothstep(1.0, 0.2, r);
        vec2 n = vec2(rvNoise2(uv * 40.0 + vec2(0.0, t * 3.0)), rvNoise2(uv * 40.0 + vec2(7.0, t * 3.4))) - 0.5;
        col = scene(uv + n * 0.012 * strength * k);
        col += WarpColor * k * 0.08 * strength;
    } else if (mode < 3.5) {
        // temporal ripple: slow concentric waves, green-shifted and desaturated
        float k = smoothstep(1.25, 0.6, r);
        float wave = sin(r * 24.0 - t * 4.0) * 0.5 + 0.5;
        vec2 offset = normalize(d + 1e-5) * (wave - 0.5) * 0.008 * strength * k;
        col = scene(uv + offset);
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum) * vec3(0.7, 1.15, 0.8), 0.55 * k * strength);
        col += WarpColor * wave * k * 0.06 * strength;
    } else {
        // gravitational pinch (rifts, gravity wells, ultimates)
        float k = smoothstep(1.25, 0.0, r);
        vec2 suv = WarpCenter + d * (1.0 - k * k * 0.45 * strength);
        float ang = k * k * 1.2 * strength;
        suv = WarpCenter + mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * (suv - WarpCenter);
        col = scene(suv);
        col = mix(col, WarpColor, k * k * 0.25 * strength);
    }
    fragColor = vec4(col, 1.0);
}
