package dev.finalframe.client.anim;

import dev.finalframe.finisher.Ease;
import java.util.Arrays;

/**
 * Keyframed scalar channel. Values between keys use a sine in-out ease, so every limb accelerates and
 * settles naturally; holding a value is just two keys with the same value.
 */
public final class Track {
    private float[] ticks = new float[0];
    private float[] values = new float[0];

    public Track key(float tick, float value) {
        int n = ticks.length;
        if (n > 0 && tick < ticks[n - 1]) {
            throw new IllegalArgumentException("Keys must be added in time order");
        }
        ticks = Arrays.copyOf(ticks, n + 1);
        values = Arrays.copyOf(values, n + 1);
        ticks[n] = tick;
        values[n] = value;
        return this;
    }

    public float sample(float t) {
        int n = ticks.length;
        if (n == 0) {
            return 0;
        }
        if (t <= ticks[0]) {
            return values[0];
        }
        for (int i = 1; i < n; i++) {
            if (t <= ticks[i]) {
                float p = Ease.inOutSine(Ease.range(t, ticks[i - 1], ticks[i]));
                return values[i - 1] + (values[i] - values[i - 1]) * p;
            }
        }
        return values[n - 1];
    }
}
