package dev.finalframe.client.choreo;

import static dev.finalframe.sheriff.DuelScript.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.finalframe.FFConfig;
import dev.finalframe.client.anim.Rig;
import dev.finalframe.client.anim.Track;
import dev.finalframe.client.camera.CameraPose;
import dev.finalframe.client.camera.ShotList;
import dev.finalframe.client.render.WorldFx;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherSnapshot;
import dev.finalframe.sheriff.DuelScript;
import dev.finalframe.sheriff.SheriffScript;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Last Duel. A standoff staged like a western, with things in it that do not add up: a holstered cylinder that
 * turns by itself, a casing on the ground before anyone fires, one gunshot for two guns that arrives before either
 * flashes, a sheriff who is not quite where he was a cut ago, and a bullet trail that comes from behind the target.
 */
public final class DuelChoreography implements Choreography {
    public static final DuelChoreography INSTANCE = new DuelChoreography();
    private static final float PI = (float) Math.PI;
    private static final DustParticleOptions WIND_DUST = new DustParticleOptions(new Vector3f(0.86f, 0.76f, 0.56f), 1.3f);

    // ------------------------------------------------------------------ performer animation
    private static final Track R_ARM_X = new Track()
        .key(0, -0.12f).key(CONTACT - 4, 0.4f).key(CONTACT, -1.45f).key(CONTACT + 5, -0.2f).key(SHERIFF_SPIN - 2, -0.25f)
        .key(SHERIFF_SPIN + 1, -0.95f).key(SHERIFF_SPIN + 8, -0.95f).key(SHERIFF_HOLSTER - 2, -0.35f).key(SHERIFF_HOLSTER, 0.22f)
        .key(SHERIFF_HOLSTER + 4, -0.08f).key(DRAW, -0.08f).key(DRAW + 1, 0.28f).key(DRAW + 4, -PI / 2).key(FIRE, -PI / 2)
        .key(FIRE + 1, -2.1f).key(FIRE + 6, -1.6f).key(LOWER, -1.55f).key(LOWER + 6, -0.85f).key(HOLSTER - 2, -0.85f)
        .key(HOLSTER + 2, 0.25f).key(HOLSTER + 6, 0);
    private static final Track R_ARM_Z = new Track()
        .key(0, 0.05f).key(SHERIFF_HOLSTER, 0.05f).key(SHERIFF_HOLSTER + 6, 0.3f).key(DRAW, 0.3f).key(DRAW + 3, 0.05f);
    private static final Track L_ARM_X = new Track()
        .key(0, 0).key(CONTACT - 4, 0.4f).key(CONTACT, -1.45f).key(CONTACT + 5, -0.1f).key(SECOND_DRAW, 0.25f)
        .key(SECOND_DRAW + 4, -0.9f).key(THROW - 2, -0.55f).key(THROW + 1, -1.8f).key(THROW + 6, -0.2f).key(STANDOFF, 0)
        .key(DRAW, 0).key(DRAW + 4, -0.45f).key(LOWER + 6, 0);
    private static final Track STANCE = new Track()
        .key(0, 0).key(SHERIFF_HOLSTER, 0).key(STANDOFF, 1).key(LOWER, 1).key(TURN, 0);
    private static final Track LEAN = new Track()
        .key(0, 0).key(CONTACT - 2, -5).key(CONTACT, 12).key(CONTACT + 6, 0).key(DRAW, 0).key(DRAW + 2, 4).key(FIRE, 2)
        .key(FIRE + 1, -6).key(FIRE + 7, 0);

