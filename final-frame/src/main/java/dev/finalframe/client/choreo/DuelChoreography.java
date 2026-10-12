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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Last Duel. A standoff staged like a western, with things in it that do not add up: a cylinder that turns by
 * itself, a casing on the ground before anyone fires, one gunshot for two guns, heard before either flashes, a
 * sheriff who is not quite where he was a cut ago, and a bullet trail that comes from behind the target.
 */
public final class DuelChoreography implements Choreography {
    public static final DuelChoreography INSTANCE = new DuelChoreography();
    private static final float PI = (float) Math.PI;

    private static final Track R_ARM_X = new Track()
        .key(0, -0.15f).key(CONTACT - 4, 0.4f).key(CONTACT, -1.45f).key(CONTACT + 5, -0.2f).key(DRAW, -0.2f)
        .key(DRAW + 4, -PI / 2).key(FIRE, -PI / 2).key(FIRE + 1, -2.1f).key(FIRE + 6, -1.6f).key(LOWER, -1.55f)
        .key(LOWER + 6, -0.85f).key(HOLSTER - 2, -0.85f).key(HOLSTER + 2, 0.25f).key(HOLSTER + 6, 0);
    private static final Track L_ARM_X = new Track()
        .key(0, 0).key(CONTACT - 4, 0.4f).key(CONTACT, -1.45f).key(CONTACT + 5, -0.1f).key(SECOND_DRAW, 0.2f)
        .key(SECOND_DRAW + 4, -0.9f).key(THROW - 2, -0.6f).key(THROW + 1, -1.7f).key(THROW + 6, -0.2f).key(STANDOFF, 0);
    private static final Track LEAN = new Track()
        .key(0, 0).key(CONTACT - 2, -5).key(CONTACT, 12).key(CONTACT + 6, 0).key(FIRE, 0).key(FIRE + 1, -5).key(FIRE + 6, 0);

    private static final ShotList SHOTS = new ShotList()
        .cut(0, (s, t, g) -> g)
        .blend(4, 10, DuelChoreography::approach)
        .cut(CONTACT - 3, DuelChoreography::shoveWide)
        .blend(SECOND_DRAW, 5, DuelChoreography::overShoulderLeft)
        .cut(THROW + 2, DuelChoreography::gunFlight)
        .cut(STANDOFF, DuelChoreography::standoff)
        .cut(HAND_P, DuelChoreography::handPerformer)
        .cut(HAND_T, DuelChoreography::handTarget)
        .cut(POV_P, DuelChoreography::povPerformer)
        .cut(POV_T, DuelChoreography::povTarget)
        .cut(RAPID, (s, t, g) -> eyes(s, t, true))
        .cut(RAPID + 4, (s, t, g) -> hand(s, t, false))
        .cut(RAPID + 8, (s, t, g) -> eyes(s, t, false))
        .cut(RAPID + 12, (s, t, g) -> hand(s, t, true))
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

    private static double pz(ClientSession s, float t) {
        return DuelScript.performer(s.snapshot(), t).z;
    }

    private static double tz(ClientSession s) {
        return endDistance(s.snapshot());
    }

    /** The sheriff is never quite where the previous cut left him. */
    private static double drift(float t) {
        if (t >= POV_T && t < RAPID) {
            return 0.38;
        }
        if (t >= RAPID + 8 && t < DRAW) {
            return -0.22;
        }
        return 0;
    }

    private static Vec3 casingFlight(ClientSession s, float t) {
        float p = Ease.range(t, FIRE, BLACK + 4);
        Vec3 eject = performerMuzzle(s.snapshot()).add(0.1, -0.1, -0.75);
        return eject.add(0.55 * p, 0.9 * Mth.sin(p * PI * 0.85f), -0.25 * p);
    }

    private static Vec3 impossibleCasing(ClientSession s) {
        return new Vec3(-0.65, 0.03, tz(s) - 0.55);
    }

    // ------------------------------------------------------------------ shots
    private static CameraPose approach(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        float p = Ease.range(t, 4, CONTACT);
        Vec3 pos = w(s, 1.15, Mth.lerp(p, 0.3, 0.9), z + 2.1);
        return CameraPose.lookAt(pos, w(s, 0, Mth.lerp(p, 0.35, 1.35), z), 0, 55f);
    }

    private static CameraPose shoveWide(ClientSession s, float t, CameraPose g) {
        double mid = (pz(s, t) + tz(s)) * 0.5;
        Vec3 pos = w(s, 4.2, 1.35, mid - 0.4);
        return CameraPose.lookAt(pos, w(s, 0, 1.1, mid), 0, 55f);
    }

    private static CameraPose overShoulderLeft(ClientSession s, float t, CameraPose g) {
        double z = pz(s, t);
        Vec3 pos = w(s, -0.95, 1.75, z - 1.45);
        return CameraPose.lookAt(pos, w(s, -0.2, 1.2, (z + tz(s)) * 0.5), 2f, 58f);
    }

