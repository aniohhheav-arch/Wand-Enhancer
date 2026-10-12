package dev.finalframe.sheriff;

import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Beat sheet and geometry for The Impossible Shot. Local frame: origin at the sheriff's feet, z toward the target.
 * The snapshot's aux point is where the missed bullet struck.
 */
public final class ImpossibleScript {
    public static final int LOOK = 2;
    public static final int LOOK_BACK = 14;
    public static final int LOWERED = 20;
    public static final int EARLY = 24;
    public static final int BACK_OUT = 26;
    public static final int HUNG = 44;
    public static final int POV = 50;
    public static final int GHOST_1 = 56;
    public static final int GHOST_2 = 63;
    public static final int GHOST_3 = 69;
    public static final int EAR = 74;
    public static final int CYLINDER = 82;
    public static final int GROUND = 90;
    public static final int PATHS = 98;
    public static final int HEARD = 120;
    public static final int HEARD_1 = 122;
    public static final int HEARD_2 = 131;
    public static final int HEARD_3 = 138;
    public static final int LOW_SHOT = 140;
    public static final int UNTOUCHED = 144;
    public static final int SHIFT = 150;
    public static final int SHIFT_BACK = 158;
    public static final int SHIFT_SIDE = 166;
    public static final int SHIFT_HOME = 172;
    public static final int HOLSTER_SCENE = 176;
    public static final int SPIN = 180;
    public static final int HOLSTER = 194;
    public static final int TEMPLE = 198;
    public static final int BLACK = 204;
    public static final int FATAL = 210;
    public static final int REVEAL = 218;
    public static final int TURN = 220;
    public static final int WALK = 228;
    public static final int WALK_END = 258;
    public static final int RETURN = 250;
    public static final int END = 272;

    public static final double WALK_AWAY = 3.0;

    private ImpossibleScript() {
    }

    public static double tz(FinisherSnapshot s) {
        return s.frame().toLocal(s.targetStart()).z;
    }

    public static Vec3 impact(FinisherSnapshot s) {
        return s.frame().toLocal(s.aux());
    }

    public static Vec3 shotDir(FinisherSnapshot s) {
        return impact(s).subtract(0, 1.62, 0).normalize();
    }

    public static Vec3 temple(FinisherSnapshot s) {
        return new Vec3(0.34, s.targetHeight() * 0.86, tz(s) - 0.04);
    }

    public static Vec3 performer(FinisherSnapshot s, float t) {
        return new Vec3(0, 0, -WALK_AWAY * Ease.inOutSine(Ease.range(t, WALK, WALK_END)));
    }

    /** Where the spent bullet would be if it obeyed anything: out of the hole, then creeping toward the target. */
    public static Vec3 bulletPath(FinisherSnapshot s, float t) {
        Vec3 hit = impact(s);
        Vec3 hang = hit.subtract(shotDir(s).scale(0.35));
        if (t < HUNG) {
            return hit.add(hang.subtract(hit).scale(Ease.inOutSine(Ease.range(t, BACK_OUT, HUNG))));
        }
        Vec3 near = temple(s).add(0.16, 0, 0);
        return hang.add(near.subtract(hang).scale(Ease.inOutSine(Ease.range(t, HUNG, TEMPLE + 2))));
    }

    /** Direction the bullet's nose points: into the hole, then slowly turning toward the target's temple. */
    public static Vec3 bulletHeading(FinisherSnapshot s, float t) {
        Vec3 toTemple = temple(s).subtract(bulletPath(s, t));
        if (toTemple.lengthSqr() < 1.0E-4) {
            toTemple = new Vec3(-1, 0, 0);
        }
        float turn = Ease.inOutSine(Ease.range(t, BACK_OUT + 6, HUNG + 10));
        Vec3 d = shotDir(s).scale(1 - turn).add(toTemple.normalize().scale(turn));
        return d.lengthSqr() < 1.0E-4 ? toTemple.normalize() : d.normalize();
    }

    /** Yaw the target's head should hold to look at a local point (degrees, world). */
    public static float yawTo(FinisherSnapshot s, Vec3 fromLocal, Vec3 toLocal) {
        Vec3 a = s.frame().toWorld(fromLocal);
        Vec3 b = s.frame().toWorld(toLocal);
        return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90f;
    }
}
