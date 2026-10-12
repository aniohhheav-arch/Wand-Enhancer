package dev.finalframe.client.choreo;

import static dev.finalframe.sheriff.ImpossibleScript.*;

import com.mojang.blaze3d.vertex.PoseStack;
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
import dev.finalframe.sheriff.ImpossibleScript;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Impossible Shot. The first shot missed. The bullet does not stay where it landed, the target hears shots that are
 * never fired, the revolver flashes with no hand on the trigger, the sheriff is briefly in places he cannot be, and he
 * holsters without firing again. Then something hits.
 */
public final class ImpossibleShotChoreography implements Choreography {
    public static final ImpossibleShotChoreography INSTANCE = new ImpossibleShotChoreography();
    private static final float PI = (float) Math.PI;

    private static final Track R_ARM_X = new Track()
        .key(0, -PI / 2).key(LOWERED - 12, -PI / 2).key(LOWERED, -0.12f).key(SPIN - 2, -0.12f).key(SPIN + 1, -0.95f)
        .key(SPIN + 10, -0.95f).key(HOLSTER - 2, -0.4f).key(HOLSTER, 0.22f).key(HOLSTER + 4, 0);
    private static final Track L_ARM_X = new Track()
        .key(0, -0.45f).key(LOWERED, 0).key(END, 0);

    private static final ShotList SHOTS = new ShotList()
        .cut(0, (s, t, g) -> g)
        .cut(1, ImpossibleShotChoreography::overShoulder)
        .cut(EARLY, ImpossibleShotChoreography::bulletCloseUp)
        .cut(POV, ImpossibleShotChoreography::targetPov)
        .cut(EAR, ImpossibleShotChoreography::earCloseUp)
        .cut(CYLINDER, ImpossibleShotChoreography::cylinderCloseUp)
        .cut(GROUND, ImpossibleShotChoreography::groundCloseUp)
        .cut(PATHS, ImpossibleShotChoreography::highWide)
        .cut(HEARD, ImpossibleShotChoreography::targetMedium)
        .cut(LOW_SHOT, ImpossibleShotChoreography::lowHand)
        .cut(SHIFT, ImpossibleShotChoreography::lockedSide)
        .cut(HOLSTER_SCENE, ImpossibleShotChoreography::sheriffMedium)
        .cut(TEMPLE, ImpossibleShotChoreography::templeCloseUp)
        .cut(REVEAL, ImpossibleShotChoreography::reveal)
        .blend(RETURN, END - RETURN - 2, (s, t, g) -> g);

    private ImpossibleShotChoreography() {
    }

    // ------------------------------------------------------------------ geometry
    private static Vec3 w(ClientSession s, Vec3 local) {
        return s.snapshot().frame().toWorld(local);
    }

    private static Vec3 w(ClientSession s, double x, double y, double z) {
        return s.snapshot().frame().toWorld(x, y, z);
    }

    private static Vec3 dir(ClientSession s, double x, double y, double z) {
        AnchorFrame f = s.snapshot().frame();
        return f.toWorld(x, y, z).subtract(f.origin());
    }

    private static Vec3 head(ClientSession s) {
        return new Vec3(0, s.snapshot().targetHeight() * 0.86, tz(s.snapshot()));
    }

    /** Where the sheriff appears to be: on his mark, except when he plainly is not. */
    private static Vec3 shown(ClientSession s, float t) {
        double tz = tz(s.snapshot());
        if (t >= SHIFT && t < SHIFT_BACK) {
            return new Vec3(0.25, 0, tz + 1.3);
        }
        if (t >= SHIFT_SIDE && t < SHIFT_HOME) {
            return new Vec3(-1.5, 0, tz - 0.2);
        }
        return ImpossibleScript.performer(s.snapshot(), t);
    }

    /** The bullet is wherever this cut needs it to be. Returns null where it is (apparently) inside the gun. */
    private static Vec3 bullet(ClientSession s, float t) {
        FinisherSnapshot snap = s.snapshot();
        if (t >= EAR && t < CYLINDER) {
            return new Vec3(-0.38, snap.targetHeight() * 0.9, tz(snap) - 0.05);
        }
        if (t >= CYLINDER && t < GROUND) {
            return null;
        }
        if (t >= GROUND && t < PATHS) {
            return new Vec3(0.75, 0.04, tz(snap) - 0.7);
        }
        return bulletPath(snap, t);
    }

    private static Vec3 cylinderPos(ClientSession s) {
        return new Vec3(0.36, 0.76, 0.32);
    }