    // ------------------------------------------------------------------ camera edit
    private static final ShotList SHOTS = new ShotList()
        .cut(0, (s, t, g) -> g)
        .blend(3, 10, DuelChoreography::approach)
        .cut(CONTACT - 3, DuelChoreography::shove)
        .cut(SECOND_DRAW, DuelChoreography::secondDraw)
        .cut(THROW, DuelChoreography::throwOverShoulder)
        .cut(CATCH - 1, DuelChoreography::catchMedium)
        .cut(SHERIFF_SPIN, DuelChoreography::sheriffHolsters)
        .cut(STANDOFF, DuelChoreography::standoff)
        .cut(STANDOFF + 9, DuelChoreography::standoffReverse)
        .cut(HAND_P, DuelChoreography::handPerformer)
        .cut(HAND_T, DuelChoreography::handTarget)
        .cut(POV_P, DuelChoreography::povPerformer)
        .cut(POV_T, DuelChoreography::povTarget)
        .cut(RAPID, (s, t, g) -> eyes(s, t, true))
        .cut(RAPID + 4, (s, t, g) -> hand(s, t, false))
        .cut(RAPID + 8, (s, t, g) -> eyes(s, t, false))
        .cut(RAPID + 12, (s, t, g) -> hand(s, t, true))
        .cut(SILENCE, DuelChoreography::stillWide)
        .cut(DRAW, DuelChoreography::profile)
        .cut(CASING_CU, DuelChoreography::casingShot)
        .cut(REVEAL, DuelChoreography::reveal)
        .blend(RETURN, END - RETURN - 6, (s, t, g) -> g);

    private DuelChoreography() {
    }

    // ------------------------------------------------------------------ geometry
    private static Vec3 w(ClientSession s, double x, double y, double z) {
        return s.snapshot().frame().toWorld(x, y, z);
    }

    private static Vec3 w(ClientSession s, Vec3 local) {
        return s.snapshot().frame().toWorld(local);
    }

    private static Vec3 dir(ClientSession s, double x, double y, double z) {
        AnchorFrame f = s.snapshot().frame();
        return f.toWorld(x, y, z).subtract(f.origin());
    }

    private static double pz(ClientSession s, float t) {
        return DuelScript.performer(s.snapshot(), t).z;
    }

    private static double tz(ClientSession s) {
        return endDistance(s.snapshot());
    }

    private static double mid(ClientSession s, float t) {
        return (pz(s, t) + tz(s)) * 0.5;
    }

    /** The sheriff is never quite where the previous cut left him. */
    private static double drift(float t) {
        if (t >= POV_T && t < RAPID) {
            return 0.38;
        }
        if (t >= RAPID + 8 && t < SILENCE) {
            return -0.22;
        }
        return 0;
    }

    private static boolean eyesCut(float t) {
        return t >= RAPID && t < RAPID + 4 || t >= RAPID + 8 && t < RAPID + 12;
    }

    private static Vec3 casingFlight(ClientSession s, float t) {
        float p = Ease.range(t, FIRE, BLACK + 4);
        Vec3 eject = performerMuzzle(s.snapshot()).add(0.12, 0.05, -0.7);
        return eject.add(0.5 * p, 1.1 * Mth.sin(p * PI * 0.8f), -0.3 * p);
    }

    private static Vec3 impossibleCasing(ClientSession s) {
        return new Vec3(-0.55, 0.03, tz(s) - 0.65);
    }

    private static Vec3 holster(ClientSession s, float t) {
        return new Vec3(0.3, 0.78, pz(s, t) + 0.02);
    }

    // ------------------------------------------------------------------ shots
    private static CameraPose approach(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        float p = Ease.range(t, 3, CONTACT);
        Vec3 pos = w(s, 2.1, Mth.lerp(p, 0.3, 1.0), z + 1.1);
        return CameraPose.lookAt(pos, w(s, 0, Mth.lerp(p, 0.3, 1.3), z + 0.2), 0, 52f);
    }

    private static CameraPose shove(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        Vec3 target = s.targetMotion(t).position().add(0, s.snapshot().targetHeight() * 0.6, 0);
        Vec3 performer = w(s, 0, 1.25, z);
        Vec3 look = SheriffScript.lerp(performer, target, 0.35 + 0.3 * Ease.range(t, CONTACT, STUMBLE_END));
        Vec3 pos = w(s, 3.1, 1.25, z + 1.0 + 0.8 * Ease.range(t, CONTACT, STUMBLE_END));
        return CameraPose.lookAt(pos, look, 0, 56f);
    }

