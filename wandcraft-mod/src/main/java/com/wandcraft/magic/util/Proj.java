package com.wandcraft.magic.util;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Spell;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Spawn helpers for projectiles and zones. */
public final class Proj {
    private static final String MARK_KEY = "wc_mark";

    private Proj() {}

    public static SpellProjectile launch(CastCtx c, Spell s, double speed, double spreadDeg, float damage, int life) {
        return launch(c, s, c.eye().add(c.look().scale(0.6)).add(0, -0.15, 0), c.look(), speed, spreadDeg, damage, life);
    }

    public static SpellProjectile launch(CastCtx c, Spell s, Vec3 from, Vec3 dir, double speed, double spreadDeg, float damage, int life) {
        ServerPlayer p = c.player;
        if (!SpellProjectile.canSpawn(p)) return null;
        SpellProjectile sp = new SpellProjectile(c.level, s);
        sp.setOwner(p);
        sp.setPos(from);
        double spread = Math.toRadians(spreadDeg);
        Vec3 d = dir.normalize();
        if (spread > 0) {
            Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = d.cross(up).normalize();
            Vec3 realUp = right.cross(d).normalize();
            double a = c.level.random.nextDouble() * Math.PI * 2;
            double r = Math.tan(spread) * Math.sqrt(c.level.random.nextDouble());
            d = d.add(right.scale(Math.cos(a) * r)).add(realUp.scale(Math.sin(a) * r)).normalize();
        }
        sp.setDeltaMovement(d.scale(speed));
        sp.damage = damage;
        sp.power = c.power;
        sp.charge = c.charge;
        sp.life = life;
        sp.setYRot((float) (Math.toDegrees(Math.atan2(d.x, d.z))));
        sp.setXRot((float) (Math.toDegrees(Math.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z)))));
        if (s.echoable) applyMark(p, sp);
        c.level.addFreshEntity(sp);
        return sp;
    }

    public static SpellZone zone(CastCtx c, Spell s, Vec3 pos, float radius, int duration) {
        if (!SpellProjectile.canSpawn(c.player)) return null;
        SpellZone z = new SpellZone(c.level, s, c.player, pos, radius, duration);
        z.power = c.power;
        z.charge = c.charge;
        c.level.addFreshEntity(z);
        return z;
    }

    // --- Arcane Mark ---------------------------------------------------------------------------------------------

    public static void setMark(ServerPlayer caster, LivingEntity target, int ticks) {
        // Only one mark per caster: strip it from any previous target.
        for (LivingEntity e : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(64))) {
            if (e.getPersistentData().contains(MARK_KEY) && caster.getUUID().equals(markOwner(e))) clearMark(e);
        }
        var tag = target.getPersistentData();
        tag.putUUID(MARK_KEY, caster.getUUID());
        tag.putLong(MARK_KEY + "_t", target.level().getGameTime() + ticks);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0, false, false, false));
    }

    public static UUID markOwner(LivingEntity e) {
        var tag = e.getPersistentData();
        if (!tag.hasUUID(MARK_KEY) || tag.getLong(MARK_KEY + "_t") < e.level().getGameTime()) return null;
        return tag.getUUID(MARK_KEY);
    }

    public static void clearMark(LivingEntity e) {
        e.getPersistentData().remove(MARK_KEY);
        e.getPersistentData().remove(MARK_KEY + "_t");
        e.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
    }

    /** Make an eligible projectile track the caster's marked enemy if it is within a limited cone and in line of sight. */
    private static void applyMark(ServerPlayer caster, SpellProjectile sp) {
        Vec3 dir = sp.getDeltaMovement().normalize();
        for (LivingEntity e : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(40))) {
            if (!caster.getUUID().equals(markOwner(e))) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(sp.position());
            if (to.length() < 1e-3 || to.normalize().dot(dir) < Math.cos(Math.toRadians(35))) continue;
            if (!caster.hasLineOfSight(e)) continue;
            sp.homingTarget = e.getUUID();
            sp.homingStrength = 0.3;
            clearMark(e);
            return;
        }
    }

    public static boolean pointFree(Entity e, BlockPos p) {
        return e.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty();
    }
}
