package dev.finalframe.client.choreo;

/**
 * Screen-space presentation for a moment of the cinematic.
 *
 * @param saturation 1 = untouched color, 0 = monochrome
 * @param warmth     strength of the warm western tint
 * @param contrast   contrast multiplier
 * @param vignette   vignette strength
 * @param flash      white flash amount (muzzle flash)
 * @param grain      film grain amount
 * @param letterbox  0..1 letterbox bar coverage
 * @param title      0..1 title card visibility
 * @param duck       0..1 world audio ducking
 */
public record Grade(float saturation, float warmth, float contrast, float vignette, float flash, float grain, float letterbox,
                    float title, float duck) {
    public static final Grade NEUTRAL = new Grade(1, 0, 1, 0, 0, 0, 0, 0, 0);

    public boolean isNeutral() {
        return letterbox <= 0.001f && warmth <= 0.001f && flash <= 0.001f && Math.abs(saturation - 1) < 0.001f
            && Math.abs(contrast - 1) < 0.001f && vignette <= 0.001f;
    }
}
