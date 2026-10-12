package dev.finalframe.sheriff;

import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Beat sheet and shared geometry for The Last Duel (local frame: z points from the performer toward the target). */
public final class DuelScript {
    public static final int APPROACH_END = 28;
    public static final int CONTACT = 32;
    public static final int STUMBLE_END = 46;
    public static final int SECOND_DRAW = 46;
    public static final int THROW = 54;
    public static final int CATCH = 70;
    public static final int STANDOFF = 70;
    public static final int HAND_P = 88;
    public static final int SELF_CYLINDER = 92;
    public static final int HAND_T = 100;
    public static final int POV_P = 112;
    public static final int POV_T = 124;
    public static final int RAPID = 136;
    public static final int DRAW = 152;
    public static final int SHOT_HEARD = 156;
    public static final int FIRE = 158;
    public static final int CASING_CU = 160;
    public static final int BLACK = 166;
    public static final int REVEAL = 184;
    public static final int LOWER = 196;
    public static final int SPIN = 204;
    public static final int HOLSTER = 218;
    public static final int TURN = 222;
    public static final int WALK = 230;
    public static final int WALK_END = 262;
    public static final int FALL = 248;
    public static final int FATAL = 258;
    public static final int RETURN = 266;
    public static final int END = 290;

    public static final double PUSH = 4.0;
    public static final double CLOSE = 1.6;
    public static final double WALK_AWAY = 3.5;

    private DuelScript() {
    }

    public static double startDistance(FinisherSnapshot s) {
        return s.frame().toLocal(s.targetStart()).z;
    }

    public static double endDistance(FinisherSnapshot s) {
        return s.frame().toLocal(s.targetEnd()).z;
    }

    /** Where the performer stands toward the target (local z). */
    public static double approachEnd(FinisherSnapshot s) {
        return Math.max(0, startDistance(s) - CLOSE);
    }

    /** Performer feet in the local frame. Both the server hold and the client render use this. */
    public static Vec3 performer(FinisherSnapshot s, float t) {
        double z = approachEnd(s) * Ease.inOutSine(Ease.range(t, 2, APPROACH_END));
        z -= WALK_AWAY * Ease.inOutSine(Ease.range(t, WALK, WALK_END));
        return new Vec3(0, 0, z);
    }

    /** The second revolver's flight from the performer's left hand to the target's right hand. */
    public static Vec3 thrownGun(FinisherSnapshot s, float t) {
        float p = Ease.range(t, THROW, CATCH);
        Vec3 from = new Vec3(-0.35, 1.25, approachEnd(s) + 0.35);
        Vec3 to = targetHand(s);
        Vec3 line = from.add(to.subtract(from).scale(p));
        return line.add(0, 1.3 * Mth.sin(p * (float) Math.PI), 0);
    }

    /** The target's gun hand at rest (its right is the anchor's left). */
    public static Vec3 targetHand(FinisherSnapshot s) {
        return new Vec3(-0.38, s.targetHeight() * 0.42, endDistance(s) - 0.18);
    }

    public static Vec3 targetEyes(FinisherSnapshot s) {
        return new Vec3(0, s.targetHeight() * 0.88, endDistance(s) - 0.2);
    }

    public static Vec3 targetAimMuzzle(FinisherSnapshot s) {
        return new Vec3(-0.36, s.targetHeight() * 0.7, endDistance(s) - 0.95);
    }

    public static Vec3 performerMuzzle(FinisherSnapshot s) {
        return new Vec3(SheriffScript.MUZZLE_AT_AIM.x, SheriffScript.MUZZLE_AT_AIM.y, approachEnd(s) + SheriffScript.MUZZLE_AT_AIM.z - 0.2);
    }

    public static Vec3 world(FinisherSnapshot s, Vec3 local) {
        AnchorFrame f = s.frame();
        return f.toWorld(local);
    }
}
