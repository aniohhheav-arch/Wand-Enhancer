package dev.finalframe.client.choreo;

import static dev.finalframe.sheriff.SheriffScript.*;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.finalframe.client.anim.Rig;
import dev.finalframe.client.anim.Track;
import dev.finalframe.client.camera.CameraPose;
import dev.finalframe.client.camera.ShotList;
import dev.finalframe.client.render.WorldFx;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherSnapshot;
import dev.finalframe.sheriff.SheriffScript;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client presentation of The Sheriff's Last Word: seven scenes, fourteen shots, keyframed body
 * animation for both actors, weapon handling and the western grade. Every value derives from the
 * session snapshot and the fractional tick, so each client renders the same frame.
 */
public final class SheriffChoreography implements Choreography {
    public static final SheriffChoreography INSTANCE = new SheriffChoreography();

    private static final float PI = (float) Math.PI;

    // ------------------------------------------------------------------ performer animation tracks
    private static final Track R_ARM_X = new Track()
        .key(0, 0).key(SHOVE, 0.15f).key(SHOVE + 3, 0.45f).key(CONTACT, -1.5f).key(CONTACT + 4, -1.25f).key(18, -0.2f)
        .key(DRAW, 0.18f).key(DRAW_PULL, 0.3f).key(DRAW_PULL + 3, -0.75f).key(DRAW_SPIN, -1.05f).key(DRAW_CATCH, -1.05f)
        .key(FLOURISH, -1.2f).key(WRIST_ROLL, -1.3f).key(BACK_FLIP, -1.45f).key(GRIP_CATCH, -1.3f).key(CYLINDER_ROLL, -1.2f)
        .key(AIM_STANCE, -PI / 2).key(THROW + 1, -0.9f).key(TOSS - 1, -2.45f).key(TOSS + 4, -2.7f).key(TOSS + 12, -1.0f)
        .key(PEAK_END, -0.9f).key(CATCH - 4, -2.15f).key(CATCH, -2.5f).key(CATCH + 5, -1.9f).key(AIM, -PI / 2).key(FIRE, -PI / 2)
        .key(FIRE + 1, -2.15f).key(FIRE + 6, -1.62f).key(LOWER, -1.6f).key(LOWER + 5, -0.8f).key(FINAL_SPIN, -0.9f)
        .key(HOLSTER - 2, -0.9f).key(HOLSTER + 2, 0.25f).key(HOLSTER + 6, 0.05f).key(RESTORE, 0);
    private static final Track R_ARM_Y = new Track()
        .key(0, 0).key(CONTACT, 0.25f).key(DRAW, 0).key(DRAW_SPIN, -0.3f).key(FLOURISH, -0.35f).key(AIM_STANCE, -0.1f)
        .key(TOSS, 0).key(CATCH, -0.1f).key(AIM, -0.1f).key(LOWER, -0.1f).key(LOWER + 5, -0.35f).key(HOLSTER, -0.3f)
        .key(HOLSTER + 4, 0);
    private static final Track R_ARM_Z = new Track()
        .key(0, 0.05f).key(DRAW, 0.32f).key(DRAW_PULL + 2, 0.3f).key(DRAW_SPIN, 0.05f).key(HOLSTER - 1, 0.05f)
        .key(HOLSTER + 2, 0.3f).key(HOLSTER + 7, 0.05f);
    private static final Track L_ARM_X = new Track()
        .key(0, 0).key(SHOVE, 0.15f).key(SHOVE + 3, 0.45f).key(CONTACT, -1.5f).key(CONTACT + 4, -1.25f).key(18, -0.1f)
        .key(FLOURISH, -0.25f).key(AIM_STANCE, -0.45f).key(TOSS, -0.2f).key(CATCH - 4, -0.5f).key(CATCH + 4, -0.25f)
        .key(AIM, -0.5f).key(FIRE, -0.5f).key(FIRE + 1, -0.62f).key(LOWER, -0.45f).key(LOWER + 6, 0);
    private static final Track L_ARM_Z = new Track()
        .key(0, -0.05f).key(CONTACT, -0.25f).key(18, -0.08f).key(AIM, -0.22f).key(LOWER + 6, -0.05f);
    private static final Track LEAN = new Track()
        .key(0, 0).key(SHOVE, -3).key(SHOVE + 3, -6).key(CONTACT, 15).key(CONTACT + 5, 4).key(DRAW, 0).key(CATCH - 1, 0)
        .key(CATCH + 1, 5).key(CATCH + 6, 0).key(FIRE, 0).key(FIRE + 1, -6).key(FIRE + 7, 0);
    private static final Track STEP = new Track()
        .key(0, 0).key(CONTACT - 2, 0).key(CONTACT, 0.32f).key(DRAW, 0.32f).key(FLOURISH, 0.1f).key(CATCH - 2, 0.1f)
        .key(CATCH, 0.22f).key(AIM, 0.22f).key(RESTORE, 0.22f).key(END, 0);
    private static final Track STANCE = new Track()
        .key(0, 0).key(SHOVE + 2, 0.3f).key(CONTACT, 0.45f).key(DRAW, 0.15f).key(CATCH - 2, 0.15f).key(CATCH, 0.4f)
        .key(AIM, 0.25f).key(RESTORE, 0.2f).key(END, 0);
    private static final Track HEAD_YAW = new Track()
        .key(0, 0).key(DRAW, 0).key(DRAW + 3, -0.45f).key(DRAW_PULL + 3, -0.15f).key(DRAW_SPIN, -0.3f).key(FLOURISH, -0.35f)
        .key(AIM_STANCE, 0).key(LOWER, 0).key(LOWER + 4, -0.3f).key(HOLSTER + 4, -0.3f).key(RESTORE, 0);
    private static final Track HEAD_PITCH = new Track()
        .key(0, 0.1f).key(CONTACT, 0.25f).key(DRAW, 0.2f).key(DRAW + 3, 0.6f).key(DRAW_PULL + 3, 0.3f).key(FLOURISH, 0.35f)
        .key(AIM_STANCE, 0.05f).key(AIM, 0.08f).key(LOWER, 0.05f).key(LOWER + 4, 0.45f).key(HOLSTER + 4, 0.4f).key(RESTORE, 0);

