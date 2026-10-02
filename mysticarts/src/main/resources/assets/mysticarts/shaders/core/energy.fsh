#version 150

#moj_import <mysticarts:ma_common.glsl>

uniform float RvTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// UV convention: u runs along the beam (in blocks), v runs across it (0..1).
void main() {
    float u = texCoord0.x;
    float v = texCoord0.y;
    float across = (v - 0.5) * 2.0;
    float core = exp(-across * across * 6.0);
    float hot = exp(-across * across * 40.0);
    float flow = rvNoise2(vec2(u * 3.0 - RvTime * 9.0, v * 4.0));
    float flow2 = rvNoise2(vec2(u * 9.0 - RvTime * 15.0, v * 9.0 + 3.0));
    float pulse = 0.85 + 0.15 * sin(u * 2.0 - RvTime * 12.0);
    float intensity = (core * (0.55 + flow * 0.6 + flow2 * 0.3) + hot * 1.2) * pulse;
    vec3 col = mix(vertexColor.rgb, vec3(1.0), hot * 0.8);
    fragColor = vec4(col * intensity, clamp(intensity * vertexColor.a, 0.0, 1.0));
}