    private static CameraPose secondDraw(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        float p = Ease.range(t, SECOND_DRAW, THROW);
        Vec3 pos = w(s, -1.35, 1.0, z + 1.9);
        return CameraPose.lookAt(pos, w(s, -0.25, Mth.lerp(p, 0.95, 1.35), z), 0, 44f);
    }

    private static CameraPose throwOverShoulder(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        Vec3 pos = w(s, 0.75, 1.6, z - 1.7);
        Vec3 gun = w(s, thrownGun(s.snapshot(), Math.min(t, CATCH)));
        Vec3 look = SheriffScript.lerp(w(s, 0, 1.4, tz(s)), gun, 0.45);
        return CameraPose.lookAt(pos, look, 0, 50f);
    }

    private static CameraPose catchMedium(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, CATCH, SHERIFF_SPIN);
        Vec3 hand = w(s, targetHand(s.snapshot()));
        Vec3 face = w(s, targetEyes(s.snapshot()));
        Vec3 pos = w(s, -1.6, 1.45, tz(s) - 2.1);
        return CameraPose.lookAt(pos, SheriffScript.lerp(hand, face, 0.55), 0, 46f - 4f * p);
    }

    private static CameraPose sheriffHolsters(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, SHERIFF_SPIN, STANDOFF);
        double z = pz(s, t);
        Vec3 pos = w(s, 1.5, 1.3, z + 1.7);
        return CameraPose.lookAt(pos, w(s, 0.25, Mth.lerp(Ease.inOutSine(p), 1.25, 0.95), z), 0, 42f);
    }

    /** Low over the sheriff's hip: holster big in the foreground, the target small and waiting at the far end. */
    private static CameraPose standoff(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, STANDOFF, STANDOFF + 9));
        double z = pz(s, t);
        Vec3 pos = w(s, 0.85, 0.55, z - 1.25 + 0.15 * p);
        return CameraPose.lookAt(pos, w(s, -0.15, 1.0, tz(s)), 0, 36f);
    }

    /** The reverse: low past the target's hip toward the sheriff. */
    private static CameraPose standoffReverse(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, STANDOFF + 9, HAND_P));
        Vec3 pos = w(s, -0.85, 0.55, tz(s) + 1.25 - 0.15 * p);
        return CameraPose.lookAt(pos, w(s, 0.15, 1.0, pz(s, t)), 0, 36f);
    }

    private static CameraPose handPerformer(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HAND_P, HAND_T);
        Vec3 h = w(s, holster(s, t));
        Vec3 pos = h.add(dir(s, 0.8, -0.22, 1.35 - 0.15 * p));
        return CameraPose.lookAt(pos, h.add(0, 0.02, 0), 0, 34f - 3f * p);
    }

    private static CameraPose handTarget(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HAND_T, POV_P);
        Vec3 belt = w(s, targetBelt(s.snapshot()));
        Vec3 look = SheriffScript.lerp(belt, w(s, impossibleCasing(s)), 0.3);
        Vec3 pos = belt.add(dir(s, -1.15, -0.15, -1.35 + 0.12 * p));
        return CameraPose.lookAt(pos, look, 0, 36f - 3f * p);
    }

    private static CameraPose povPerformer(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, POV_P, POV_T));
        Vec3 pos = w(s, 0.32, 1.7, pz(s, t) - 0.35);
        return CameraPose.lookAt(pos, w(s, targetEyes(s.snapshot())), 0, Mth.lerp(p, 55f, 16f));
    }

    private static CameraPose povTarget(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, POV_T, RAPID));
        Vec3 pos = w(s, targetEyes(s.snapshot())).add(dir(s, 0.3, 0.05, 0.4));
        return CameraPose.lookAt(pos, w(s, drift(t), 1.6, pz(s, t)), 0, Mth.lerp(p, 55f, 14f));
    }

    private static CameraPose eyes(ClientSession s, float t, boolean performer) {
        if (performer) {
            Vec3 face = w(s, drift(t), 1.62, pz(s, t));
            return CameraPose.lookAt(face.add(dir(s, 0.05, 0.02, 1.0)), face, 0, 26f);
        }
        Vec3 face = w(s, targetEyes(s.snapshot()));
        return CameraPose.lookAt(face.add(dir(s, -0.05, 0.02, -1.0)), face, 0, 26f);
    }

    private static CameraPose hand(ClientSession s, float t, boolean performer) {
        if (performer) {
            Vec3 h = w(s, holster(s, t).add(drift(t), 0, 0));
            return CameraPose.lookAt(h.add(dir(s, 0.6, -0.15, 1.0)), h, 3f, 26f);
        }
        Vec3 belt = w(s, targetBelt(s.snapshot()));
        return CameraPose.lookAt(belt.add(dir(s, -0.85, -0.1, -1.5)), belt, -3f, 30f);
    }

    private static CameraPose stillWide(ClientSession s, float t, CameraPose g) {
        double mid = mid(s, t);
        return CameraPose.lookAt(w(s, -10.5, 1.6, mid), w(s, 0, 1.1, mid), 0, 42f);
    }

    private static CameraPose profile(ClientSession s, float t, CameraPose g) {
        double mid = mid(s, t);
        float push = Ease.inOutSine(Ease.range(t, DRAW, FIRE));
        float kick = Ease.bump(t, FIRE, FIRE + 4);
        Vec3 pos = w(s, 9.0 - 1.0 * push, 1.0, mid);
        return CameraPose.lookAt(pos, w(s, 0, 1.3, mid), 0, 44f - 4f * kick);
    }

    private static CameraPose casingShot(ClientSession s, float t, CameraPose g) {
        Vec3 casing = w(s, casingFlight(s, t));
        return CameraPose.lookAt(casing.add(dir(s, 0.45, -0.55, 0.25)), casing, 8f, 34f);
    }

    private static CameraPose reveal(ClientSession s, float t, CameraPose g) {
        float fall = Ease.inCubic(Ease.range(t, FALL, FATAL));
        Vec3 eyes = w(s, targetEyes(s.snapshot()));
        Vec3 pos = eyes.add(0, -(eyes.y - s.snapshot().targetEnd().y - 0.25) * fall, 0)
            .add(s.snapshot().frame().forward().scale(0.6 * fall));
        Vec3 look = w(s, 0, 1.3 - 0.8 * fall, pz(s, t));
        return CameraPose.lookAt(pos, look.add(0, 1.4 * fall, 0), 5f + 70f * fall, 50f);
    }

    // ------------------------------------------------------------------ choreography
    @Override
    public CameraPose camera(ClientSession session, float t, CameraPose gameplay) {
        return SHOTS.evaluate(session, t, gameplay);
    }

    @Override
    public void onTick(ClientSession s, int tick) {
        switch (tick) {
            case CONTACT -> s.shake().add(0.45f);
            case CATCH, SHERIFF_HOLSTER -> s.shake().add(0.08f);
            case FIRE -> s.shake().add(1.0f);
            case FATAL -> s.shake().add(0.3f);
            default -> {
            }
        }
        if (tick >= STANDOFF && tick < DRAW) {
            windDust(s, tick);
        }
        if (s.isLocal() && tick == SILENCE && FFConfig.get(FFConfig.DUCK_WORLD_AUDIO)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
    }

    /** A thin, slow drift of dust between the two of them. Purely local; nothing gameplay-relevant. */
    private static void windDust(ClientSession s, int tick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource r = RandomSource.create(s.snapshot().sessionId() * 31L + tick);
        Vec3 wind = dir(s, 0.035, 0, 0.004);
        double mid = mid(s, tick);
        for (int i = 0; i < 2; i++) {
            Vec3 p = w(s, -4.0 + r.nextDouble() * 2.5, 0.08 + r.nextDouble() * 0.5, mid + (r.nextDouble() - 0.5) * 7.0);
            level.addParticle(WIND_DUST, p.x, p.y, p.z, wind.x, 0.002, wind.z);
        }
    }

    @Override
    public void posePerformer(Rig rig, ClientSession s, float t) {
        float wgt = Ease.range(t, 0, 3) * (1 - Ease.range(t, RETURN, END - 6));
        boolean walking = (t > 2 && t < APPROACH_END) || (t >= WALK && t < WALK_END);
        float stride = walking ? 0.55f * Mth.sin(t * 0.45f) : 0;
        float stance = STANCE.sample(t);
        // Fingers over the holster: barely-there twitches that get worse as the draw nears.
        float nerves = Ease.range(t, STANDOFF, DRAW) * (1 - Ease.range(t, DRAW, DRAW + 1));
        float twitch = nerves * (0.03f * Mth.sin(t * 1.9f) + 0.05f * Ease.bump(t, HAND_P + 6, HAND_P + 8)
            + 0.06f * Ease.bump(t, RAPID + 13, RAPID + 15));
        Rig.blend(rig.head(), 0.05f - 0.08f * Ease.bump(t, SHERIFF_SPIN, SHERIFF_HOLSTER), 0, 0, wgt);
        Rig.blend(rig.rightArm(), R_ARM_X.sample(t) - 0.4f * stride * (t >= WALK ? 1 : 0), -0.05f, R_ARM_Z.sample(t) + twitch, wgt);
        Rig.blend(rig.leftArm(), L_ARM_X.sample(t) + 0.4f * stride, 0, -0.06f - 0.12f * stance, wgt);
        Rig.blend(rig.rightLeg(), stride, 0, 0.04f + 0.1f * stance, wgt);
        Rig.blend(rig.leftLeg(), -stride, 0, -0.04f - 0.1f * stance, wgt);
    }

    @Override
    public void poseTarget(Rig rig, ClientSession s, float t) {
        float stumble = Ease.bump(t, CONTACT, STUMBLE_END + 2);
        float steps = Mth.sin((t - CONTACT) * 0.75f) * stumble;
        float reach = Ease.bump(t, CATCH - 6, CATCH + 6);
        float inspect = Ease.bump(t, CATCH + 2, SHERIFF_HOLSTER);
        float hover = Ease.range(t, SHERIFF_HOLSTER, STANDOFF) * (1 - Ease.range(t, DRAW, DRAW + 2));
        float aim = Ease.inOutCubic(Ease.range(t, DRAW, DRAW + 4)) * (1 - Ease.range(t, BLACK, REVEAL));
        float twitch = hover * 0.04f * Mth.sin(t * 2.3f + 1f);
        float armX = -1.3f * stumble - 1.2f * reach - 0.7f * inspect - (PI / 2) * aim - 0.5f * Ease.bump(t, FIRE, FIRE + 6)
            - 0.1f * hover;
        float w = Math.max(Math.max(stumble, reach), Math.max(Math.max(aim, inspect), Math.max(hover, Ease.range(t, CATCH, CATCH + 4))));
        if (w > 0) {
            Rig.blend(rig.rightArm(), armX, 0, 0.1f + 0.22f * hover + twitch, w);
            Rig.blend(rig.leftArm(), -1.3f * stumble, 0, -0.5f * stumble, stumble);
            Rig.blend(rig.head(), 0.3f * inspect, 0, 0, inspect);
        }
        if (stumble > 0) {
            Rig.blend(rig.rightLeg(), 0.6f * steps, 0, 0, stumble);
            Rig.blend(rig.leftLeg(), -0.6f * steps, 0, 0, stumble);
        }
    }

    @Override
    public float[] performerLean(ClientSession s, float t) {
        return new float[] {LEAN.sample(t), 0};
    }

    @Override
    public Vec3 performerOffset(ClientSession s, float t) {
        return DuelScript.performer(s.snapshot(), t).add(drift(t), 0, 0);
    }

    @Override
    public float performerBodyYaw(ClientSession s, float t) {
        return 180f * Ease.inOutSine(Ease.range(t, TURN, WALK));
    }

    @Override
    public WeaponState weapon(ClientSession s, float t) {
        // The holstered cylinder turns a chamber on its own during the hand close-up.
        float selfTurn = 60f * Ease.outBack(Ease.range(t, SELF_CYLINDER, SELF_CYLINDER + 3));
        if (t >= SHERIFF_HOLSTER && t < DRAW + 1 || t >= HOLSTER + 2) {
            return new WeaponState(WeaponState.Placement.HOLSTER, 0, 0, 0, 0, selfTurn, 0);
        }
        float cylinder = selfTurn + 60f * Ease.range(t, FIRE + 2, FIRE + 5);
        float hammer = t >= DRAW + 2 && t < FIRE ? Ease.outBack(Ease.range(t, DRAW + 2, DRAW + 4)) : 0;
        float spin = 720f * Ease.inOutSine(Ease.range(t, SHERIFF_SPIN, SHERIFF_SPIN + 8))
            + 720f * Ease.inOutSine(Ease.range(t, SPIN, SPIN + 10));
        return new WeaponState(WeaponState.Placement.HAND, spin, 0, 0, 0, cylinder, hammer);
    }

    @Override
    public void renderWorld(ClientSession s, float t, PoseStack ps, MultiBufferSource buffers, Camera camera) {
        FinisherSnapshot snap = s.snapshot();
        AnchorFrame f = snap.frame();
        if (grade(s, t).black() > 0.98f) {
            return;
        }
        WeaponState second = new WeaponState(WeaponState.Placement.AIR, 0, 0, 0, 0, 0, t >= DRAW + 2 && t < FIRE ? 1 : 0);
        if (t >= SECOND_DRAW + 1 && t < THROW) {
            Vec3 hand = w(s, -0.42, 0.75 + 0.5 * Ease.range(t, SECOND_DRAW, THROW), pz(s, t) + 0.25);
            WorldFx.renderAirborneRevolver(ps, buffers, camera, hand, f.yaw(),
                -70f + 720f * Ease.inOutSine(Ease.range(t, SECOND_DRAW + 3, THROW)), second);
        } else if (t >= THROW && t < CATCH) {
            WorldFx.renderAirborneRevolver(ps, buffers, camera, w(s, thrownGun(snap, t)), f.yaw() + 90f,
                900f * Ease.range(t, THROW, CATCH), second);
        } else if (t >= CATCH && t < REVEAL) {
            Vec3 hand = targetHand(snap);
            Vec3 belt = targetBelt(snap);
            Vec3 aimed = targetAimMuzzle(snap).add(0, -0.08, 0.55);
            float tuck = Ease.inOutSine(Ease.range(t, SHERIFF_HOLSTER - 4, SHERIFF_HOLSTER + 2));
            float aim = Ease.inOutCubic(Ease.range(t, DRAW, DRAW + 4));
            Vec3 rest = hand.add(belt.subtract(hand).scale(tuck));
            Vec3 at = rest.add(aimed.subtract(rest).scale(aim));
            float kick = 30f * Ease.bump(t, FIRE, FIRE + 5);
            float inspect = 35f * Ease.bump(t, CATCH + 2, SHERIFF_HOLSTER - 3);
            float spin = Mth.lerp(aim, Mth.lerp(tuck, -75f + inspect, -95f), 0f) + kick;
            WorldFx.renderAirborneRevolver(ps, buffers, camera, w(s, at), f.yaw() + 180f, spin, second);
        }
        if (t >= HAND_T - 0.01f && t < FIRE) {
            renderCasing(ps, buffers, camera, w(s, impossibleCasing(s)), f.yaw() + 70f, 90f);
        }
        if (t >= FIRE && t < BLACK + 4) {
            renderCasing(ps, buffers, camera, w(s, casingFlight(s, t)), f.yaw(), 1400f * Ease.range(t, FIRE, BLACK + 4));
        }
        if (t >= FIRE && t < FIRE + 1.6f) {
            float a = 1 - (t - FIRE) / 1.6f;
            Vec3 pm = w(s, performerMuzzle(snap));
            Vec3 tm = w(s, targetAimMuzzle(snap));
            WorldFx.renderMuzzleFlash(ps, buffers, camera, pm, tm, a);
            WorldFx.renderMuzzleFlash(ps, buffers, camera, tm, pm, a);
        }
        if (t >= FIRE && t < FIRE + 8) {
            // The only visible bullet comes from somewhere no one is standing.
            Vec3 chest = w(s, 0, snap.targetHeight() * 0.62, tz(s));
            Vec3 behind = w(s, 1.6, snap.targetHeight() * 0.9, tz(s) + 7.0);
            WorldFx.renderTracer(ps, buffers, camera, behind, chest, 1 - (t - FIRE) / 8f);
        }
    }

    public static void renderCasing(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, float yaw, float spin) {
        renderSlug(ps, buffers, camera, at, yaw, spin, 0.95f, 0.72f, 0.3f, 0.78f, 0.56f, 0.22f);
    }

    /** A small two-tone cylinder-ish prop: casing (brass/brass) or bullet (lead nose over brass). */
    public static void renderSlug(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, float yaw, float spin,
                                  float r, float g, float b, float r2, float g2, float b2) {
        Vec3 cam = camera.getPosition();
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        float rad = 0.026f;
        float h = 0.07f;
        box(vc, m, -rad, -h, -rad, rad, h, rad, r, g, b);
        box(vc, m, -rad * 1.25f, -h - 0.01f, -rad * 1.25f, rad * 1.25f, -h, rad * 1.25f, r2, g2, b2);
        ps.popPose();
    }

    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b) {
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, 1f);
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, 0.55f);
        quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, 0.8f);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, 0.8f);
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, 0.68f);
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, 0.68f);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float r, float g, float b, float shade) {
        vc.addVertex(m, ax, ay, az).setColor(r * shade, g * shade, b * shade, 1f);
        vc.addVertex(m, bx, by, bz).setColor(r * shade, g * shade, b * shade, 1f);
        vc.addVertex(m, cx, cy, cz).setColor(r * shade, g * shade, b * shade, 1f);
        vc.addVertex(m, dx, dy, dz).setColor(r * shade, g * shade, b * shade, 1f);
    }

    @Override
    public Grade grade(ClientSession s, float t) {
        float on = Ease.inOutSine(Ease.range(t, 3, 14)) * (1 - Ease.inOutSine(Ease.range(t, RETURN, END - 4)));
        float tension = Ease.inOutSine(Ease.range(t, STANDOFF, DRAW)) * (1 - Ease.range(t, BLACK, REVEAL + 10));
        float slow = Ease.range(t, DRAW - 2, DRAW + 2) * (1 - Ease.range(t, FIRE + 4, BLACK));
        float saturation = 1 - on * (0.2f + 0.35f * tension + 0.4f * slow);
        float flash = t >= FIRE && t < FIRE + 2.5f ? 0.75f * (1 - (t - FIRE) / 2.5f) : 0;
        float black = 0;
        if (t >= BLACK && t < REVEAL) {
            black = 1;
        } else if (t >= REVEAL && t < REVEAL + 16) {
            black = 1 - Ease.inOutSine(Ease.range(t, REVEAL, REVEAL + 16));
        } else if (t >= FATAL && t < RETURN + 4) {
            black = 1;
        } else if (t >= RETURN + 4) {
            black = 1 - Ease.inOutSine(Ease.range(t, RETURN + 4, END - 4));
        }
        // Music bleeds away through the standoff; from the silence beat on there is nothing at all.
        float duck = Ease.inOutSine(Ease.range(t, STANDOFF, SILENCE)) * (1 - Ease.range(t, WALK, END));
        float letterbox = on * (eyesCut(t) ? 2.7f : 1f);
        return new Grade(saturation, 0.55f * on * (1 - 0.5f * tension), 1 + 0.12f * on + 0.1f * tension,
            on * (0.45f + 0.35f * tension), flash, 0.05f * on, letterbox, 0, duck, black);
    }
}
