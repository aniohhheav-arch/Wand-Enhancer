package dev.infinitygauntlet.client.finisher;

import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Base of the finishers in which the caster holds their ground and works the target from where they stand. Gives
 * scripts the scene geometry and posing helpers: aim an arm at any point in the world, plant the feet, turn the head,
 * all blended over the vanilla pose.
 */
abstract class StationaryClient implements ClientFinishers.ClientScript {
    protected final ClientFinishers.Active a;
    protected final Vec3 d;
    protected final Vec3 right;
    protected final float yaw;
    protected final float hw;
    protected final float h;
    protected final Vec3 feet;
    protected final Vec3 core;
    protected final HumanoidArm gauntletArm;
    protected final float side;

    StationaryClient(ClientFinishers.Active a) {
        this.a = a;
        LivingEntity target = a.target();
        this.hw = target == null ? 0.3f : target.getBbWidth() * 0.5f;
        this.h = target == null ? 1.8f : target.getBbHeight();
        Vec3 dd = new Vec3(a.targetPos.x - a.casterStart.x, 0, a.targetPos.z - a.casterStart.z);
        this.d = dd.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dd.normalize();
        this.right = new Vec3(-d.z, 0, d.x);
        this.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        this.feet = a.targetPos;
        this.core = a.targetPos.add(0, h * 0.55, 0);
        Entity caster = a.caster();
        HumanoidArm arm = HumanoidArm.RIGHT;
        if (caster instanceof Player p) {
            arm = p.getMainHandItem().getItem() instanceof InfinityGauntletItem ? p.getMainArm() : p.getMainArm().getOpposite();
        }
        this.gauntletArm = arm;
        this.side = arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    static float smooth(float x) {
        return VfxDraw.smooth(x);
    }

    static float window(float t, float from, float to) {
        return Mth.clamp((t - from) / (to - from), 0, 1);
    }

    @Override
    public Vec3 casterPos(float t) {
        return a.casterStart;
    }

    @Override
    public float casterYaw() {
        return yaw;
    }

    protected float bodyWeight(float t, float pt) {
        return smooth(t / 4f) * (1 - smooth((t - (a.type.duration - 14)) / 12f)) * a.presence(pt);
    }

    protected Vec3 shoulder(HumanoidArm arm) {
        return a.casterStart.add(0, 1.37, 0).add(right.scale(0.31 * (arm == HumanoidArm.RIGHT ? 1 : -1)));
    }

    protected Vec3 eye() {
        return a.casterStart.add(0, 1.62, 0);
    }

    /** World position of the hand of {@code arm} when it points at {@code point}. */
    protected Vec3 handToward(HumanoidArm arm, Vec3 point) {
        Vec3 s = shoulder(arm);
        return s.add(point.subtract(s).normalize().scale(0.75));
    }

    protected Vec3 gauntletHand(Vec3 point) {
        return handToward(gauntletArm, point);
    }

    /** Arm angles that point it straight at {@code point}: {x, y}. */
    protected float[] aim(HumanoidArm arm, Vec3 point) {
        Vec3 v = point.subtract(shoulder(arm));
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        float yawOff = (float) Math.atan2(v.dot(right), v.dot(d));
        float pitch = (float) Math.atan2(-v.y, Math.max(1.0E-3, horiz));
        return new float[]{-Mth.HALF_PI + pitch, yawOff};
    }

    static void blend(ModelPart p, float w, float x, float y, float z) {
        p.xRot = Mth.lerp(w, p.xRot, x);
        p.yRot = Mth.lerp(w, p.yRot, y);
        p.zRot = Mth.lerp(w, p.zRot, z);
    }

    protected ModelPart part(HumanoidModel<?> m, HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
    }

    /** Feet apart, a braced stance; head on {@code look}. Called once per frame (on the right-arm call). */
    protected void stance(HumanoidModel<?> m, float w, float spread, Vec3 look) {
        blend(m.rightLeg, w, -0.15f * spread, 0, 0.12f * spread);
        blend(m.leftLeg, w, 0.15f * spread, 0, -0.12f * spread);
        Vec3 v = look.subtract(eye());
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        blend(m.head, w, (float) Math.atan2(-v.y, Math.max(1.0E-3, horiz)), (float) Math.atan2(v.dot(right), v.dot(d)), 0);
    }

    protected static CinematicCamera.Shot shot(Vec3 pos, Vec3 look, float roll, float fov) {
        return new CinematicCamera.Shot(pos, look, roll, fov);
    }

    /** Draws a tinted ghost of {@code e} at the pose origin (with its current body angles). */
    protected static void ghost(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt, float alpha, int rgb, float inflate,
                                float limbPos, float age) {
        Afterimages.draw(pose, buffers, e, 0, 0, 0, Mth.lerp(pt, e.yBodyRotO, e.yBodyRot), Mth.lerp(pt, e.yHeadRotO, e.yHeadRot), e.getXRot(),
                limbPos, e.walkAnimation.speed(pt), age, alpha, rgb, inflate);
    }

    protected static void ghost(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt, float alpha, int rgb, float inflate) {
        ghost(pose, buffers, e, pt, alpha, rgb, inflate, e.walkAnimation.position(pt), e.tickCount + pt);
    }

    static int grade(int rgb, float alpha) {
        return (int) (Mth.clamp(alpha, 0, 1) * 255) << 24 | rgb;
    }
}
