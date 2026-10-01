#version 150

#define RV_PI 3.14159265359
#define RV_TAU 6.28318530718

float rvHash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float rvHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float rvHash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 rvHash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

vec2 rvHash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float rvNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = rvHash12(i);
    float b = rvHash12(i + vec2(1.0, 0.0));
    float c = rvHash12(i + vec2(0.0, 1.0));
    float d = rvHash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float rvNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = rvHash13(i);
    float n100 = rvHash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = rvHash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = rvHash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = rvHash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = rvHash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = rvHash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = rvHash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float rvFbm2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += rvNoise2(p) * amp;
        p = p * 2.03 + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum;
}

float rvFbm3(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += rvNoise3(p) * amp;
        p = p * 2.03 + vec3(17.1, 9.2, 4.7);
        amp *= 0.5;
    }
    return sum;
}

mat2 rvRot(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

vec3 rvRotate(vec3 v, vec3 axis, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

vec3 rvSaturate(vec3 c, float amount) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(l), c, amount);
}

vec3 rvHueShift(vec3 color, float shift) {
    const vec3 k = vec3(0.57735);
    float c = cos(shift);
    return color * c + cross(k, color) * sin(shift) + k * dot(k, color) * (1.0 - c);
}

/** Reconstructs a camera-relative world direction for a screen coordinate. */
vec3 rvRayDir(mat4 invViewProj, vec2 uv) {
    vec4 far = invViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 near = invViewProj * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    return normalize(far.xyz / far.w - near.xyz / near.w);
}

/** Camera-relative world position of a depth-buffer sample. */
vec3 rvWorldPos(mat4 invViewProj, vec2 uv, float depth) {
    vec4 p = invViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

/** Projects a camera-relative world position into [0,1] screen space; z < 0 means behind the camera. */
vec3 rvProject(mat4 viewProj, vec3 p) {
    vec4 clip = viewProj * vec4(p, 1.0);
    return vec3(clip.xy / clip.w * 0.5 + 0.5, clip.w);
}

/** Cheap procedural starfield on a direction. */
float rvStars(vec3 d, float scale, float density, float time) {
    vec3 p = d * scale;
    vec3 c = floor(p);
    vec3 f = fract(p) - 0.5;
    float h = rvHash13(c);
    if (h > density) return 0.0;
    vec3 off = (rvHash33(c) - 0.5) * 0.7;
    float dist = length(f - off);
    float twinkle = 0.65 + 0.35 * sin(time * (1.0 + h * 7.0) + h * 91.0);
    return smoothstep(0.09, 0.0, dist) * twinkle * (0.4 + 0.6 * fract(h * 37.0));
}
