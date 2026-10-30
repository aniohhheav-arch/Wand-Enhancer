#version 150

// Reality Breach screen-space distortion.
// Intensity: steady background interference (0..1). Spike: momentary glitch bursts (0..1).
// Focus: screen position the warp radiates from (the rift when one is in view, else the centre).

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Clock;
uniform float Intensity;
uniform float Spike;
uniform float FocusX;
uniform float FocusY;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec2 Focus = vec2(FocusX, FocusY);
    float t = Clock;
    vec2 c = uv - Focus;
    float r = length(c * vec2(OutSize.x / OutSize.y, 1.0));

    // spatial warping: ripples travelling out of the focus, plus a slow lens pull toward it
    uv += c * sin(r * 26.0 - t * 4.0) * 0.006 * Intensity * smoothstep(1.2, 0.0, r);
    uv -= c * 0.04 * Intensity * smoothstep(0.6, 0.0, r);
    uv.x += sin(uv.y * 55.0 + t * 7.0) * 0.0012 * Intensity;

    // slice tearing: horizontal bands jump sideways during glitches
    float band = floor(uv.y * 28.0);
    float tear = step(1.0 - 0.22 * Spike - 0.015 * Intensity, hash(vec2(band, floor(t * 9.0))));
    uv.x += (hash(vec2(band, floor(t * 23.0))) - 0.5) * 0.12 * tear;

    // RGB separation, strongest away from the focus
    vec2 shift = normalize(c + 1e-5) * (0.0025 + 0.006 * r) * (Intensity + 3.5 * Spike);
    vec3 col;
    col.r = texture(DiffuseSampler, uv + shift).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - shift).b;

    // grain
    col += (hash(uv * OutSize + t) - 0.5) * 0.05 * (Intensity + Spike);

    // lighting inconsistencies: brief exposure drops and colour casts
    float flicker = step(0.6, hash(vec2(floor(t * 6.0), 7.0))) * Spike;
    col *= 1.0 - 0.25 * flicker;
    col += vec3(0.06, -0.02, 0.05) * flicker;

    // corrupted blocks: rectangles of inverted, channel-swapped picture
    vec2 cell = floor(uv * vec2(18.0, 10.0));
    float corrupt = step(0.992 - 0.06 * Spike, hash(cell + floor(t * 5.0))) * step(0.02, Spike + Intensity * 0.1);
    col = mix(col, vec3(1.0) - col.brg, corrupt);

    // vignette
    float edge = length((texCoord - 0.5) * vec2(1.25, 1.0));
    col *= 1.0 - smoothstep(0.45, 0.95, edge) * (0.25 * Intensity + 0.3 * Spike);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
