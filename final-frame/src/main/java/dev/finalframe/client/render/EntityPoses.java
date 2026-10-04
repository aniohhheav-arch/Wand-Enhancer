package dev.finalframe.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.finalframe.client.anim.Rig;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.TargetMotion;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Places performer and target exactly on their choreographed marks every frame (independent of
 * network interpolation), applies whole-body leans, and exposes the entity being rendered to the
 * item renderer.
 */
public final class EntityPoses {
    private record Frame(Entity entity, boolean pushed) {
    }

    private static final Deque<Frame> STACK = new ArrayDeque<>();
    private static float partialTick;

    private EntityPoses() {
    }

    @Nullable
    public static Entity current() {
        Frame f = STACK.peek();
        return f == null ? null : f.entity;
    }

    public static float partialTick() {
        return partialTick;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        partialTick = event.getPartialTick();
        boolean pushed = false;
        ClientFinisherManager mgr = ClientFinisherManager.INSTANCE;
        if (!mgr.isEmpty()) {
            ClientSession performer = mgr.asPerformer(entity);
            ClientSession target = performer == null ? mgr.asTarget(entity) : null;
            if (performer != null && !performer.isAborting()) {
                pushed = placePerformer(event.getPoseStack(), entity, performer);
            } else if (target != null && !target.isAborting() && entity.deathTime == 0 && entity.isAlive()) {
                pushed = placeTarget(event.getPoseStack(), entity, target);
            }
        }
        STACK.push(new Frame(entity, pushed));
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        Frame f = STACK.poll();
        if (f != null && f.pushed) {
            event.getPoseStack().popPose();
        }
    }

    private static boolean placePerformer(PoseStack ps, LivingEntity entity, ClientSession s) {
        float t = s.time(partialTick);
        AnchorFrame frame = s.snapshot().frame();
        float yaw = frame.yaw();
        float headYaw = yaw + s.choreography().performerHeadYaw(s, t);
        entity.yBodyRot = entity.yBodyRotO = yaw;
        entity.yHeadRot = entity.yHeadRotO = headYaw;

        Vec3 mark = frame.toWorld(s.choreography().performerOffset(s, t));
        Vec3 drawn = entity.getPosition(partialTick);
        float[] lean = s.choreography().performerLean(s, t);
        ps.pushPose();
        ps.translate(mark.x - drawn.x, mark.y - drawn.y, mark.z - drawn.z);
        lean(ps, yaw, lean[0], lean[1]);
        return true;
    }

    private static boolean placeTarget(PoseStack ps, LivingEntity entity, ClientSession s) {
        float t = s.time(partialTick);
        TargetMotion m = s.targetMotion(t);
        entity.yBodyRot = entity.yBodyRotO = m.bodyYaw();
        entity.yHeadRot = entity.yHeadRotO = m.headYaw();
        Vec3 drawn = entity.getPosition(partialTick);
        ps.pushPose();
        ps.translate(m.position().x - drawn.x, m.position().y - drawn.y, m.position().z - drawn.z);
        lean(ps, m.bodyYaw(), m.leanPitch(), m.leanRoll());
        return true;
    }

    /** Tilts the whole body around the feet: pitch toward the facing direction, roll around it. */
    private static void lean(PoseStack ps, float yaw, float pitch, float roll) {
        if (pitch == 0 && roll == 0) {
            return;
        }
        float r = yaw * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(r);
        float fz = Mth.cos(r);
        Quaternionf q = new Quaternionf()
            .rotateAxis(pitch * Mth.DEG_TO_RAD, fz, 0, -fx)
            .rotateAxis(roll * Mth.DEG_TO_RAD, fx, 0, fz);
        ps.mulPose(q);
    }

    /** Called from the humanoid model mixin after vanilla animation. */
    public static void applyModelPose(HumanoidModel<?> model, LivingEntity entity) {
        ClientFinisherManager mgr = ClientFinisherManager.INSTANCE;
        if (mgr.isEmpty()) {
            return;
        }
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        ClientSession performer = mgr.asPerformer(entity);
        if (performer != null && !performer.isAborting()) {
            performer.choreography().posePerformer(Rig.of(model), performer, performer.time(pt));
            model.hat.copyFrom(model.head);
            return;
        }
        ClientSession target = mgr.asTarget(entity);
        if (target != null && !target.isAborting() && entity.deathTime == 0) {
            target.choreography().poseTarget(Rig.of(model), target, target.time(pt));
            model.hat.copyFrom(model.head);
        }
    }
}