    // ------------------------------------------------------------------ shots
    private static CameraPose overShoulder(ClientSession s, float t, CameraPose g) {
        Vec3 pos = w(s, 1.25, 1.85, -2.1);
        float glance = Ease.inOutSine(Ease.range(t, LOOK, LOOK + 6)) * (1 - Ease.inOutSine(Ease.range(t, LOOK_BACK, LOOK_BACK + 8)));
        Vec3 look = w(s, head(s)).add(w(s, impact(s.snapshot())).subtract(w(s, head(s))).scale(0.35 * glance));
        return CameraPose.lookAt(pos, look, 0, 52f);
    }

    private static CameraPose bulletCloseUp(ClientSession s, float t, CameraPose g) {
        FinisherSnapshot snap = s.snapshot();
        Vec3 b = w(s, bulletPath(snap, t));
        Vec3 side = dir(s, shotDir(snap).z, 0, -shotDir(snap).x).normalize();
        float orbit = Ease.inOutSine(Ease.range(t, EARLY, POV));
        Vec3 pos = b.add(side.scale(0.55 - 0.15 * orbit)).add(0, 0.12, 0).subtract(dir(s, shotDir(snap).x, 0, shotDir(snap).z).scale(0.25 + 0.2 * orbit));
        return CameraPose.lookAt(pos, b, -6f + 6f * orbit, 30f);
    }

