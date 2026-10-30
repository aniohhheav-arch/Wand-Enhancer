package dev.infinitygauntlet.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.network.PowerPosePayload;
import dev.infinitygauntlet.power.PowerPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client state of Power Stone body poses: drives the third-person arm pose, moves the first-person gauntlet hand
 * through each ability's motion, and sheds sparks off an overloaded fist.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class PowerPoses {
    public static final class State {
        public final PowerPose pose;
        public final int ticks;
        public final HumanoidArm gauntletArm;
        public int age;

        /** Ease in over 3 ticks and back out to the vanilla pose over {@link #BLEND_OUT} ticks after the pose ends. */
        public float weight(float pt) {
            float t = age + pt;
            return PowerArmPose.smooth(t / 3f) * PowerArmPose.smooth((ticks + BLEND_OUT - t) / BLEND_OUT);
        }

        State(PowerPose pose, int ticks, HumanoidArm gauntletArm) {
            this.pose = pose;
            this.ticks = ticks;
            this.gauntletArm = gauntletArm;
        }
    }

    static final int BLEND_OUT = 6;
    private static final Map<Integer, State> POSES = new HashMap<>();
    private static final Map<Integer, Integer> OVERLOAD = new HashMap<>();

    public static void set(PowerPosePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.pose() < 0 || p.pose() >= PowerPose.values().length) {
            return;
        }
        PowerPose pose = PowerPose.values()[p.pose()];
        if (pose == PowerPose.OVERLOAD) {
            OVERLOAD.put(p.entityId(), p.ticks());
            return;
        }
        Entity e = mc.level.getEntity(p.entityId());
        HumanoidArm arm = HumanoidArm.RIGHT;
        if (e instanceof Player player) {
            boolean main = player.getMainHandItem().getItem() instanceof InfinityGauntletItem;
            arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        }
        POSES.put(p.entityId(), new State(pose, p.ticks(), arm));
    }

    @Nullable
    public static State get(int entityId) {
        State s = POSES.get(entityId);
        if (s != null) {
            return s;
        }
        return OVERLOAD.containsKey(entityId) ? new State(PowerPose.OVERLOAD, 1, HumanoidArm.RIGHT) : null;
    }

    public static void clear() {
        POSES.clear();
        OVERLOAD.clear();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        Iterator<State> it = POSES.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (++s.age > s.ticks + BLEND_OUT) {
                it.remove();
            }
        }
        Iterator<Map.Entry<Integer, Integer>> ov = OVERLOAD.entrySet().iterator();
        while (ov.hasNext()) {
            Map.Entry<Integer, Integer> e = ov.next();
            e.setValue(e.getValue() - 1);
            if (e.getValue() <= 0) {
                ov.remove();
                continue;
            }
            if (mc.level.getEntity(e.getKey()) instanceof Player player) {
                overloadSparks(player);
            }
        }
    }

    private static void overloadSparks(Player player) {
        boolean self = player == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        Vec3 look = player.getLookAngle();
        float b = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(b), 0, Mth.cos(b));
        Vec3 right = new Vec3(-forward.z, 0, forward.x).scale(player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
        Vec3 hand = self
                ? player.getEyePosition().add(look.scale(0.9)).add(new Vec3(-look.z, 0, look.x).normalize().scale(0.45)).add(0, -0.4, 0)
                : player.position().add(right.scale(0.4)).add(forward.scale(0.15)).add(0, 0.7, 0);
        int n = Particles.scaled(3);
        for (int i = 0; i < n; i++) {
            Vec3 jitter = new Vec3(player.getRandom().nextGaussian(), player.getRandom().nextGaussian(), player.getRandom().nextGaussian()).scale(0.12);
            Particles.spark(hand.add(jitter), jitter.scale(0.6).add(0, 0.03, 0), i % 2 == 0 ? 0xA032FF : 0xFFE0FF, 0.07f);
        }
        if (player.isSprinting() && Particles.chance(0.6f)) {
            Particles.smoke(player.position().add(0, 0.1, 0), new Vec3(0, 0.02, 0), 0x2A0848, 0.35f, 18);
        }
    }

    /** Third person: both arms (or just the gauntlet arm) take the Power pose. */
    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        var finisher = dev.infinitygauntlet.client.finisher.ClientFinishers.asCaster(event.getEntity());
        if (finisher != null) {
            // the whole body performs the finisher, squared up to the target
            PlayerModel<?> model = event.getRenderer().getModel();
            model.rightArmPose = PowerArmPose.get();
            model.leftArmPose = PowerArmPose.get();
            var p = event.getEntity();
            float yaw = finisher.script.casterYaw();
            p.yBodyRot = p.yBodyRotO = yaw;
            p.yHeadRot = p.yHeadRotO = yaw;
            return;
        }
        State s = get(event.getEntity().getId());
        if (s == null) {
            return;
        }
        PlayerModel<?> model = event.getRenderer().getModel();
        HumanoidModel.ArmPose pose = PowerArmPose.get();
        boolean both = s.pose == PowerPose.CHARGE || s.pose == PowerPose.SLAM || s.pose == PowerPose.SPREAD || s.pose == PowerPose.HOLD
                || s.pose == PowerPose.PUNCH || s.pose == PowerPose.THRUST || s.pose == PowerPose.RAISE;
        if (both || s.gauntletArm == HumanoidArm.RIGHT) {
            model.rightArmPose = pose;
        }
        if (both || s.gauntletArm == HumanoidArm.LEFT) {
            model.leftArmPose = pose;
        }
    }

    /** First person: the gauntlet hand follows each ability's motion. */
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(event.getItemStack().getItem() instanceof InfinityGauntletItem)) {
            return;
        }
        State s = get(mc.player.getId());
        if (s == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float age = s.age + event.getPartialTick();
        float t = Mth.clamp(age / Math.max(1, s.ticks), 0, 1);
        boolean right = (event.getHand() == InteractionHand.MAIN_HAND) == (mc.player.getMainArm() == HumanoidArm.RIGHT);
        float side = right ? 1 : -1;
        float jitter = Mth.sin(age * 3.1f) * 0.012f;
        switch (s.pose) {
            case CHARGE -> {
                float in = PowerArmPose.smooth(age / 6f);
                pose.translate(-side * 0.14f * in + jitter, 0.06f * in + jitter, 0.05f * in);
                pose.mulPose(Axis.ZP.rotationDegrees(side * 8 * in));
            }
            case THRUST -> {
                float kick = (float) Math.exp(-age * 0.3f);
                pose.translate(-side * 0.08f, 0.08f * kick, -0.28f * (1 - kick * 0.6f) + jitter);
                pose.mulPose(Axis.XP.rotationDegrees(-25 * kick));
            }
            case PUNCH -> {
                float windup = PowerArmPose.smooth(age / 4f);
                float drive = PowerArmPose.smooth((age - 4) / 1.6f);
                float back = PowerArmPose.smooth((age - 10) / 8f);
                float z = Mth.lerp(drive, 0.22f * windup, -0.55f) * (1 - back);
                pose.translate(-side * 0.18f * drive * (1 - back), -0.05f * windup * (1 - drive), z);
                pose.mulPose(Axis.YP.rotationDegrees(side * 20 * windup * (1 - drive)));
            }
            case SLAM -> {
                float up = PowerArmPose.smooth(t / 0.4f);
                float down = PowerArmPose.smooth((t - 0.4f) / 0.12f);
                pose.translate(0, Mth.lerp(down, 0.4f * up, -0.3f), -0.1f * down);
                pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(down, 50 * up, -35)));
            }
            case SPREAD -> {
                float open = PowerArmPose.smooth(t / 0.25f);
                pose.translate(side * 0.3f * open + jitter, 0.05f * open, 0.1f * open);
                pose.mulPose(Axis.ZP.rotationDegrees(-side * 30 * open));
            }
            case RAISE -> {
                float up = PowerArmPose.smooth(t / 0.2f) * (1 - PowerArmPose.smooth((t - 0.85f) / 0.15f));
                pose.translate(0, 0.45f * up, -0.1f * up);
                pose.mulPose(Axis.XP.rotationDegrees(55 * up));
            }
            case HOLD -> {
                float grow = PowerArmPose.smooth(t / 0.15f);
                pose.translate(-side * 0.2f * grow + jitter * (1 + t * 2), 0.1f * grow, -0.15f * grow);
                pose.mulPose(Axis.ZP.rotationDegrees(side * 12 * grow));
            }
            case OVERLOAD -> pose.translate(jitter * 0.5f, jitter * 0.5f, 0);
        }
    }

    private PowerPoses() {
    }
}