    // ------------------------------------------------------------------ camera edit
    private static final ShotList SHOTS = new ShotList()
        // Scene 1: gameplay, then the camera takes over exactly as the target stumbles.
        .cut(0, (s, t, g) -> g.withFov(g.fov() - 6f * Ease.inOutSine(Ease.range(t, 0, CONTACT))))
        .blend(CONTACT - 1, 7, (s, t, g) -> overShoulder(s, t))
        // Scene 2: slow lateral orbit through the draw.
        .blend(DRAW, 5, SheriffChoreography::drawOrbit)
        // Scene 3: the flourish, pushing in for the cylinder.
        .blend(FLOURISH, 4, SheriffChoreography::flourish)
        // Scene 4: the throw. Front-low, then tracking the revolver upward.
        .cut(THROW, SheriffChoreography::tossWide)
        .blend(TOSS + 1, 4, SheriffChoreography::tossTrack)
        .cut(PEAK, SheriffChoreography::peakCloseUp)
        // Scene 5: low-angle for the fall and the catch.
        .cut(PEAK_END, SheriffChoreography::lowAngle)
        // Scene 6: revolver close-up, side profile, target POV, hero shot, then the wide shot for the fire.
        .cut(AIM, SheriffChoreography::barrelCloseUp)
        .cut(CUT_PROFILE, SheriffChoreography::sideProfile)
        .cut(CUT_TARGET_POV, SheriffChoreography::targetPov)
        .cut(CUT_HERO, SheriffChoreography::heroLow)
        .cut(FIRE_PHASE, SheriffChoreography::fireWide)
        // Scene 7: smoke close-up, then the slow pull-away and the hand-back to gameplay.
        .cut(FINISH, SheriffChoreography::smokeCloseUp)
        .blend(LOWER + 4, 6, SheriffChoreography::pullAway)
        .blend(RESTORE, END - RESTORE, (s, t, g) -> g);

    private SheriffChoreography() {
    }

    // ------------------------------------------------------------------ shots
    private static Vec3 w(ClientSession s, double x, double y, double z) {
        return s.snapshot().frame().toWorld(x, y, z);
    }