    private static CameraPose targetPov(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, POV, EAR);
        Vec3 eyes = w(s, head(s)).add(dir(s, 0.12, 0, -0.25));
        return CameraPose.lookAt(eyes, w(s, 0, 1.45, 0), 2f * Mth.sin(t * 0.07f), 48f - 8f * p);
    }

    private static CameraPose earCloseUp(ClientSession s, float t, CameraPose g) {
        Vec3 b = w(s, bullet(s, t));
        return CameraPose.lookAt(b.add(dir(s, -0.55, 0.05, -0.45)), b.add(dir(s, 0.2, 0, 0)), 0, 32f);
    }

    private static CameraPose cylinderCloseUp(ClientSession s, float t, CameraPose g) {
        Vec3 c = w(s, cylinderPos(s));
        return CameraPose.lookAt(c.add(dir(s, 0.5, 0.12, 0.35)), c, 4f, 26f);
    }

    private static CameraPose groundCloseUp(ClientSession s, float t, CameraPose g) {
        Vec3 b = w(s, bullet(s, t));
        float p = Ease.range(t, GROUND, PATHS);
        return CameraPose.lookAt(b.add(dir(s, 0.6 - 0.1 * p, 0.16, -0.4)), b.add(0, 0.05, 0), 0, 34f);
    }

    private static CameraPose highWide(ClientSession s, float t, CameraPose g) {
        double tz = tz(s.snapshot());
        float p = Ease.inOutSine(Ease.range(t, PATHS, HEARD));
        Vec3 pos = w(s, 6.5 - 0.8 * p, 4.6 - 0.6 * p, tz * 0.45);
        return CameraPose.lookAt(pos, w(s, 0, 1.0, tz * 0.6), 0, 50f);
    }

    private static CameraPose targetMedium(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HEARD, LOW_SHOT);
        Vec3 h = w(s, head(s));
        return CameraPose.lookAt(h.add(dir(s, 1.2, -0.15, -1.7 + 0.25 * p)), h.add(0, -0.1, 0), 0, 40f - 4f * p);
    }

    private static CameraPose lowHand(ClientSession s, float t, CameraPose g) {
        Vec3 hand = w(s, 0.38, 0.72, 0.35);
        return CameraPose.lookAt(w(s, 1.5, 0.45, 1.6), hand, -3f, 36f);
    }

    private static CameraPose lockedSide(ClientSession s, float t, CameraPose g) {
        double tz = tz(s.snapshot());
        return CameraPose.lookAt(w(s, 6.8, 1.5, tz * 0.6), w(s, 0, 1.1, tz * 0.6), 0, 48f);
    }

    private static CameraPose sheriffMedium(ClientSession s, float t, CameraPose g) {
        float p = Ease.range(t, HOLSTER_SCENE, TEMPLE);
        return CameraPose.lookAt(w(s, 2.1, 1.35, 2.5 - 0.25 * p), w(s, 0.15, 1.15, 0), 0, 42f);
    }

    private static CameraPose templeCloseUp(ClientSession s, float t, CameraPose g) {
        Vec3 b = w(s, bulletPath(s.snapshot(), t));
        Vec3 temple = w(s, temple(s.snapshot()));
        return CameraPose.lookAt(b.add(dir(s, 0.15, 0.04, -0.42)), temple.add(b.subtract(temple).scale(0.5)), 0, 30f);
    }

    private static CameraPose reveal(ClientSession s, float t, CameraPose g) {
        double tz = tz(s.snapshot());
        Vec3 pos = w(s, 1.8, 2.3, tz + 2.6);
        return CameraPose.lookAt(pos, w(s, 0, 1.1, ImpossibleScript.performer(s.snapshot(), t).z), 0, 46f);
    }

    // ------------------------------------------------------------------ choreography
    @Override
    public CameraPose camera(ClientSession session, float t, CameraPose gameplay) {
        return SHOTS.evaluate(session, t, gameplay);
    }

    @Override
    public void onTick(ClientSession s, int tick) {
        switch (tick) {
            case HEARD_1, HEARD_2, HEARD_3 -> s.shake().add(0.35f);
            case UNTOUCHED -> s.shake().add(0.25f);
            case SHIFT, SHIFT_BACK, SHIFT_SIDE, SHIFT_HOME -> s.shake().add(0.06f);
            case FATAL -> s.shake().add(0.4f);
            default -> {
            }
        }
        if (s.isLocal() && tick == EARLY && FFConfig.get(FFConfig.DUCK_WORLD_AUDIO)) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
    }

    @Override
    public void posePerformer(Rig rig, ClientSession s, float t) {
        float wgt = 1 - Ease.range(t, RETURN, END - 4);
        boolean walking = t >= WALK && t < WALK_END;
        float stride = walking ? 0.55f * Mth.sin(t * 0.45f) : 0;
        Rig.blend(rig.head(), 0.05f, 0, 0, wgt);
        Rig.blend(rig.rightArm(), R_ARM_X.sample(t) - (walking ? 0.4f * stride : 0), -0.05f, 0.05f, wgt);
        Rig.blend(rig.leftArm(), L_ARM_X.sample(t) + 0.4f * stride, 0.1f * (1 - Ease.range(t, 0, LOWERED)), -0.06f, wgt);
        Rig.blend(rig.rightLeg(), stride, 0, 0.04f, wgt);
        Rig.blend(rig.leftLeg(), -stride, 0, -0.04f, wgt);
    }

    @Override
    public void poseTarget(Rig rig, ClientSession s, float t) {
        float flinch = Math.max(Ease.bump(t, HEARD_1, HEARD_1 + 6), Math.max(Ease.bump(t, HEARD_2, HEARD_2 + 6), Ease.bump(t, HEARD_3, HEARD_3 + 6)));
        float guard = Math.max(flinch, 0.6f * Ease.bump(t, SHIFT, SHIFT_HOME + 2));
        if (guard > 0) {
            Rig.blend(rig.rightArm(), -1.7f, 0, 0.35f, guard);
            Rig.blend(rig.leftArm(), -1.7f, 0, -0.35f, guard);
        }
    }

    @Override
    public Vec3 performerOffset(ClientSession s, float t) {
        return shown(s, t);
    }

    @Override
    public float performerBodyYaw(ClientSession s, float t) {
        if (t >= SHIFT && t < SHIFT_BACK) {
            return 180f;
        }
        if (t >= SHIFT_SIDE && t < SHIFT_HOME) {
            return -90f;
        }
        return 180f * Ease.inOutSine(Ease.range(t, TURN, WALK));
    }

    @Override
    public WeaponState weapon(ClientSession s, float t) {
        if (t >= HOLSTER + 2) {
            return WeaponState.HOLSTERED;
        }
        // The cylinder turns back one chamber while the gun hangs at his side, as if the round were never spent.
        float cylinder = -60f * Ease.outBack(Ease.range(t, CYLINDER + 2, CYLINDER + 5));
        float spin = 720f * Ease.inOutSine(Ease.range(t, SPIN, SPIN + 10));
        float hammer = t >= UNTOUCHED - 6 && t < UNTOUCHED ? 1 : 0;
        return new WeaponState(WeaponState.Placement.HAND, spin, 0, 0, 0, cylinder, hammer);
    }

    @Override
    public void renderWorld(ClientSession s, float t, PoseStack ps, MultiBufferSource buffers, Camera camera) {
        FinisherSnapshot snap = s.snapshot();
        if (grade(s, t).black() > 0.98f) {
            return;
        }
        if (t < BLACK) {
            Vec3 b = bullet(s, t);
            if (b != null) {
                Vec3 heading = t >= GROUND && t < PATHS ? new Vec3(1, 0, 0.3).normalize() : bulletHeading(snap, t);
                renderBullet(ps, buffers, camera, w(s, b), dir(s, heading.x, heading.y, heading.z));
            }
            if (t >= HUNG - 2 && t < HUNG + 4 || t >= CYLINDER && t < GROUND) {
                Vec3 at = t < CYLINDER ? w(s, bulletPath(snap, t)) : w(s, cylinderPos(s)).add(dir(s, 0.1, 0.05, 0.05));
                WorldFx.renderGlint(ps, buffers, camera, at, 0.06f, t);
            }
        }
        // Three trajectories, each ending in the same place; none of them is the one that was fired.
        if (t >= PATHS && t < HEARD) {
            Vec3 end = w(s, temple(snap));
            renderPath(ps, buffers, camera, w(s, 0.33, 1.36, 0.95), end, t, PATHS + 2);
            renderPath(ps, buffers, camera, w(s, impact(snap)), end, t, PATHS + 8);
            renderPath(ps, buffers, camera, w(s, -3.0, 9.0, tz(snap) + 4.0), end, t, PATHS + 14);
        }
        if (t >= UNTOUCHED && t < UNTOUCHED + 1.6f) {
            Vec3 muzzle = w(s, 0.36, 0.72, 0.62);
            WorldFx.renderMuzzleFlash(ps, buffers, camera, muzzle, w(s, 0.36, 0.2, 2.0), 1 - (t - UNTOUCHED) / 1.6f);
        }
        renderAfterimages(s, t, ps, buffers, camera);
    }

    private static void renderPath(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 from, Vec3 to, float t, float start) {
        if (t < start) {
            return;
        }
        float a = Ease.bump(t, start, start + 10);
        WorldFx.renderTracer(ps, buffers, camera, from, to, 0.75f * a);
    }

    private static void renderBullet(PoseStack ps, MultiBufferSource buffers, Camera camera, Vec3 at, Vec3 heading) {
        float yaw = (float) (Mth.atan2(-heading.x, heading.z) * Mth.RAD_TO_DEG);
        float elevation = (float) (Math.asin(Mth.clamp(heading.y, -1, 1)) * Mth.RAD_TO_DEG);
        DuelChoreography.renderSlug(ps, buffers, camera, at, yaw, 90f - elevation, 0.58f, 0.58f, 0.62f, 0.86f, 0.64f, 0.28f);
    }

    /** For a tick or two at a time the sheriff is also standing somewhere else. */
    private static void renderAfterimages(ClientSession s, float t, PoseStack ps, MultiBufferSource buffers, Camera camera) {
        Vec3 where = null;
        double tz = tz(s.snapshot());
        if (t >= GHOST_1 && t < GHOST_1 + 2) {
            where = new Vec3(1.7, 0, 0.9);
        } else if (t >= GHOST_2 && t < GHOST_2 + 1.5f) {
            where = new Vec3(-1.3, 0, 1.6);
        } else if (t >= GHOST_3 && t < GHOST_3 + 2) {
            where = new Vec3(0.35, 0, tz - 1.4);
        }
        if (where == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(s.snapshot().performerId());
        if (!(e instanceof LivingEntity performer)) {
            return;
        }
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        AnchorFrame f = s.snapshot().frame();
        Vec3 mark = f.toWorld(shown(s, t));
        Vec3 drawn = performer.getPosition(pt);
        Vec3 ghost = f.toWorld(where);
        Vec3 cam = camera.getPosition();
        // The performer render hook moves the model from its drawn position to its mark; pre-compensate for that.
        Vec3 call = ghost.subtract(mark).add(drawn).subtract(cam);
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(ghost.add(0, 1, 0)));
        mc.getEntityRenderDispatcher().render(performer, call.x, call.y, call.z, performer.getYRot(), pt, ps, buffers, light);
    }

    @Override
    public Grade grade(ClientSession s, float t) {
        float on = Ease.inOutSine(Ease.range(t, 2, 12)) * (1 - Ease.inOutSine(Ease.range(t, RETURN, END - 2)));
        float wrong = Ease.range(t, EARLY, EARLY + 4) * (1 - Ease.range(t, BLACK, REVEAL + 8));
        float saturation = 1 - on * (0.3f + 0.35f * wrong);
        float flash = t >= UNTOUCHED && t < UNTOUCHED + 2 ? 0.35f * (1 - (t - UNTOUCHED) / 2f) : 0;
        float black = 0;
        if (t >= BLACK && t < REVEAL) {
            black = 1;
        } else if (t >= REVEAL && t < REVEAL + 12) {
            black = 1 - Ease.inOutSine(Ease.range(t, REVEAL, REVEAL + 12));
        }
        float duck = Ease.range(t, EARLY - 2, EARLY + 6) * (1 - Ease.range(t, WALK, END));
        return new Grade(saturation, 0.08f * on, 1 + 0.16f * on, on * (0.55f + 0.2f * wrong), flash, 0.06f * on, on, 0, duck, black);
    }
}
