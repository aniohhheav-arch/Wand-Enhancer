package dev.finalframe.client.choreo;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.finalframe.client.anim.Rig;
import dev.finalframe.client.camera.CameraPose;
import dev.finalframe.client.session.ClientSession;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client presentation of a finisher: camera direction, body animation, weapon handling, world effects
 * and screen grade. Implementations are pure functions of the session and the fractional tick, which
 * keeps every client in lockstep with the server's timeline.
 */
public interface Choreography {
    /** Camera for the performing player. {@code gameplay} is the vanilla first-person pose to blend from/to. */
    CameraPose camera(ClientSession session, float t, CameraPose gameplay);

    /** Discrete per-tick cues (camera shake, local-only feedback). */
    default void onTick(ClientSession session, int tick) {
    }

    void posePerformer(Rig rig, ClientSession session, float t);

    void poseTarget(Rig rig, ClientSession session, float t);

    /** Whole-body lean of the performer: {pitch, roll} in degrees. */
    default float[] performerLean(ClientSession session, float t) {
        return new float[] {0, 0};
    }

    /** Visual offset of the performer from the anchor, in the anchor's local frame. */
    default Vec3 performerOffset(ClientSession session, float t) {
        return Vec3.ZERO;
    }

    /** Head yaw of the performer relative to the anchor yaw (degrees). */
    default float performerHeadYaw(ClientSession session, float t) {
        return 0;
    }

    WeaponState weapon(ClientSession session, float t);

    void renderWorld(ClientSession session, float t, PoseStack poseStack, MultiBufferSource buffers, Camera camera);

    Grade grade(ClientSession session, float t);
}