    private static Vec3 targetChest(ClientSession s, float t) {
        return s.targetMotion(t).position().add(0, s.snapshot().targetHeight() * 0.62, 0);
    }

    private static double pushed(ClientSession s) {
        return pushedDistance(s.snapshot());
    }

    private static CameraPose overShoulder(ClientSession s, float t) {
        float p = Ease.range(t, CONTACT, DRAW);
        Vec3 pos = w(s, Mth.lerp(p, 0.78, 0.95), Mth.lerp(p, 1.95, 1.8), Mth.lerp(p, -1.55, -1.25));
        Vec3 look = SheriffScript.lerp(w(s, 0, 1.35, 0.6), targetChest(s, t), 0.65);
        return CameraPose.lookAt(pos, look, -2f * p, 62f);
    }

    private static CameraPose drawOrbit(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, DRAW, FLOURISH));
        float a = Mth.lerp(p, 150f, 58f) * Mth.DEG_TO_RAD;
        double r = Mth.lerp(p, 2.3, 2.0);
        Vec3 pos = w(s, Mth.sin(a) * r, Mth.lerp(p, 1.5, 1.32), Mth.cos(a) * r);
        Vec3 hip = w(s, 0.3, 0.95, 0.05);
        Vec3 hand = w(s, 0.36, 1.32, 0.32);
        Vec3 look = SheriffScript.lerp(hip, hand, Ease.inOutSine(Ease.range(t, DRAW_PULL, DRAW_SPIN + 2)));
        return CameraPose.lookAt(pos, look, 0, 50f);
    }

    private static CameraPose flourish(ClientSession s, float t, CameraPose g) {
        Vec3 hand = w(s, 0.36, 1.34, 0.4);
        float orbit = Ease.inOutSine(Ease.range(t, FLOURISH, AIM_STANCE));
        float a = Mth.lerp(orbit, 42f, 12f) * Mth.DEG_TO_RAD;
        double r = 1.55 - 0.7 * Ease.inOutCubic(Ease.range(t, COCK - 2, CYLINDER_ROLL + 2))
            + 0.95 * Ease.inOutCubic(Ease.range(t, AIM_STANCE - 1, THROW));
        double y = 0.18 - 0.1 * Ease.range(t, COCK, CYLINDER_ROLL);
        Vec3 pos = hand.add(rotateLocal(s, Mth.sin(a) * r, y, Mth.cos(a) * r));
        float fov = 42f - 6f * Ease.bump(t, COCK - 2, AIM_STANCE + 2);
        return CameraPose.lookAt(pos, hand, 0, fov);
    }

    private static CameraPose tossWide(ClientSession s, float t, CameraPose g) {
        Vec3 pos = w(s, 1.35, 0.85, 2.0);
        return CameraPose.lookAt(pos, w(s, 0.2, 1.55, 0), 0, 55f);
    }

    private static CameraPose tossTrack(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, TOSS, PEAK);
        Vec3 pos = w(s, Mth.lerp(p, 1.5, 1.7), Mth.lerp(p, 0.95, 1.7), Mth.lerp(p, 1.65, 1.35));
        Vec3 gun = s.snapshot().frame().toWorld(airbornePosition(Math.max(t, TOSS)));
        return CameraPose.lookAt(pos, gun, 3f * p, Mth.lerp(p, 55f, 44f));
    }

    private static CameraPose peakCloseUp(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, PEAK, PEAK_END);
        Vec3 gun = s.snapshot().frame().toWorld(airbornePosition(t));
        float a = Mth.lerp(p, 62f, 30f) * Mth.DEG_TO_RAD;
        Vec3 pos = gun.add(rotateLocal(s, Mth.sin(a) * 0.95, -0.12 + 0.06 * p, Mth.cos(a) * 0.95));
        return CameraPose.lookAt(pos, gun, -4f + 3f * p, 33f);
    }

    private static CameraPose lowAngle(ClientSession s, float t, CameraPose g) {
        float push = Ease.outCubic(Ease.range(t, CATCH, AIM));
        Vec3 pos = w(s, Mth.lerp(push, 1.2, 0.95), Mth.lerp(push, 0.32, 0.45), Mth.lerp(push, 1.95, 1.5));
        Vec3 gun = s.snapshot().frame().toWorld(airbornePosition(Math.min(t, CATCH)));
        Vec3 look = SheriffScript.lerp(w(s, 0.3, 1.95, 0.3), gun, 0.35 * (1 - Ease.range(t, CATCH, CATCH + 4)));
        return CameraPose.lookAt(pos, look, 2f, 50f - 4f * push);
    }

    private static CameraPose barrelCloseUp(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, AIM, CUT_PROFILE);
        Vec3 pos = w(s, 0.66, 1.5, Mth.lerp(p, 0.42, 0.55));
        return CameraPose.lookAt(pos, targetChest(s, t), -3f, 40f);
    }

    private static CameraPose sideProfile(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, CUT_PROFILE, CUT_TARGET_POV);
        double d = pushed(s);
        Vec3 pos = w(s, 2.9, 1.45, d * 0.35 + 0.15 * p);
        return CameraPose.lookAt(pos, w(s, 0, 1.38, d * 0.38), 0, 46f - 3f * p);
    }

    private static CameraPose targetPov(ClientSession s, float t, CameraPose g) {
        double d = pushed(s);
        Vec3 pos = w(s, 0.05, s.snapshot().targetHeight() * 0.9, d - 0.25);
        return CameraPose.lookAt(pos, w(s, 0.15, 1.5, 0), 0, 38f - 4f * Ease.range(t, CUT_TARGET_POV, CUT_HERO));
    }

    private static CameraPose heroLow(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, CUT_HERO, FIRE_PHASE + 1);
        Vec3 pos = w(s, -0.85, 0.32, 1.25 - 0.1 * p);
        return CameraPose.lookAt(pos, w(s, 0.05, 1.55, 0), -4f, 48f);
    }

    private static CameraPose fireWide(ClientSession s, float t, CameraPose g) {
        double d = pushed(s);
        float kick = Ease.bump(t, FIRE, FIRE + 4);
        Vec3 pos = w(s, 3.7 - 0.2 * Ease.range(t, FIRE_PHASE, FINISH), 1.3, d * 0.5);
        return CameraPose.lookAt(pos, w(s, 0, 1.2, d * 0.5), 0, 56f - 7f * kick);
    }

    private static CameraPose smokeCloseUp(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, FINISH, LOWER + 4);
        Vec3 muzzle = w(s, MUZZLE_AT_AIM.x, MUZZLE_AT_AIM.y, MUZZLE_AT_AIM.z);
        Vec3 pos = w(s, 0.95 + 0.1 * p, 1.52 + 0.06 * p, 0.62 + 0.1 * p);
        return CameraPose.lookAt(pos, muzzle.add(0, 0.06 * p, 0), 0, 36f);
    }

    private static CameraPose pullAway(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, LOWER + 4, END));
        float a = Mth.lerp(p, 38f, 20f) * Mth.DEG_TO_RAD;
        double r = Mth.lerp(p, 2.3, 4.6);
        Vec3 pos = w(s, Mth.sin(a) * r, Mth.lerp(p, 1.45, 2.6), Mth.cos(a) * r);
        return CameraPose.lookAt(pos, w(s, 0, 1.15, 0), 0, 50f);
    }

    /** Rotates a local-frame offset into world space (no translation). */
    private static Vec3 rotateLocal(ClientSession s, double x, double y, double z) {
        AnchorFrame f = s.snapshot().frame();
        return f.toWorld(x, y, z).subtract(f.origin());
    }

    // ------------------------------------------------------------------ choreography
    @Override
    public CameraPose camera(ClientSession session, float t, CameraPose gameplay) {
        return SHOTS.evaluate(session, t, gameplay);
    }

    @Override
    public void onTick(ClientSession s, int tick) {
        switch (tick) {
            case CONTACT -> s.shake().add(0.5f);
            case DRAW_CATCH, GRIP_CATCH -> s.shake().add(0.12f);
            case CATCH -> s.shake().add(0.42f);
            case FIRE -> s.shake().add(0.95f);
            case IMPACT -> s.shake().add(0.35f);
            default -> {
            }
        }
        if (s.isLocal()) {
            WorldFx.onLocalTick(s, tick);
        }
    }

    @Override
    public void posePerformer(Rig rig, ClientSession s, float t) {
        float blendIn = Ease.range(t, 0, 3);
        float blendOut = 1 - Ease.range(t, RESTORE + 4, END);
        float wgt = blendIn * blendOut;

        float headPitch = HEAD_PITCH.sample(t);
        if (t >= TOSS - 1 && t <= CATCH + 2) {
            Vec3 gun = airbornePosition(Mth.clamp(t, TOSS, CATCH));
            double dy = gun.y - 1.6;
            double horizontal = Math.max(0.1, Math.hypot(gun.x, gun.z));
            float look = (float) -Math.atan2(dy, horizontal);
            float follow = Ease.range(t, TOSS - 1, TOSS + 3) * (1 - Ease.range(t, CATCH, CATCH + 2));
            headPitch = Mth.lerp(follow, headPitch, look);
        }
        Rig.blend(rig.head(), headPitch, HEAD_YAW.sample(t), 0, wgt);
        Rig.blend(rig.body(), 0, 0.12f * Ease.bump(t, DRAW, FLOURISH) - 0.1f * Ease.bump(t, AIM, FINISH), 0, wgt);
        Rig.blend(rig.rightArm(), R_ARM_X.sample(t) + aimPitch(t, rig), R_ARM_Y.sample(t), R_ARM_Z.sample(t), wgt);
        Rig.blend(rig.leftArm(), L_ARM_X.sample(t), 0.1f * Ease.range(t, AIM, AIM + 4), L_ARM_Z.sample(t), wgt);
        float stance = STANCE.sample(t);
        Rig.blend(rig.rightLeg(), -0.32f * stance, 0, 0.08f * stance, wgt);
        Rig.blend(rig.leftLeg(), 0.24f * stance, 0, -0.08f * stance, wgt);
    }

    /** Small aim correction so the barrel lines up with the target's chest during the aim phases. */
    private static float aimPitch(float t, Rig rig) {
        float aiming = Ease.range(t, AIM, AIM + 2) * (1 - Ease.range(t, LOWER, LOWER + 3));
        return 0.06f * aiming;
    }

    @Override
    public void poseTarget(Rig rig, ClientSession s, float t) {
        if (t < CONTACT) {
            return;
        }
        float flail = Ease.bump(t, CONTACT, STUMBLE_END + 2);
        float surrender = Ease.inOutCubic(Ease.range(t, AIM + 1, AIM + 8)) * (1 - Ease.range(t, IMPACT, IMPACT + 1));
        float dazed = Ease.range(t, STUMBLE_END, STUMBLE_END + 4) * (1 - surrender);
        float w = Math.max(flail, Math.max(dazed, surrender));
        if (w <= 0) {
            return;
        }
        float sway = Mth.sin(t * 0.3f);
        float armX = -1.35f * flail - 0.25f * dazed - 2.75f * surrender;
        float armZ = 0.65f * flail + 0.08f * dazed + 0.38f * surrender;
        Rig.blend(rig.rightArm(), armX + 0.15f * sway * dazed, 0, armZ, w);
        Rig.blend(rig.leftArm(), armX - 0.15f * sway * dazed, 0, -armZ, w);
        Rig.blend(rig.head(), 0.35f * flail - 0.12f * surrender, 0, 0.12f * sway * dazed, w);
    }

    @Override
    public float[] performerLean(ClientSession s, float t) {
        return new float[] {LEAN.sample(t), 0};
    }

    @Override
    public Vec3 performerOffset(ClientSession s, float t) {
        return new Vec3(0, 0, STEP.sample(t));
    }

    @Override
    public WeaponState weapon(ClientSession s, float t) {
        if (t < DRAW_PULL + 1 || t >= HOLSTER + 3) {
            return WeaponState.HOLSTERED;
        }
        if (t >= TOSS && t < CATCH) {
            return new WeaponState(WeaponState.Placement.AIR, 0, 0, 0, 0, 25f * (t - TOSS), 1f);
        }
        float spin = 1080f * Ease.inOutSine(Ease.range(t, DRAW_SPIN, DRAW_CATCH))
            + 720f * Ease.inOutSine(Ease.range(t, FLOURISH, WRIST_ROLL))
            + 720f * Ease.inOutSine(Ease.range(t, FINAL_SPIN, HOLSTER));
        float roll = 360f * Ease.inOutCubic(Ease.range(t, WRIST_ROLL, BACK_FLIP));
        float flip = -360f * Ease.inOutCubic(Ease.range(t, BACK_FLIP, GRIP_CATCH));
        float lift = 0.35f * Ease.bump(t, BACK_FLIP, GRIP_CATCH);
        float cylinder = 780f * Ease.outCubic(Ease.range(t, CYLINDER_ROLL, AIM_STANCE)) + 60f * Ease.range(t, AIM + 2, AIM + 4)
            + 60f * Ease.range(t, FIRE + 3, FIRE + 6);
        float hammer;
        if (t < COCK) {
            hammer = 0;
        } else if (t < FIRE) {
            hammer = Ease.outBack(Ease.range(t, COCK, COCK + 2));
        } else {
            hammer = 0;
        }
        return new WeaponState(WeaponState.Placement.HAND, spin, roll, flip, lift, cylinder, hammer);
    }

    @Override
    public void renderWorld(ClientSession s, float t, PoseStack poseStack, MultiBufferSource buffers, Camera camera) {
        FinisherSnapshot snap = s.snapshot();
        AnchorFrame f = snap.frame();
        if (t >= TOSS && t < CATCH) {
            Vec3 pos = f.toWorld(airbornePosition(t));
            WorldFx.renderAirborneRevolver(poseStack, buffers, camera, pos, f.yaw(), airborneSpin(t), weapon(s, t));
            float glint = Ease.bump(t, PEAK - 2, PEAK_END + 2);
            if (glint > 0) {
                WorldFx.renderGlint(poseStack, buffers, camera, pos.add(0, 0.05, 0), 0.35f * glint, t);
            }
        }
        if (t >= FIRE && t < FIRE + 1.6f) {
            WorldFx.renderMuzzleFlash(poseStack, buffers, camera, muzzleWorld(snap), impactWorld(snap), 1 - (t - FIRE) / 1.6f);
        }
        if (t >= FIRE && t < FIRE + 9) {
            WorldFx.renderTracer(poseStack, buffers, camera, muzzleWorld(snap), impactWorld(snap), 1 - (t - FIRE) / 9f);
        }
        if (t >= IMPACT && t < IMPACT + 12) {
            float p = (t - IMPACT) / 12f;
            WorldFx.renderShockwave(poseStack, buffers, camera, snap.targetEnd().add(0, 0.06, 0), 0.3f + 3.4f * Ease.outCubic(p), 1 - p);
        }
    }

    @Override
    public Grade grade(ClientSession s, float t) {
        float on = Ease.inOutSine(Ease.range(t, CONTACT - 1, CONTACT + 7)) * (1 - Ease.inOutSine(Ease.range(t, RESTORE, END - 2)));
        float slow = Ease.inOutSine(Ease.range(t, TOSS, TOSS + 6)) * (1 - Ease.range(t, CATCH - 1, CATCH + 2));
        float post = Ease.range(t, IMPACT, IMPACT + 3) * (1 - Ease.range(t, LOWER + 6, RESTORE + 6));
        float saturation = 1 - on * (0.18f + 0.62f * slow + 0.25f * post);
        float flash = 0;
        if (t >= FIRE && t < FIRE + 3) {
            flash = 0.85f * (1 - (t - FIRE) / 3f);
        } else if (t >= CATCH && t < CATCH + 2) {
            flash = 0.12f * (1 - (t - CATCH) / 2f);
        }
        float title = Ease.inOutSine(Ease.range(t, FINISH + 6, FINISH + 12)) * (1 - Ease.inOutSine(Ease.range(t, HOLSTER, HOLSTER + 6)));
        float duck = Ease.range(t, FIRE, FIRE + 2) * (1 - Ease.range(t, RESTORE, END));
        return new Grade(saturation, 0.6f * on, 1 + 0.14f * on + 0.08f * slow, on * (0.5f + 0.3f * slow), flash,
            0.045f * on, on, title, duck);
    }
}
