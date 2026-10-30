package dev.arcana.spells;

import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.SpellContext;
import dev.arcana.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Chronos: temporal distortions, rewinding, acceleration and full time stop. */
public final class ChronosSpells {

    private static void clock(SpellContext c, Vec3 center, double r, int age) {
        Magic.ring(c.level, Magic.dust(c, 1f), center, r, 40, 0.1);
        double hand = age * 0.25;
        for (int h = 0; h < 2; h++) {
            double a = hand * (h == 0 ? 1 : 0.1) + h * 1.1;
            Magic.line(c.level, ParticleTypes.ENCHANT, center.add(0, 0.2, 0), center.add(Math.cos(a) * r * (h == 0 ? 0.95 : 0.6), 0.2, Math.sin(a) * r * (h == 0 ? 0.95 : 0.6)), 0.6);
        }
    }

    /** A time-dilation field: enemies crawl and projectiles crawl even slower. */
    public static boolean temporalSlow(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 24);
        double r = 4.5 * c.radius + c.charge * 2;
        int duration = 110 + (int) (c.charge * 100);
        Magic.snd(c.level, center, SoundEvents.BELL_RESONATE, 1f, 0.5f);
        EffectManager.run(c.level, duration, age -> {
            clock(c, center, r, age);
            Magic.fx(c.level, ParticleTypes.REVERSE_PORTAL, center.add(0, 1, 0), 3, r * 0.4, 1, r * 0.4, 0.02);
            for (LivingEntity e : Magic.enemies(c, center.add(0, 1, 0), r)) {
                if (age % 10 == 0) Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 24, 3);
                if (age % 20 == 0) Magic.hurt(c, e, 1.5f);
            }
            for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(r, r, r))) {
                if (p.getOwner() != c.player) p.setDeltaMovement(p.getDeltaMovement().scale(0.5));
            }
            if (age % 20 == 0) for (LivingEntity a : Magic.allies(c, center, r)) Magic.effect(a, MobEffects.DIG_SPEED, 30, 1);
            return true;
        }, null);
        return true;
    }

    /** Returns the caster to where they stood (and how healthy they were) about five seconds ago. */
    public static boolean rewind(SpellContext c) {
        PlayerMagic m = c.player.getData(ModAttachments.MAGIC);
        if (m.history.size() < 4) return false;
        PlayerMagic.Snapshot snap = m.history.peekFirst();
        if (!snap.dim().equals(c.level.dimension())) return false;
        Vec3 from = c.player.position();
        Magic.line(c.level, ParticleTypes.REVERSE_PORTAL, from.add(0, 1, 0), snap.pos().add(0, 1, 0), 0.4);
        Magic.line(c.level, Magic.dust(c, 1.2f), from.add(0, 1, 0), snap.pos().add(0, 1, 0), 0.6);
        c.player.teleportTo(snap.pos().x, snap.pos().y, snap.pos().z);
        c.player.setYRot(snap.yRot());
        c.player.setXRot(snap.xRot());
        c.player.resetFallDistance();
        c.player.setHealth(Math.min(c.player.getMaxHealth(), Math.max(c.player.getHealth(), snap.health())));
        c.player.clearFire();
        m.history.clear();
        Magic.fx(c.level, ParticleTypes.REVERSE_PORTAL, snap.pos().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.4);
        Magic.snd(c.level, snap.pos(), SoundEvents.BELL_RESONATE, 1f, 1.8f);
        return true;
    }

    /** Hastens the caster and allies while slowing nearby enemies. */
    public static boolean chronoSurge(SpellContext c) {
        Vec3 center = c.player.position();
        double r = 10 * c.radius;
        int dur = 200 + c.stage * 100;
        for (LivingEntity a : Magic.allies(c, center, r)) {
            a.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            a.removeEffect(MobEffects.WEAKNESS);
            a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1 + c.stage));
            a.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, dur, 1 + c.stage));
            Magic.fx(c.level, ParticleTypes.ENCHANT, a.position().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.8);
        }
        for (LivingEntity e : Magic.enemies(c, center, r)) Magic.effect(e, MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
        EffectManager.run(c.level, 16, age -> {
            clock(c, center, r * (age + 1) / 16.0, age * 4);
            return true;
        }, null);
        Magic.snd(c.level, center, SoundEvents.BELL_BLOCK, 1f, 1.5f);
        return true;
    }

    /** Ultimate: the world stops. Hostile creatures, projectiles and rival players within range are frozen in time. */
    public static boolean timeStop(SpellContext c) {
        Vec3 center = c.player.position();
        double r = 18 * c.radius;
        int duration = 100 + c.stage * 20;
        List<Entity> frozen = new ArrayList<>();
        Map<Entity, Boolean> hadGravity = new HashMap<>();
        for (Entity e : c.level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(r))) {
            boolean ok = (e instanceof LivingEntity le && Magic.isEnemy(c.player, le)) || (e instanceof Projectile p && p.getOwner() != c.player);
            if (!ok || e.position().distanceToSqr(center) > r * r) continue;
            frozen.add(e);
            hadGravity.put(e, !e.isNoGravity());
            e.getPersistentData().putBoolean("arcana_frozen", true);
            dev.arcana.magic.EffectManager.FROZEN.add(e.getUUID());
            if (e instanceof Mob mob) mob.setNoAi(true);
            e.setNoGravity(true);
        }
        Magic.snd(c.level, center, SoundEvents.BELL_RESONATE, 2f, 0.5f);
        Magic.snd(c.level, center, SoundEvents.WARDEN_HEARTBEAT, 2f, 0.5f);
        EffectManager.runSafe(c.level, duration, age -> {
            if (age % 4 == 0) Magic.ring(c.level, Magic.dust(c, 1.4f), center, r * Math.min(1.0, (age + 4) / 20.0), 56, 0.2 + (age % 8) * 0.1);
            for (Entity e : frozen) {
                if (!e.isAlive()) continue;
                if (e instanceof Projectile) e.setDeltaMovement(Vec3.ZERO);
                else e.setDeltaMovement(0, 0, 0);
                e.hurtMarked = true;
                if (age % 5 == 0) Magic.fx(c.level, ParticleTypes.ENCHANT, e.position().add(0, e.getBbHeight() / 2, 0), 3, 0.3, 0.4, 0.3, 0.2);
                if (e instanceof LivingEntity le && !(e instanceof Mob)) Magic.effect(le, MobEffects.MOVEMENT_SLOWDOWN, 5, 9);
            }
            return true;
        }, () -> {
            for (Entity e : frozen) {
                e.getPersistentData().remove("arcana_frozen");
                dev.arcana.magic.EffectManager.FROZEN.remove(e.getUUID());
                if (e instanceof Mob mob) mob.setNoAi(false);
                e.setNoGravity(!hadGravity.getOrDefault(e, true));
            }
            Magic.snd(c.level, center, SoundEvents.BELL_BLOCK, 2f, 0.5f);
            Magic.fx(c.level, ParticleTypes.FLASH, center.add(0, 1, 0), 1, 0, 0, 0, 0);
        });
        return true;
    }

    private ChronosSpells() {}
}
