package dev.finalframe.sheriff;

import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Sheriff's Last Word beat sheet. Every tick constant and every shared spatial path lives here so
 * the server (sounds, particles, the shot) and every client (camera, animation, effects) stay frame
 * accurate. Local coordinates use {@link AnchorFrame}: x = performer's right, y = up, z = forward.
 */
public final class SheriffScript {
    // Scene 1 — the shove
    public static final int ACTIVATION = 0;
    public static final int SHOVE = 4;
    public static final int CONTACT = 9;
    public static final int STUMBLE_END = 20;
    // Scene 2 — the draw
    public static final int DRAW = 22;
    public static final int DRAW_PULL = 28;
    public static final int DRAW_SPIN = 33;
    public static final int DRAW_CATCH = 46;
    // Scene 3 — the flourish
    public static final int FLOURISH = 48;
    public static final int WRIST_ROLL = 58;
    public static final int BACK_FLIP = 64;
    public static final int GRIP_CATCH = 70;
    public static final int COCK = 72;
    public static final int CYLINDER_ROLL = 75;
    public static final int AIM_STANCE = 80;
    // Scene 4 — the throw
    public static final int THROW = 84;
    public static final int TOSS = 87;
    public static final int PEAK = 104;
    public static final int PEAK_END = 112;
    // Scene 5 — the catch
    public static final int CATCH = 124;
    // Scene 6 — the last word
    public static final int AIM = 134;
    public static final int CUT_PROFILE = 139;
    public static final int CUT_TARGET_POV = 144;
    public static final int CUT_HERO = 147;
    public static final int FIRE_PHASE = 150;
    public static final int FIRE = 153;
    public static final int IMPACT = 155;
    // Scene 7 — the final pose
    public static final int FINISH = 162;
    public static final int LOWER = 172;
    public static final int FINAL_SPIN = 178;
    public static final int HOLSTER = 188;
    public static final int RESTORE = 196;
    public static final int END = 212;

    /** Revolver spin angle accumulated between toss and catch (degrees); a whole number of turns so the catch lands in grip. */
    public static final float AIR_SPIN_TOTAL = 1800f;

    public static final double PUSH_DISTANCE = 1.8;
    public static final Vec3 HAND_AT_TOSS = new Vec3(0.34, 1.62, 0.32);
    public static final Vec3 PEAK_POINT = new Vec3(0.30, 4.35, 0.42);
    public static final Vec3 CATCH_POINT = new Vec3(0.36, 1.78, 0.48);
    public static final Vec3 MUZZLE_AT_AIM = new Vec3(0.33, 1.36, 0.98);

    private SheriffScript() {
    }

    /** Airborne revolver position (local frame) for TOSS <= t <= CATCH. */
    public static Vec3 airbornePosition(float t) {
        if (t <= PEAK) {
            float p = Ease.outQuad(Ease.range(t, TOSS, PEAK));
            return lerp(HAND_AT_TOSS, PEAK_POINT, p);
        }
        if (t <= PEAK_END) {
            float p = Ease.range(t, PEAK, PEAK_END);
            return PEAK_POINT.add(0, 0.08 * Mth.sin((float) Math.PI * p), 0.02 * p);
        }
        float p = Ease.inQuad(Ease.range(t, PEAK_END, CATCH));
        return lerp(PEAK_POINT.add(0, 0, 0.02), CATCH_POINT, p);
    }

    /** Airborne spin angle (degrees) around the revolver's lateral axis. */
    public static float airborneSpin(float t) {
        float rise = 1080f;
        float hold = 60f;
        if (t <= PEAK) {
            return rise * Ease.outQuad(Ease.range(t, TOSS, PEAK));
        }
        if (t <= PEAK_END) {
            return rise + hold * Ease.range(t, PEAK, PEAK_END);
        }
        return rise + hold + (AIR_SPIN_TOTAL - rise - hold) * Ease.inQuad(Ease.range(t, PEAK_END, CATCH));
    }

    public static Vec3 muzzleWorld(FinisherSnapshot s) {
        return s.frame().toWorld(MUZZLE_AT_AIM);
    }

    /** Point on the target the final shot strikes. */
    public static Vec3 impactWorld(FinisherSnapshot s) {
        return s.targetEnd().add(0, s.targetHeight() * 0.62, 0);
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
    }

    /** Target distance along the anchor's forward axis after the shove. */
    public static double pushedDistance(FinisherSnapshot s) {
        AnchorFrame f = s.frame();
        return f.toLocal(s.targetEnd()).z;
    }
}
