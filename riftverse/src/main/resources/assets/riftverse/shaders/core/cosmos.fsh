#version 150

#moj_import <riftverse:rv_common.glsl>

uniform float RvTime;
uniform vec3 ColorA;
uniform vec3 ColorB;
// x aspect, y focus pulse, z vortex strength, w brightness
uniform vec4 CosmosParams;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = (texCoord - 0.5) * vec2(CosmosParams.x, 1.0) * 2.0;
    float t = RvTime;
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float vortex = CosmosParams.z;
    vec2 swirled = rvRot(vortex * 1.8 / (r + 0.35) + t * 0.02) * uv;

    float n1 = rvFbm2(swirled * 1.6 + vec2(t * 0.015, -t * 0.01), 6);
    float n2 = rvFbm2(swirled * 2.7 - vec2(t * 0.02, t * 0.013) + 4.0, 5);
    vec3 col = vec3(0.012, 0.008, 0.03);
    col += ColorA * smoothstep(0.45, 0.85, n1) * 0.7;
    col += ColorB * smoothstep(0.5, 0.9, n2) * 0.55;
    col *= 0.6 + 0.4 * rvFbm2(swirled * 5.0, 3);

    vec3 dir = normalize(vec3(uv * 0.8, 1.0));
    dir = rvRotate(dir, vec3(0.0, 1.0, 0.0), t * 0.01);
    col += vec3(rvStars(dir, 90.0, 0.14, t) + rvStars(dir, 210.0, 0.2, t * 1.3) * 0.6) * 0.9;

    float ring = exp(-abs(r - 0.55 - 0.02 * sin(t)) * 30.0) * vortex;
    col += mix(ColorA, ColorB, 0.5 + 0.5 * sin(a * 3.0 + t)) * ring * 0.35;
    col += mix(ColorB, vec3(1.0), 0.5) * exp(-r * 5.0) * vortex * (0.4 + 0.2 * CosmosParams.y);

    col *= CosmosParams.w;
    col *= smoothstep(2.2, 0.4, r) * 0.6 + 0.4;
    fragColor = vec4(col, 1.0);
}
