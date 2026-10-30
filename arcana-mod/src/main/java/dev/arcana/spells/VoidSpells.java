package dev.arcana.spells;

import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.SpellContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Void: gravity wells, singularities and dimensional rifts. */
public final class VoidSpells {

    /** Slow dark orb that becomes a gravity well where it lands. Charge extends range and well lifetime. */
    public static boolean singularity(SpellContext c) {
        Vec3 dir = Magic.look(c.player);
        Vec3[] pos = {Magic.eye(c.player).add(dir.scale(1.2))};
        int travel = 12 + (int) (c.charge * 14);
        var dust = Magic.dust(c, 1.6f);
        Magic.snd(c.level, pos[0], SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
        EffectManager.run(c.level, travel, age -> {
            Vec3 next = pos[0].add(dir.scale(0.85));
            BlockHitResult hit = c.level.clip(new ClipContext(pos[0], next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
            boolean stop = hit.getType() != HitResult.Type.MISS;
            pos[0] = stop ? hit.getLocation() : next;
            Magic.fx(c.level, dust, pos[0], 5, 0.12, 0.12, 0.12, 0);
            Magic.fx(c.level, ParticleTypes.PORTAL, pos[0], 6, 0.3, 0.3, 0.3, 0.6);
            return !stop && Magic.enemies(c, pos[0], 1.1).isEmpty();
        }, () -> well(c, pos[0], 5.5 * c.radius, 50 + (int) (c.charge * 50) + c.stage * 20, 0.3, 2f, 4f, false));
        return true;
    }

    /**
     * A gravity well: pulls entities and items toward its centre, damages them periodically and implodes at the end.
     * Shared by Singularity, Event Horizon and the Forbidden Wand's Rift Collapse.
     */
    public static void well(SpellContext c, Vec3 center, double radius, int duration, double pull, float tickDamage, float finalDamage, boolean accretion) {
        var dust = Magic.dust(c, accretion ? 2.2f : 1.4f);
        Magic.snd(c.level, center, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 1.4f, 0.5f);
        EffectManager.run(c.level, duration, age -> {
            if (!c.valid() && age > 5) return false;
            double spin = age * 0.5;
            for (int i = 0; i < 3; i++) {
                double a = spin + i * 2.094;
                double r = radius * (0.25 + 0.75 * ((age * 0.07 + i * 0.33) % 1.0));
                Vec3 p = center.add(Math.cos(a * 3) * r, Math.sin(age * 0.2 + i) * 0.4, Math.sin(a * 3) * r);
                c.level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            Magic.fx(c.level, ParticleTypes.PORTAL, center, 8, radius * 0.25, radius * 0.25, radius * 0.25, 0.7);
            Magic.fx(c.level, ParticleTypes.SQUID_INK, center, 2, 0.4, 0.4, 0.4, 0.02);
            if (accretion) Magic.ring(c.level, ParticleTypes.PORTAL, center, radius * 0.45, 28, 0);
            for (LivingEntity e : Magic.enemies(c, center, radius)) {
                Magic.pull(e, center, pull);
                if (age % 10 == 0) Magic.hurt(c, e, tickDamage);
            }
            for (ItemEntity it : c.level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(radius))) Magic.pull(it, center, pull * 0.6);
            if (age % 20 == 0) Magic.snd(c.level, center, SoundEvents.PORTAL_AMBIENT, 0.6f, 0.6f);
            return true;
        }, () -> {
            Magic.fx(c.level, ParticleTypes.REVERSE_PORTAL, center, 80, radius * 0.5, radius * 0.5, radius * 0.5, 0.5);
            Magic.fx(c.level, ParticleTypes.EXPLOSION, center, 2, 0.5, 0.5, 0.5, 0);
            Magic.snd(c.level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.5f);
            if (c.valid()) {
                for (LivingEntity e : Magic.enemies(c, center, radius)) {
                    Magic.hurt(c, e, finalDamage);
                    Vec3 out = e.position().subtract(center).normalize().scale(1.1);
                    Magic.push(e, new Vec3(out.x, 0.5, out.z));
                }
            }
        });
    }

    /** Blink to the aimed point; awakened/ascended wands damage everything along the rift trail. */
    public static boolean riftStep(SpellContext c) {
        double range = 10 + c.stage * 4;
        Vec3 eye = Magic.eye(c.player);
        Vec3 look = Magic.look(c.player);
        BlockHitResult hit = Magic.rayBlocks(c, range);
        Vec3 target = hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation().subtract(look.scale(0.7));
        Vec3 feet = target.subtract(0, c.player.getEyeHeight(), 0);
        Vec3 dest = null;
        for (int dy = 0; dy <= 3 && dest == null; dy++) {
            Vec3 t = feet.add(0, dy, 0);
            if (Magic.canStand(c.level, c.player, t)) dest = t;
        }
        if (dest == null) return false;
        Vec3 from = c.player.position();
        Magic.line(c.level, ParticleTypes.PORTAL, from.add(0, 1, 0), dest.add(0, 1, 0), 0.35);
        Magic.line(c.level, Magic.dust(c, 1.3f), from.add(0, 1, 0), dest.add(0, 1, 0), 0.5);
        Magic.fx(c.level, ParticleTypes.REVERSE_PORTAL, from.add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
        c.player.teleportTo(dest.x, dest.y, dest.z);
        c.player.resetFallDistance();
        Magic.fx(c.level, ParticleTypes.PORTAL, dest.add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0.8);
        Magic.snd(c.level, from, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.1f);
        Magic.snd(c.level, dest, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.9f);
        if (c.stage >= 1) {
            AABB path = new AABB(from, dest).inflate(2);
            List<LivingEntity> hit2 = new ArrayList<>();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, path, e -> Magic.isEnemy(c.player, e))) {
                if (distToSegment(e.position().add(0, 1, 0), from, dest) < 2.2) hit2.add(e);
            }
            for (LivingEntity e : hit2) Magic.hurt(c, e, 3f + c.stage * 2f);
        }
        return true;
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Lifts enemies in a field, then slams them into the ground with a shockwave. */
    public static boolean gravityCrush(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 24);
        double r = 5.5 * c.radius;
        Magic.snd(c.level, center, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.7f);
        EffectManager.run(c.level, 28, age -> {
            Magic.ring(c.level, Magic.dust(c, 1.5f), center, r * (1.0 - Math.min(age, 14) / 28.0), 40, 0.1);
            if (age < 14) {
                Magic.fx(c.level, ParticleTypes.PORTAL, center.add(0, 2, 0), 12, r * 0.3, 1.5, r * 0.3, 0.5);
                for (LivingEntity e : Magic.enemies(c, center, r)) {
                    Vec3 d = center.subtract(e.position());
                    Magic.push(e, new Vec3(d.x * 0.06, 0.38, d.z * 0.06));
                }
            } else if (age == 14) {
                for (LivingEntity e : Magic.enemies(c, center, r)) Magic.push(e, new Vec3(0, -2.8, 0));
                Magic.snd(c.level, center, SoundEvents.ANVIL_LAND, 1f, 0.5f);
            }
            return true;
        }, () -> {
            for (LivingEntity e : Magic.enemies(c, center, r)) {
                Magic.hurt(c, e, 7f);
                Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
            }
            Magic.fx(c.level, ParticleTypes.EXPLOSION, center, 4, r * 0.4, 0.2, r * 0.4, 0);
            Magic.ring(c.level, ParticleTypes.SQUID_INK, center, r * 0.8, 50, 0.2);
            Magic.snd(c.level, center, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.6f);
        });
        return true;
    }

    /** Ultimate: a black hole with an accretion disc that devours everything nearby and collapses in a blast. */
    public static boolean eventHorizon(SpellContext c) {
        Vec3 center = Magic.aimFar(c, 28, 17).add(0, 4.5, 0);
        well(c, center, 12 * c.radius, 140, 0.55, 2.5f, 16f, true);
        Magic.fx3d(c.level, dev.arcana.entity.FxKind.BLACK_HOLE, center, 7.5f * c.radius, 160, dev.arcana.entity.SpellFxEntity.EASE);
        return true;
    }

    private VoidSpells() {}

}
