package dev.infinitygauntlet.finisher;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Cosmic Punch execution, tick by tick. Shared by the server script (gameplay beats, sounds, burst effects) and
 * the client script (body animation, camera, glow), so both sides agree on where the caster is and what happens when.
 *
 * <pre>
 *  0  WINDUP     fist drawn back, weight on the back foot
 *  6  LUNGE      the caster closes the distance in six ticks, arm swinging through
 * 12  IMPACT     the fist is driven through the target: impact frame, hit-stop, energy bursts out of its back
 * 12  OVERLOAD   the target hangs frozen on the fist, glowing ever brighter pink; heartbeats quicken
 * 74  ERUPTION   it detonates in a pink-purple cosmic explosion
 * 74  AFTERMATH  the arm draws back out of the fading energy; control returns at the end
 * </pre>
 */
public final class CosmicPunchTimeline {
    public static final int LUNGE = 6;
    public static final int IMPACT = 12;
    public static final int ERUPTION = 74;
    public static final int RETRACT = 80;
    public static final int DURATION = 112;
    /** Heartbeat pulses through the target, quickening toward the eruption. */
    public static final int[] HEARTBEATS = {18, 29, 38, 46, 52, 57, 61, 64, 67, 69, 71, 72, 73};

    /** Where the caster stands to drive the fist through a target centred at {@code target} with half-width {@code halfWidth}. */
    public static Vec3 standPoint(Vec3 start, Vec3 target, float halfWidth) {
        Vec3 d = dir(start, target);
        Vec3 p = target.subtract(d.scale(halfWidth + 0.55));
        return new Vec3(p.x, start.y, p.z);
    }

    public static Vec3 dir(Vec3 start, Vec3 target) {
        Vec3 d = new Vec3(target.x - start.x, 0, target.z - start.z);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** The caster's feet position at {@code t} ticks into the finisher. */
    public static Vec3 casterPos(Vec3 start, Vec3 stand, float t) {
        if (t <= LUNGE) {
            // rock back onto the rear foot
            return start.subtract(stand.subtract(start).normalize().scale(0.15 * Math.sin(t / LUNGE * Math.PI * 0.5)));
        }
        float u = Mth.clamp((t - LUNGE) / (IMPACT - LUNGE), 0, 1);
        float e = u * u * (3 - 2 * u) * 0.35f + u * u * 0.65f; // explosive start of a lunge that slams to a stop
        Vec3 from = start.subtract(stand.subtract(start).normalize().scale(0.15));
        return from.lerp(stand, e);
    }

    /** 0..1 energy build-up inside the target. */
    public static float charge(float t) {
        float u = Mth.clamp((t - IMPACT) / (ERUPTION - IMPACT), 0, 1);
        return u * u * (0.6f + 0.4f * u);
    }

    private CosmicPunchTimeline() {
    }
}
