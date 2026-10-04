package dev.finalframe.client.camera;

import dev.finalframe.client.choreo.Grade;
import dev.finalframe.client.render.ColorGrade;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.Ease;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the vanilla {@link Camera} while the local player performs a finisher. Applied at the end of
 * {@code Camera.setup}, so the vanilla first-person pose is available as the blend source and target.
 */
public final class CinematicCamera {
    private static final double DETACH_DISTANCE = 0.35;
    private static final double WALL_MARGIN = 0.18;

    private static boolean active;
    private static boolean detached;
    private static float fov = 70f;
    private static float smoothedClip = -1;

    private CinematicCamera() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Whether the camera has left the player's head far enough to render their body. */
    public static boolean isDetached() {
        return active && detached;
    }

    public static float fov() {
        return fov;
    }

    public interface Access {
        void finalframe$place(Vec3 position, float yaw, float pitch, float roll);
    }

    public static void apply(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientSession s = ClientFinisherManager.INSTANCE.local();
        if (s == null || mc.player == null || camera.getEntity() != mc.player) {
            if (active) {
                ColorGrade.update(Grade.NEUTRAL);
            }
            active = false;
            detached = false;
            smoothedClip = -1;
            return;
        }

        Vec3 eye = camera.getPosition();
        CameraPose gameplay = new CameraPose(eye, camera.getYRot(), camera.getXRot(), 0f, mc.options.fov().get().floatValue(), null);
        float t = s.time(partialTick);
        CameraPose pose = s.choreography().camera(s, t, gameplay);
        s.rememberPose(pose);
        if (s.isAborting()) {
            CameraPose from = s.lastCinematicPose() != null ? s.lastCinematicPose() : gameplay;
            pose = CameraPose.blend(from, gameplay, Ease.inOutCubic(s.abortProgress(partialTick)));
        }

        pose = avoidWalls(mc, pose);
        float[] shake = s.shake().sample(s.tick() + partialTick, partialTick);
        pose = pose.offset(shake[0], shake[1], shake[2]);

        ((Access) camera).finalframe$place(pose.position(), pose.yaw(), pose.pitch(), pose.roll());
        fov = pose.fov();
        detached = pose.position().distanceToSqr(mc.player.getEyePosition(partialTick)) > DETACH_DISTANCE * DETACH_DISTANCE;
        active = true;

        Grade grade = s.choreography().grade(s, t);
        if (s.isAborting()) {
            grade = fade(grade, 1f - s.abortProgress(partialTick));
        }
        ColorGrade.update(grade);
    }

    /** Pulls the camera toward its focus when a block would sit between them, smoothing the change. */
    private static CameraPose avoidWalls(Minecraft mc, CameraPose pose) {
        if (pose.focus() == null || mc.level == null) {
            smoothedClip = -1;
            return pose;
        }
        Vec3 focus = pose.focus();
        Vec3 wanted = pose.position();
        double full = wanted.distanceTo(focus);
        if (full < 1.0E-3) {
            return pose;
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(focus, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        double allowed = hit.getType() == HitResult.Type.MISS ? full : Math.max(0.3, hit.getLocation().distanceTo(focus) - WALL_MARGIN);
        float target = (float) (allowed / full);
        smoothedClip = smoothedClip < 0 || target < smoothedClip ? target : smoothedClip + (target - smoothedClip) * 0.2f;
        if (smoothedClip >= 0.999f) {
            return pose;
        }
        Vec3 pos = focus.add(wanted.subtract(focus).scale(smoothedClip));
        return pose.moved(pos);
    }

    private static Grade fade(Grade g, float w) {
        return new Grade(1 + (g.saturation() - 1) * w, g.warmth() * w, 1 + (g.contrast() - 1) * w, g.vignette() * w,
            g.flash() * w, g.grain() * w, g.letterbox() * w, g.title() * w, g.duck() * w);
    }
}
