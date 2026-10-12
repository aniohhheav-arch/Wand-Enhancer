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
    public static final int STUMBLE_END = 50;
    public static final int SECOND_DRAW = 50;
    public static final int THROW = 58;
    public static final int CATCH = 72;
    /** The sheriff twirls his own revolver home; the target tucks the gift into its belt. */
    public static final int SHERIFF_SPIN = 74;
    public static final int SHERIFF_HOLSTER = 84;
    public static final int STANDOFF = 88;
    public static final int HAND_P = 106;
    public static final int SELF_CYLINDER = 111;
    public static final int HAND_T = 118;
    public static final int POV_P = 130;
    public static final int POV_T = 142;
    public static final int RAPID = 154;
    public static final int SILENCE = 168;
    public static final int DRAW = 174;
    public static final int SHOT_HEARD = 178;
    public static final int FIRE = 180;
    public static final int CASING_CU = 182;
    public static final int BLACK = 190;
    public static final int REVEAL = 208;
    public static final int LOWER = 220;
    public static final int SPIN = 228;
    public static final int HOLSTER = 242;
    public static final int TURN = 246;
    public static final int WALK = 254;
    public static final int WALK_END = 286;
    public static final int FALL = 272;
    public static final int FATAL = 282;
    public static final int RETURN = 290;
    public static final int END = 314;

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

    /** Where the target tucks the gift revolver: the front of its belt, on its right. */
    public static Vec3 targetBelt(FinisherSnapshot s) {
        return new Vec3(-0.24, s.targetHeight() * 0.5, endDistance(s) - 0.12);
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
