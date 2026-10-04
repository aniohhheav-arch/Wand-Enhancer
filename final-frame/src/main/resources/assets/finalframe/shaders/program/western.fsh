#version 150

// Final Frame cinematic grade: desaturation, warm western tint, contrast, vignette, muzzle flash and film grain.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform float Saturation;
uniform float Warmth;
uniform float Contrast;
uniform float Vignette;
uniform float Flash;
uniform float Grain;
uniform float Time;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(luma), c, Saturation);

    vec3 warm = c * vec3(1.10, 0.99, 0.80) + vec3(0.035, 0.018, 0.0);
    c = mix(c, warm, Warmth);

    c = (c - 0.5) * Contrast + 0.5;

    vec2 d = (texCoord - 0.5) * vec2(1.3, 1.0);
    c *= 1.0 - Vignette * smoothstep(0.28, 0.9, length(d));

    c += (hash(texCoord * 913.0 + Time) - 0.5) * Grain;
    c = mix(c, vec3(1.0, 0.96, 0.86), Flash);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
