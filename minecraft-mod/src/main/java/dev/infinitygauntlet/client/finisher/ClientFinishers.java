package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.finisher.FinisherType;
import dev.infinitygauntlet.network.FinisherPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client runtime of cinematic finishers. Every client near one plays the same timeline from the start payload: the
 * caster's real 3D model performs the body animation, the target reacts, effects play. The caster's own client also
 * moves them along the scripted path, locks their input and hands the camera to the finisher's director.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class ClientFinishers {
    /** One running finisher as seen by this client. */
    public static final class Active {
        public final FinisherType type;
        public final int casterId;
        public final int targetId;
        public final Vec3 casterStart;
        public final Vec3 targetPos;
        public final ClientScript script;
        public int age;
        public boolean stopped;
        /** Fading out after an interruption. */
        public int stopAge;

        Active(FinisherPayload p, FinisherType type) {
            this.type = type;
            this.casterId = p.casterId();
            this.targetId = p.targetId();
            this.casterStart = p.casterStart();
            this.targetPos = p.targetPos();
            this.script = switch (type) {
                case COSMIC_PUNCH -> new CosmicPunchClient(this);
            };
        }

        public float time(float pt) {
            return age + pt;
        }

        @Nullable
        public Entity caster() {
            return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(casterId);
        }

        @Nullable
        public LivingEntity target() {
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(targetId);
            return e instanceof LivingEntity living ? living : null;
        }

        /** 0..1 overall presence: eases in at the start and out at the end (or after an interruption). */
        public float presence(float pt) {
            float t = time(pt);
            float in = Math.min(1f, t / 3f);
            float out = stopped ? Math.max(0f, 1 - (stopAge + pt) / 8f) : Math.min(1f, Math.max(0f, (type.duration + 8 - t) / 16f));
            return Math.min(in, out);
        }
    }

    /** Per-finisher client behaviour. Default methods do nothing. */
    public interface ClientScript {
        default void tick() {
        }

        /** World-space effects, drawn with the other VFX. */
        default void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        }

        /** Poses the caster's model for {@code arm}; body, head and legs are posed on the right-arm call. */
        default void poseCaster(HumanoidModel<?> model, LivingEntity caster, HumanoidArm arm, HumanoidArm gauntletArm, float pt) {
        }

        /** Whole-body transform of the caster around its feet (lean, recoil). */
        default void transformCaster(PoseStack pose, LivingEntity caster, float pt) {
        }

        default void transformTarget(PoseStack pose, LivingEntity target, float pt) {
        }

        /** Drawn right after the target's model, inside the same transform (glow shells, cracks). */
        default void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity target, float pt) {
        }

        /** Where the local caster stands this tick. */
        Vec3 casterPos(float t);

        /** Facing of the caster's body, degrees. */
        float casterYaw();

        @Nullable
        default CinematicCamera.Shot shot(float pt) {
            return null;
        }

        /** ARGB full-screen grade over the world (0 = none). */
        default int grade(float pt) {
            return 0;
        }

        /** ARGB impact frame drawn over everything (0 = none). */
        default int impactFrame(float pt) {
            return 0;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Active> BY_CASTER = new HashMap<>();
    private static final Map<Integer, Active> BY_TARGET = new HashMap<>();

    public static void handle(FinisherPayload p) {
        if (p.kind() < 0 || p.kind() >= FinisherType.values().length) {
            return;
        }
        if (p.ended()) {
            Active a = BY_CASTER.get(p.casterId());
            if (a != null) {
                a.stopped = true;
            }
            return;
        }
        Active old = BY_CASTER.remove(p.casterId());
        if (old != null) {
            ACTIVE.remove(old);
            BY_TARGET.remove(old.targetId);
        }
        Active a = new Active(p, FinisherType.values()[p.kind()]);
        ACTIVE.add(a);
        BY_CASTER.put(a.casterId, a);
        BY_TARGET.put(a.targetId, a);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == a.casterId) {
            CinematicCamera.direct(new CinematicCamera.Director() {
                @Override
                public CinematicCamera.Shot shot(float pt) {
                    return ACTIVE.contains(a) ? a.script.shot(pt) : null;
                }

                @Override
                public float weight(float pt) {
                    return a.presence(pt);
                }
            });
        }
    }

    @Nullable
    public static Active asCaster(Entity e) {
        return BY_CASTER.get(e.getId());
    }

    @Nullable
    public static Active asTarget(Entity e) {
        return BY_TARGET.get(e.getId());
    }

    public static boolean localCasting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && BY_CASTER.containsKey(mc.player.getId());
    }

    public static void clear() {
        ACTIVE.clear();
        BY_CASTER.clear();
        BY_TARGET.clear();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            a.age++;
            if (a.stopped) {
                a.stopAge++;
            }
            if (a.age > a.type.duration + 8 || a.stopped && a.stopAge > 8) {
                it.remove();
                BY_CASTER.remove(a.casterId);
                BY_TARGET.remove(a.targetId);
                continue;
            }
            if (!a.stopped) {
                a.script.tick();
                if (mc.player != null && mc.player.getId() == a.casterId && a.age <= a.type.duration) {
                    drive(mc.player, a);
                }
            }
        }
    }

    /** The local caster follows the scripted path, facing the target. */
    private static void drive(Player player, Active a) {
        Vec3 p = a.script.casterPos(a.age);
        player.setPos(p.x, p.y, p.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        float yaw = a.script.casterYaw();
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
    }

    @SubscribeEvent
    static void onInput(MovementInputUpdateEvent event) {
        if (localCasting()) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    public static void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        for (Active a : ACTIVE) {
            if (!a.stopped || a.stopAge < 8) {
                a.script.renderWorld(pose, buffers, camera, pt);
            }
        }
        buffers.endBatch();
    }

    public static int grade(float pt) {
        for (Active a : ACTIVE) {
            if (Minecraft.getInstance().player != null && a.casterId == Minecraft.getInstance().player.getId()) {
                return scaleAlpha(a.script.grade(pt), a.presence(pt));
            }
        }
        return 0;
    }

    public static int impactFrame(float pt) {
        for (Active a : ACTIVE) {
            if (!a.stopped && Minecraft.getInstance().player != null && a.casterId == Minecraft.getInstance().player.getId()) {
                return a.script.impactFrame(pt);
            }
        }
        return 0;
    }

    private static int scaleAlpha(int argb, float k) {
        int alpha = (int) ((argb >>> 24) * Math.max(0, Math.min(1, k)));
        return alpha << 24 | argb & 0xFFFFFF;
    }

    private ClientFinishers() {
    }
}
