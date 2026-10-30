package dev.infinitygauntlet.power;

import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.power.SeveredHeadEntity;
import dev.infinitygauntlet.network.CombatPayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.time.TimeManager;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server side of the Power Stone combat presentation: damage with knockback, hit-stop, slow motion, per-creature hit
 * reactions and death styles. Gameplay (damage, motion) happens here; the matching visuals are requested from clients
 * with {@link CombatPayload}, where the material of the creature decides what its impacts look like.
 */
public final class CombatFx {
    public static final int KIND_HIT = 0;
    public static final int KIND_REACT = 1;
    public static final int KIND_DEATH = 2;
    public static final int HEADLESS = 256;

    /** Short-lived body reactions drawn on top of the entity's normal animation. */
    public enum Reaction {
        /** Rocks back from the blow and shudders. */
        STAGGER,
        /** Hangs helpless in the air, slowly turning. */
        SUSPEND,
        /** Tumbles head over heels through the air. */
        TUMBLE,
        /** Squashed flat by a weight from above. */
        CRUSH,
        /** Doubles over a blow to the body. */
        RECOIL
    }

    /** How a creature killed by the Power Stone goes down. */
    public enum DeathStyle {
        /** Limp, spinning tumble wherever the blast threw it. */
        RAGDOLL,
        /** Flattened into the ground. */
        CRUSH,
        /** Burnt away layer by layer into purple ash. */
        DISINTEGRATE,
        /** A finishing blow: frozen for a beat, then thrown back and broken. */
        EXECUTION,
        /** Gone in an instant: the body erupts into energy and leaves nothing behind. */
        VAPORIZE
    }

    public static float damage(float base) {
        return base * ServerConfig.POWER_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /** Living creatures within {@code radius} of {@code center} the caster may hit, nearest first. */
    public static List<LivingEntity> targets(ServerPlayer caster, Vec3 center, double radius) {
        ServerLevel level = caster.serverLevel();
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && TargetRules.canTarget(caster, e) && e.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return list;
    }

    /**
     * Damages {@code target}, throws it along {@code push}, plays the material hit burst and, if the blow kills, the
     * given death style. Bypasses the usual invulnerability window so staged multi-hit attacks always connect.
     *
     * @return whether the blow killed
     */
    public static boolean strike(ServerPlayer caster, LivingEntity target, float baseDamage, Vec3 push, float intensity, DeathStyle onKill,
                                 boolean finisher) {
        ServerLevel level = caster.serverLevel();
        boolean wasAlive = target.isAlive();
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().indirectMagic(caster, caster), damage(baseDamage));
        if (push.lengthSqr() > 0) {
            target.setDeltaMovement(push);
            target.hurtMarked = true;
            target.fallDistance = 0;
        }
        Vec3 dir = push.lengthSqr() < 1.0E-6 ? target.position().subtract(caster.position()).normalize() : push.normalize();
        hit(level, target, dir, intensity);
        boolean killed = wasAlive && target.isDeadOrDying();
        if (killed) {
            boolean headless = finisher && decapitate(level, target, dir);
            death(level, target, onKill, dir, headless);
        }
        return killed;
    }

    /** Material burst (blood, bone, sparks, slime... chosen on the client from the creature type). */
    public static void hit(ServerLevel level, LivingEntity target, Vec3 dir, float intensity) {
        send(level, target, new CombatPayload(target.getId(), KIND_HIT, 0, dir, intensity, 0));
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                target instanceof AbstractSkeleton ? ModSounds.IMPACT_BONE.get() : ModSounds.IMPACT_FLESH.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7f + intensity * 0.3f, 0.8f + level.random.nextFloat() * 0.4f);
    }

    public static void react(ServerLevel level, LivingEntity target, Reaction reaction, Vec3 dir, int ticks) {
        send(level, target, new CombatPayload(target.getId(), KIND_REACT, reaction.ordinal(), dir, 1f, ticks));
    }

    public static void death(ServerLevel level, LivingEntity target, DeathStyle style, Vec3 dir, boolean headless) {
        send(level, target, new CombatPayload(target.getId(), KIND_DEATH, style.ordinal() | (headless ? HEADLESS : 0), dir, 1f, 20));
    }

    /**
     * Knocks the head off a killed zombie, skeleton, creeper, piglin or player as a tumbling skull item. Non-graphic:
     * the head pops off whole with a puff, the client hides the head on the body.
     */
    public static boolean decapitate(ServerLevel level, LivingEntity target, Vec3 dir) {
        if (!ServerConfig.POWER_DISMEMBERMENT.get()) {
            return false;
        }
        ItemStack head = headItem(target);
        if (head.isEmpty()) {
            return false;
        }
        SeveredHeadEntity entity = new SeveredHeadEntity(ModEntities.SEVERED_HEAD.get(), level);
        Vec3 at = target.getEyePosition();
        entity.setup(head, target.getBbWidth());
        entity.moveTo(at.x, at.y, at.z, target.getYHeadRot(), 0);
        entity.setDeltaMovement(dir.scale(0.55).add(level.random.nextGaussian() * 0.12, 0.55 + level.random.nextDouble() * 0.25,
                level.random.nextGaussian() * 0.12));
        level.addFreshEntity(entity);
        Vfx.sound(level, at, ModSounds.HEAD_POP, 1.2f, 0.9f + level.random.nextFloat() * 0.2f);
        return true;
    }

    static ItemStack headItem(LivingEntity target) {
        if (target instanceof WitherSkeleton) {
            return new ItemStack(Items.WITHER_SKELETON_SKULL);
        }
        if (target instanceof AbstractSkeleton) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        if (target instanceof ZombifiedPiglin || target instanceof AbstractPiglin) {
            return new ItemStack(Items.PIGLIN_HEAD);
        }
        if (target instanceof Zombie) {
            return new ItemStack(Items.ZOMBIE_HEAD);
        }
        if (target instanceof Creeper) {
            return new ItemStack(Items.CREEPER_HEAD);
        }
        if (target instanceof Player player) {
            ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
            skull.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            return skull;
        }
        return ItemStack.EMPTY;
    }

    /** Hit-stop: everything but the caster within {@code radius} freezes for a few ticks, selling the weight of the blow. */
    public static void hitstop(ServerLevel level, Vec3 center, double radius, int ticks, Entity except) {
        if (!ServerConfig.POWER_SLOW_MOTION.get()) {
            return;
        }
        for (Entity e : level.getEntities(except, new AABB(center, center).inflate(radius), e -> e instanceof LivingEntity && !(e instanceof Player))) {
            TimeManager.apply(e, 0f, ticks, true);
        }
    }

    /** Slow motion for every non-player creature around {@code center}, except {@code spared}. */
    public static void slowmo(ServerLevel level, Vec3 center, double radius, float scale, int ticks, Entity... spared) {
        if (!ServerConfig.POWER_SLOW_MOTION.get()) {
            return;
        }
        java.util.List<Entity> skip = java.util.Arrays.asList(spared);
        for (Entity e : level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                e -> e instanceof LivingEntity && !(e instanceof Player) && !skip.contains(e))) {
            TimeManager.apply(e, scale, ticks, true);
        }
    }

    private static void send(ServerLevel level, Entity target, CombatPayload payload) {
        ModNetwork.sendNear(level, target.position(), 128, payload);
    }

    private CombatFx() {
    }
}
