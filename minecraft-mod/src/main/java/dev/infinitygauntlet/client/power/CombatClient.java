package dev.infinitygauntlet.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.config.ClientConfig;
import dev.infinitygauntlet.network.CombatPayload;
import dev.infinitygauntlet.power.CombatFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client side of {@link CombatFx}: what a blow looks like on each kind of creature, short body reactions (stagger,
 * suspension, tumbling, crushing) and custom deaths, applied as transforms around the normal entity render. Severed
 * heads are hidden on the body here; the head itself is a {@code SeveredHeadEntity}.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class CombatClient {
    /** What a creature is made of, for its impact effects. */
    public enum Material {
        FLESH(0xA8101C, 0x5E0610, 0xFFB0B0, true),
        UNDEAD(0x5A0E14, 0x4C5E22, 0x9AA060, true),
        BONE(0xEAE4D4, 0xB8B0A0, 0xFFFFFF, false),
        WITHERED(0x2A2A2E, 0x101012, 0x6A6A70, false),
        SLIME(0x6ACB4A, 0x3E8A2A, 0xC8FFB0, true),
        MAGMA(0xFF7A1A, 0x8A1E08, 0xFFE0A0, true),
        EMBER(0xFFB040, 0x5A2A10, 0xFFF0C0, false),
        METAL(0xFFD080, 0x7A7A80, 0xFFFFFF, false),
        ENDER(0xCC33FF, 0x1A0A24, 0xF0C0FF, false),
        ICHOR(0x9ACD32, 0x4A5A10, 0xE0FF90, true),
        GUNPOWDER(0x4FA34A, 0x3A3A3A, 0xB8F0B0, false),
        SNOW(0xF4FAFF, 0xB0C8D8, 0xFFFFFF, false),
        ENERGY(0xA032FF, 0x2A0848, 0xFFE0FF, false);

        public final int primary, secondary, flash;
        /** Whether hits shed liquid droplets that splat on the ground. */
        public final boolean liquid;

        Material(int primary, int secondary, int flash, boolean liquid) {
            this.primary = primary;
            this.secondary = secondary;
            this.flash = flash;
            this.liquid = liquid;
        }
    }

    private record Reaction(CombatFx.Reaction type, int ticks, Vec3 dir, long start) {
    }

    private record Death(CombatFx.DeathStyle style, Vec3 dir, boolean headless, long start) {
    }

    private static final Map<Integer, Reaction> REACTIONS = new HashMap<>();
    private static final Map<Integer, Death> DEATHS = new HashMap<>();
    private static final Map<Integer, Boolean> HIDDEN = new HashMap<>();
    private static long clock;

    public static Material material(Entity e) {
        if (!ClientConfig.visceral()) {
            return Material.ENERGY;
        }
        if (e instanceof net.minecraft.world.entity.monster.WitherSkeleton || e instanceof WitherBoss) {
            return Material.WITHERED;
        }
        if (e instanceof AbstractSkeleton) {
            return Material.BONE;
        }
        if (e instanceof Zombie || e instanceof Zoglin) {
            return Material.UNDEAD;
        }
        if (e instanceof MagmaCube) {
            return Material.MAGMA;
        }
        if (e instanceof Slime) {
            return Material.SLIME;
        }
        if (e instanceof Blaze || e instanceof Breeze || e instanceof Vex) {
            return Material.EMBER;
        }
        if (e instanceof IronGolem || e instanceof Shulker) {
            return Material.METAL;
        }
        if (e instanceof SnowGolem) {
            return Material.SNOW;
        }
        if (e instanceof EnderMan || e instanceof Endermite || e instanceof EnderDragon) {
            return Material.ENDER;
        }
        if (e instanceof Spider || e instanceof CaveSpider || e instanceof Silverfish || e instanceof Bee) {
            return Material.ICHOR;
        }
        if (e instanceof Creeper) {
            return Material.GUNPOWDER;
        }
        return Material.FLESH;
    }

    public static void handle(CombatPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(p.entityId());
        switch (p.kind()) {
            case CombatFx.KIND_HIT -> {
                if (e != null) {
                    ImpactBursts.hit(e, material(e), p.dir(), p.intensity());
                }
            }
            case CombatFx.KIND_REACT -> {
                int style = p.style();
                if (style >= 0 && style < CombatFx.Reaction.values().length) {
                    REACTIONS.put(p.entityId(), new Reaction(CombatFx.Reaction.values()[style], p.ticks(), p.dir(), clock));
                }
            }
            case CombatFx.KIND_DEATH -> {
                int style = p.style() & 255;
                if (style < CombatFx.DeathStyle.values().length) {
                    boolean headless = (p.style() & CombatFx.HEADLESS) != 0;
                    DEATHS.put(p.entityId(), new Death(CombatFx.DeathStyle.values()[style], p.dir(), headless, clock));
                    if (e != null) {
                        ImpactBursts.death(e, material(e), CombatFx.DeathStyle.values()[style], p.dir(), headless);
                    }
                }
            }
            default -> {
            }
        }
    }

    public static void clear() {
        REACTIONS.clear();
        DEATHS.clear();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        clock++;
        REACTIONS.values().removeIf(r -> clock - r.start > r.ticks);
        Iterator<Map.Entry<Integer, Death>> it = DEATHS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Death> entry = it.next();
            Entity e = mc.level.getEntity(entry.getKey());
            if (e == null || clock - entry.getValue().start > 200) {
                it.remove();
                continue;
            }
            ImpactBursts.dying(e, material(e), entry.getValue().style, clock - entry.getValue().start);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        Reaction r = REACTIONS.get(e.getId());
        Death d = e.isDeadOrDying() ? DEATHS.get(e.getId()) : null;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float pt = event.getPartialTick();
        float h = e.getBbHeight();
        if (d != null) {
            applyDeath(pose, e, d, clock - d.start + pt, h);
            if (d.headless) {
                setHead(event.getRenderer().getModel(), false);
                HIDDEN.put(e.getId(), true);
            }
        } else if (r != null) {
            applyReaction(pose, e, r, clock - r.start + pt, h);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        event.getPoseStack().popPose();
        if (HIDDEN.remove(event.getEntity().getId()) != null) {
            setHead(event.getRenderer().getModel(), true);
        }
    }

    private static void setHead(EntityModel<?> model, boolean visible) {
        if (model instanceof HumanoidModel<?> humanoid) {
            humanoid.head.visible = visible;
            humanoid.hat.visible = visible;
        } else if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart head = find(hierarchical.root(), "head");
            if (head != null) {
                head.visible = visible;
            }
        }
    }

    @Nullable
    private static ModelPart find(ModelPart root, String name) {
        if (root.hasChild(name)) {
            return root.getChild(name);
        }
        return root.getAllParts().filter(p -> p != root && p.hasChild(name)).findFirst().map(p -> p.getChild(name)).orElse(null);
    }

    /** Axis perpendicular to the horizontal blow direction: the blow rotates the body around it. */
    private static Vector3f tipAxis(Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return new Vector3f(1, 0, 0);
        }
        flat = flat.normalize();
        return new Vector3f((float) flat.z, 0, (float) -flat.x);
    }

    private static void applyReaction(PoseStack pose, LivingEntity e, Reaction r, float age, float h) {
        float t = Mth.clamp(age / Math.max(1, r.ticks), 0, 1);
        float decay = (1 - t) * (1 - t);
        Vector3f axis = tipAxis(r.dir);
        switch (r.type) {
            case STAGGER -> {
                float angle = 22 * decay * (0.6f + 0.4f * Mth.cos(age * 1.3f));
                pose.mulPose(Axis.of(axis).rotationDegrees(-angle));
                pose.translate(Mth.sin(age * 4.1f) * 0.04f * decay, 0, Mth.cos(age * 3.7f) * 0.04f * decay);
            }
            case RECOIL -> {
                float bend = 28 * (float) Math.exp(-age * 0.25f) * Mth.clamp(age / 1.5f, 0, 1);
                pose.translate(0, h * 0.5f, 0);
                pose.mulPose(Axis.of(axis).rotationDegrees(-bend));
                pose.translate(0, -h * 0.5f, 0);
            }
            case SUSPEND -> {
                float in = Mth.clamp(age / 6f, 0, 1) * Mth.clamp((r.ticks - age) / 4f, 0, 1);
                pose.translate(0, h * 0.5f, 0);
                pose.mulPose(Axis.YP.rotationDegrees(age * 3 * in));
                pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.15f + e.getId()) * 18 * in));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(age * 0.11f + e.getId()) * 14 * in));
                pose.translate(0, -h * 0.5f, 0);
            }
            case TUMBLE -> {
                float spin = (float) (age * 26 * (e.onGround() ? 0.3 : 1));
                pose.translate(0, h * 0.5f, 0);
                pose.mulPose(Axis.of(axis).rotationDegrees(-spin));
                pose.mulPose(Axis.YP.rotationDegrees(age * 7));
                pose.translate(0, -h * 0.5f, 0);
            }
            case CRUSH -> {
                float slam = Mth.clamp(age / 2.5f, 0, 1);
                float rebound = Mth.clamp((age - r.ticks * 0.6f) / (r.ticks * 0.4f), 0, 1);
                float squash = slam * (1 - rebound) * (0.65f + 0.08f * Mth.sin(age * 2));
                pose.scale(1 + squash * 0.5f, 1 - squash, 1 + squash * 0.5f);
            }
        }
    }

    private static void applyDeath(PoseStack pose, LivingEntity e, Death d, float age, float h) {
        Vector3f axis = tipAxis(d.dir);
        switch (d.style) {
            case RAGDOLL -> {
                float spin = e.onGround() && age > 4 ? 0 : age * 22;
                pose.translate(0, h * 0.5f, 0);
                pose.mulPose(Axis.of(axis).rotationDegrees(-Math.min(spin, 540) - Math.min(age, 10) * 6));
                pose.mulPose(Axis.YP.rotationDegrees(Math.min(age, 20) * 9));
                pose.translate(0, -h * 0.5f, 0);
            }
            case CRUSH -> {
                float flat = Mth.clamp(age / 2f, 0, 1);
                pose.scale(1 + 0.55f * flat, 1 - 0.86f * flat, 1 + 0.55f * flat);
            }
            case DISINTEGRATE -> {
                float gone = Mth.clamp(age / 18f, 0, 1);
                float flicker = age % 2 < 1 ? 1f : 0.96f;
                float s = (1 - gone * gone) * flicker;
                pose.translate(0, h * 0.5f * (1 - s), 0);
                pose.scale(s, s, s);
            }
            case EXECUTION -> {
                // a beat of rigid stillness, then thrown back, folding at the waist
                float snap = Mth.clamp((age - 2) / 4f, 0, 1);
                pose.translate(0, h * 0.4f, 0);
                pose.mulPose(Axis.of(axis).rotationDegrees(-(80 * snap + Math.max(0, age - 6) * 14)));
                pose.translate(0, -h * 0.4f, 0);
            }
        }
    }

    private CombatClient() {
    }
}
