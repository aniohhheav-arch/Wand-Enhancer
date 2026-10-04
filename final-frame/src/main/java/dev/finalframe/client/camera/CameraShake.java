package dev.finalframe.client.camera;

import dev.finalframe.FFConfig;
import net.minecraft.util.Mth;

/**
 * Trauma-based camera shake: impacts add trauma, which decays every tick; displacement scales with
 * trauma squared and is driven by smooth layered noise so it reads as a physical jolt, not jitter.
 */
public final class CameraShake {
    private static final float DECAY_PER_TICK = 0.075f;
    private static final float MAX_YAW = 2.6f;
    private static final float MAX_PITCH = 2.2f;
    private static final float MAX_ROLL = 3.2f;
    private float trauma;
    private float lastTrauma;

    public void add(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    public void tick() {
        lastTrauma = trauma;
        trauma = Math.max(0f, trauma - DECAY_PER_TICK);
    }

    public void reset() {
        trauma = 0;
        lastTrauma = 0;
    }

    /** Returns {yaw, pitch, roll} offsets in degrees. */
    public float[] sample(float time, float partialTick) {
        float t = Mth.lerp(partialTick, lastTrauma, trauma);
        float scale = t * t * FFConfig.get(FFConfig.CAMERA_SHAKE).floatValue();
        if (scale <= 0.0001f) {
            return new float[] {0, 0, 0};
        }
        float s = time * 1.35f;
        return new float[] {
            MAX_YAW * scale * noise(s, 0.0f),
            MAX_PITCH * scale * noise(s, 17.3f),
            MAX_ROLL * scale * noise(s, 41.9f)
        };
    }

    private static float noise(float t, float seed) {
        return 0.6f * Mth.sin(t * 2.1f + seed) + 0.3f * Mth.sin(t * 4.7f + seed * 1.7f) + 0.1f * Mth.sin(t * 9.3f + seed * 2.3f);
    }
}