    private static CameraPose gunFlight(ClientSession s, float t, CameraPose g) {
        Vec3 gun = w(s, thrownGun(s.snapshot(), Math.min(t, CATCH)));
        double mid = (pz(s, t) + tz(s)) * 0.5;
        Vec3 pos = w(s, -2.6, 1.2, mid - 0.6);
        return CameraPose.lookAt(pos, gun, 0, 48f);
    }

    private static CameraPose standoff(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, STANDOFF, HAND_P);
        double mid = (pz(s, t) + tz(s)) * 0.5;
        Vec3 pos = w(s, 9.5 - 1.2 * p, 0.55, mid);
        return CameraPose.lookAt(pos, w(s, 0, 1.15, mid), 0, 44f);
    }

    private static CameraPose handPerformer(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HAND_P, HAND_T);
        Vec3 hand = w(s, 0.4, 0.72, pz(s, t) + 0.25);
        Vec3 pos = hand.add(w(s, 0.75, 0.08, 0.3 - 0.1 * p).subtract(s.snapshot().frame().origin()));
        return CameraPose.lookAt(pos, hand, 0, 32f - 3f * p);
    }

    private static CameraPose handTarget(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HAND_T, POV_P);
        Vec3 hand = w(s, targetHand(s.snapshot()));
        Vec3 look = dev.finalframe.sheriff.SheriffScript.lerp(hand, w(s, impossibleCasing(s)), 0.35 + 0.1 * p);
        Vec3 pos = w(s, -1.3, 0.75, tz(s) - 1.0);
        return CameraPose.lookAt(pos, look, 0, 34f - 3f * p);
    }

    private static CameraPose povPerformer(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, POV_P, POV_T));
        Vec3 eyes = w(s, 0, 1.62, pz(s, t));
        return CameraPose.lookAt(eyes, w(s, targetEyes(s.snapshot())), 0, Mth.lerp(p, 62f, 22f));
    }

    private static CameraPose povTarget(ClientSession s, float t, CameraPose g) {
        float p = Ease.inOutSine(Ease.range(t, POV_T, RAPID));
        Vec3 eyes = w(s, targetEyes(s.snapshot()));
        return CameraPose.lookAt(eyes, w(s, drift(t), 1.6, pz(s, t)), 0, Mth.lerp(p, 62f, 20f));
    }

    private static CameraPose eyes(ClientSession s, float t, boolean performer) {
        if (performer) {
            Vec3 face = w(s, drift(t), 1.62, pz(s, t));
            return CameraPose.lookAt(face.add(w(s, 0.05, 0.04, 1.0).subtract(s.snapshot().frame().origin())), face, 0, 24f);
        }
        Vec3 face = w(s, targetEyes(s.snapshot()));
        return CameraPose.lookAt(face.add(w(s, -0.05, 0.04, -1.0).subtract(s.snapshot().frame().origin())), face, 0, 24f);
    }

    private static CameraPose hand(ClientSession s, float t, boolean performer) {
        if (performer) {
            Vec3 hand = w(s, 0.4 + drift(t), 0.72, pz(s, t) + 0.25);
            return CameraPose.lookAt(w(s, 0.95 + drift(t), 0.85, pz(s, t) + 0.6), hand, 3f, 22f);
        }
        Vec3 hand = w(s, targetHand(s.snapshot()));
        return CameraPose.lookAt(w(s, -0.95, 0.85, tz(s) - 0.7), hand, -3f, 22f);
    }

    private static CameraPose profile(ClientSession s, float t, CameraPose g) {
        double mid = (pz(s, t) + tz(s)) * 0.5;
        float kick = Ease.bump(t, FIRE, FIRE + 4);
        Vec3 pos = w(s, 6.0, 0.9, mid);
        return CameraPose.lookAt(pos, w(s, 0, 1.3, mid), 0, 40f - 5f * kick);
    }

    private static CameraPose casingShot(ClientSession s, float t, CameraPose g) {
        Vec3 casing = w(s, casingFlight(s, t));
        return CameraPose.lookAt(casing.add(w(s, 0.55, 0.05, 0.45).subtract(s.snapshot().frame().origin())), casing, 8f, 30f);
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
            case CATCH -> s.shake().add(0.1f);
            case FIRE -> s.shake().add(1.0f);
            case FATAL -> s.shake().add(0.3f);
            default -> {
            }
        }
        if (s.isLocal() && tick == POV_P && FFConfig.get(FFConfig.DUCK_WORLD_AUDIO)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
    }

    @Override
    public void posePerformer(Rig rig, ClientSession s, float t) {
        float wgt = Ease.range(t, 0, 3) * (1 - Ease.range(t, RETURN, END - 6));
        boolean walking = (t > 2 && t < APPROACH_END) || (t >= WALK && t < WALK_END);
        float stride = walking ? 0.55f * Mth.sin(t * 0.45f) : 0;
        Rig.blend(rig.head(), 0.05f, 0, 0, wgt);
        Rig.blend(rig.rightArm(), R_ARM_X.sample(t) - 0.4f * stride * (t >= WALK ? 1 : 0), -0.05f, 0.05f, wgt);
        Rig.blend(rig.leftArm(), L_ARM_X.sample(t) + 0.4f * stride, 0, -0.06f, wgt);
        Rig.blend(rig.rightLeg(), stride, 0, 0.04f, wgt);
        Rig.blend(rig.leftLeg(), -stride, 0, -0.04f, wgt);
    }

    @Override
    public void poseTarget(Rig rig, ClientSession s, float t) {
        float flail = Ease.bump(t, CONTACT, STUMBLE_END + 2);
        float reach = Ease.bump(t, CATCH - 6, CATCH + 6);
        float aim = Ease.inOutCubic(Ease.range(t, DRAW, DRAW + 4)) * (1 - Ease.range(t, BLACK, REVEAL));
        float armX = -1.3f * flail - 1.2f * reach - (PI / 2) * aim - 0.5f * Ease.bump(t, FIRE, FIRE + 6);
        float w = Math.max(Math.max(flail, reach), Math.max(aim, Ease.range(t, CATCH, CATCH + 4)));
        if (w > 0) {
            Rig.blend(rig.rightArm(), armX, 0, 0.1f, w);
            Rig.blend(rig.leftArm(), -1.3f * flail, 0, -0.5f * flail, flail);
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
        if (t >= HOLSTER + 2) {
            return WeaponState.HOLSTERED;
        }
        float cylinder = 60f * Ease.outBack(Ease.range(t, SELF_CYLINDER, SELF_CYLINDER + 3))
            + 60f * Ease.range(t, FIRE + 2, FIRE + 5);
        float hammer = t >= DRAW + 2 && t < FIRE ? Ease.outBack(Ease.range(t, DRAW + 2, DRAW + 4)) : 0;
        float spin = 720f * Ease.inOutSine(Ease.range(t, SPIN, SPIN + 10));
        return new WeaponState(WeaponState.Placement.HAND, spin, 0, 0, 0, cylinder, hammer);
    }

    @Override
    public void renderWorld(ClientSession s, float t, PoseStack ps, MultiBufferSource buffers, Camera camera) {
        FinisherSnapshot snap = s.snapshot();
        AnchorFrame f = snap.frame();
        float grade = grade(s, t).black();
        if (grade > 0.98f) {
            return;
        }
        WeaponState second = new WeaponState(WeaponState.Placement.AIR, 0, 0, 0, 0, 0, t >= DRAW + 2 && t < FIRE ? 1 : 0);
        if (t >= SECOND_DRAW + 1 && t < THROW) {
            Vec3 hand = w(s, -0.42, 0.75 + 0.5 * Ease.range(t, SECOND_DRAW, THROW), pz(s, t) + 0.25);
            WorldFx.renderAirborneRevolver(ps, buffers, camera, hand, f.yaw(), -70f + 720f * Ease.inOutSine(Ease.range(t, SECOND_DRAW + 3, THROW)), second);
        } else if (t >= THROW && t < CATCH) {
            WorldFx.renderAirborneRevolver(ps, buffers, camera, w(s, thrownGun(snap, t)), f.yaw() + 90f, 900f * Ease.range(t, THROW, CATCH), second);
        } else if (t >= CATCH && t < REVEAL) {
            float aim = Ease.inOutCubic(Ease.range(t, DRAW, DRAW + 4));
            Vec3 rest = targetHand(snap);
            Vec3 aimed = targetAimMuzzle(snap).add(0, -0.08, 0.55);
            Vec3 at = rest.add(aimed.subtract(rest).scale(aim));
            float kick = 30f * Ease.bump(t, FIRE, FIRE + 5);
            WorldFx.renderAirborneRevolver(ps, buffers, camera, w(s, at), f.yaw() + 180f, Mth.lerp(aim, -75f, 0f) + kick, second);
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

    private static void renderCasing(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, float yaw, float spin) {
        Vec3 cam = camera.getPosition();
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        float r = 0.022f;
        float h = 0.06f;
        box(vc, m, -r, -h, -r, r, h, r, 0.86f, 0.64f, 0.26f);
        box(vc, m, -r * 1.2f, -h - 0.008f, -r * 1.2f, r * 1.2f, -h, r * 1.2f, 0.7f, 0.5f, 0.2f);
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
        float on = Ease.inOutSine(Ease.range(t, 4, 16)) * (1 - Ease.inOutSine(Ease.range(t, RETURN, END - 4)));
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
        float duck = Ease.inOutSine(Ease.range(t, HAND_P, RAPID)) * (1 - Ease.range(t, WALK, END));
        return new Grade(saturation, 0.55f * on * (1 - 0.5f * tension), 1 + 0.12f * on + 0.1f * tension,
            on * (0.45f + 0.35f * tension), flash, 0.05f * on, on, 0, duck, black);
    }
}
