package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.FORCE;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.CastCtx;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Force Magic: momentum, position and pressure. */
final class ForceSpells {
    static final int FORCE_C = FORCE.color;

    private ForceSpells() {}

    /** Entities in a forward cone, nearest first. */
    static List<LivingEntity> cone(CastCtx c, double range, double halfAngleDeg) {
        Vec3 eye = c.eye(), look = c.look();
        double cos = Math.cos(Math.toRadians(halfAngleDeg));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, c.player.getBoundingBox().inflate(range),
                e -> Combat.canTarget(c.player, e))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > range || d < 0.01) continue;
            if (to.scale(1 / d).dot(look) >= cos && c.player.hasLineOfSight(e)) out.add(e);
        }
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(c.player)));
        return out;
    }

    /** Pressure rings travelling along the cone axis. */
    static void coneFx(CastCtx c, double range, double halfAngleDeg, int rgb) {
        Vec3 eye = c.eye().add(0, -0.3, 0), look = c.look();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        for (int s = 1; s <= 6; s++) {
            final double dist = range * s / 6.0;
            final Vec3 r = right, u = up;
            Scheduler.after(c.level, s - 1, () -> {
                double rad = dist * Math.tan(Math.toRadians(halfAngleDeg));
                int n = Fx.scaled(10 + (int) (rad * 5));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Vec3 p = eye.add(look.scale(dist)).add(r.scale(Math.cos(a) * rad)).add(u.scale(Math.sin(a) * rad));
                    c.level.sendParticles(Fx.spark(rgb, 0.7f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                c.level.sendParticles(ParticleTypes.CLOUD, eye.x + look.x * dist, eye.y + look.y * dist, eye.z + look.z * dist, Fx.scaled(3), rad * 0.4, rad * 0.4, rad * 0.4, 0.02);
            });
        }
    }

    static int push(CastCtx c, double range, double halfAngle, double baseStrength, int rgb) {
        List<LivingEntity> hit = cone(c, range, halfAngle);
        coneFx(c, range, halfAngle, rgb);
        Fx.sound(c.level, c.eye(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.8f);
        int n = 0;
        for (LivingEntity e : hit) {
            double falloff = 1.0 - 0.5 * (e.distanceTo(c.player) / range);
            Vec3 dir = e.position().subtract(c.player.position()).multiply(1, 0, 1).normalize();
            Combat.hurt(c.level, c.player, c.player, e, c.dmg(2));
            Combat.push(e, dir.scale(baseStrength * falloff).add(0, 0.3 + baseStrength * 0.12, 0));
            e.stopUsingItem();
            n++;
        }
        return n;
    }

    static void register() {
        Spells.add(Spell.of("force_push", "Force Push", FORCE, CHARGE).icon(Glyph.RING).cost(12).cd(40).charge(25).level(0)
                .desc("Blast a cone of force that shoves enemies back. Charging adds reach and strength. Bosses hold their ground.")
                .perks("Slightly stronger, cheaper.", "Unlocks Force Wave.")
                .onCast(c -> push(c, 6 + 5 * c.charge, 35, 0.9 + 1.1 * c.charge, FORCE_C)));

        Spells.add(Spell.of("force_wave", "Force Wave", FORCE, CHARGE).icon(Glyph.WAVE).cost(20).cd(70).charge(30).evolvesFrom("force_push")
                .desc("A wider, longer-range force cone with far more punch.")
                .onCast(c -> push(c, 10 + 6 * c.charge, 55, 1.3 + 1.4 * c.charge, 0xE8F2FF)));

        Spells.add(Spell.of("force_pull", "Force Pull", FORCE, INSTANT).icon(Glyph.SWIRL).cost(14).cd(60).level(2)
                .desc("Drag an ordinary enemy toward you, or snatch dropped items. Heavy things resist.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 22);
                    Vec3 mid = c.eye().add(c.look().scale(0.5));
                    if (t != null) {
                        if (Combat.isHeavy(t)) {
                            Casting.fail(c.player, "Too heavy to pull");
                            return -1;
                        }
                        Vec3 to = c.player.position().subtract(t.position());
                        double d = to.length();
                        Vec3 v = to.normalize().scale(Math.min(1.7, 0.45 + d * 0.11)).add(0, 0.2, 0);
                        t.setDeltaMovement(v);
                        t.hurtMarked = true;
                        t.fallDistance = 0;
                        Fx.line(c.level, Fx.spark(FORCE_C, 0.6f), mid, t.position().add(0, t.getBbHeight() / 2, 0), 0.6);
                        Fx.sound(c.level, t.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8f, 1.4f);
                        return 1;
                    }
                    Vec3 aim = Aim.trace(c.player, 22).pos();
                    int n = 0;
                    for (ItemEntity item : c.level.getEntitiesOfClass(ItemEntity.class, new AABB(aim, aim).inflate(4))) {
                        Vec3 v = c.player.position().add(0, 0.5, 0).subtract(item.position());
                        item.setDeltaMovement(v.normalize().scale(Math.min(1.1, 0.3 + v.length() * 0.07)).add(0, 0.1, 0));
                        item.hurtMarked = true;
                        Fx.line(c.level, Fx.spark(FORCE_C, 0.5f), mid, item.position(), 0.8);
                        n++;
                    }
                    if (n == 0) {
                        Casting.fail(c.player, "Nothing to pull");
                        return -1;
                    }
                    return 0;
                }));

        Spells.add(Spell.of("levitation", "Levitation", FORCE, INSTANT).icon(Glyph.WINGS).cost(18).cd(120).level(4)
                .desc("Float a target (enemy or willing player) briefly into the air. Powerful enemies float for less time.")
                .onCast(c -> {
                    LivingEntity t = Aim.entityAny(c.player, 24);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    boolean enemy = Combat.canTarget(c.player, t);
                    int ticks = Combat.isBoss(t) ? 20 : Combat.isHeavy(t) ? 40 : 80;
                    t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, 0, false, true, true));
                    if (enemy) Combat.slow(t, ticks, 1);
                    Fx.burst(c.level, Fx.glow(FORCE_C, 1f), t.position().add(0, 0.1, 0), 20, 0.5, 0.05);
                    Fx.shockwave(c.level, Fx.spark(FORCE_C, 0.6f), t.position().add(0, 0.1, 0), 1.6f, 4);
                    Fx.sound(c.level, t.position(), SoundEvents.BREEZE_JUMP, 1f, 1.2f);
                    return enemy ? 1 : 0;
                }));

        Spells.add(Spell.of("gravity_well", "Gravity Well", FORCE, INSTANT).icon(Glyph.SWIRL).cost(30).cd(300).level(7)
                .desc("Plant a sphere of gravity that drags enemies in and bends passing projectiles, then collapses.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        Vec3 centre = z.position();
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, centre, z.radius(), owner)) {
                            if (Combat.isBoss(e)) continue;
                            double k = Combat.isHeavy(e) ? 0.3 : 1.0;
                            Vec3 to = centre.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                            if (to.lengthSqr() < 0.25) continue;
                            Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.1 * k));
                            e.setDeltaMovement(v.x, Math.min(v.y, 0.4), v.z);
                            e.hurtMarked = true;
                            if (z.age % 20 == 0) hits++;
                        }
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(z.radius()), p -> p.getOwner() != owner)) {
                            Vec3 bend = centre.subtract(p.position()).normalize().scale(0.08);
                            p.setDeltaMovement(p.getDeltaMovement().add(bend));
                        }
                        z.credit(hits);
                        if (z.age % 10 == 0) Fx.sound(l, centre, SoundEvents.BEACON_AMBIENT, 0.4f, 0.5f);
                    }
                    @Override public void end(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Fx.burst(l, Fx.spark(0xFFFFFF, 1.2f), z.position(), 40, 0.3, 0.5);
                        Fx.shockwave(l, Fx.glow(FORCE_C, 1.2f), z.position(), z.radius(), 5);
                        Fx.sound(l, z.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius() * 0.6, z.owner())) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 3 * z.power);
                            n++;
                        }
                        z.credit(n);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        Vec3 c = z.position();
                        Fx.cSphere(z.level(), Fx.glow(0x1A1030, 1.8f), c, 0.5, 2);
                        for (int i = 0; i < 4; i++) {
                            double a = z.age * 0.3 + i * Math.PI / 2, r = 0.8 + rnd.nextDouble() * (z.radius() - 0.8);
                            double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
                            Fx.c(z.level(), Fx.spark(FORCE_C, 0.6f), c.x + ox, c.y + (rnd.nextDouble() - .5) * r * .6, c.z + oz, -ox * 0.06 - oz * 0.04, 0, -oz * 0.06 + ox * 0.04);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 28).pos().add(0, 1.0, 0);
                    SpellZone z = Proj.zone(c, c.spell, at, 6f, 100);
                    if (z == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.BEACON_ACTIVATE, 0.9f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("repulsion_field", "Repulsion Field", FORCE, INSTANT).icon(Glyph.RING).cost(16).cd(100).level(3)
                .desc("A brief force bubble around you pushes away enemies and stray projectiles.")
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        if (owner == null) return;
                        Vec3 centre = owner.position().add(0, 1, 0);
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, centre, z.radius(), owner)) {
                            Combat.pushAway(e, owner.position(), 0.4, 0.05);
                            if (z.age % 20 == 0) {
                                Combat.hurt(l, owner, owner, e, 1.0f * z.power);
                                hits++;
                            }
                        }
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(z.radius()), p -> p.getOwner() != owner)) {
                            Vec3 out = p.position().subtract(centre).normalize().scale(p.getDeltaMovement().length() + 0.2);
                            p.setDeltaMovement(out);
                            p.hurtMarked = true;
                        }
                        z.credit(hits);
                    }
                    @Override public void fx(SpellZone z) {
                        var o = z.level().getPlayerByUUID(z.ownerId);
                        if (o == null) return;
                        Fx.cSphere(z.level(), Fx.spark(FORCE_C, 0.6f), o.position().add(0, 1, 0), z.radius() * (0.85 + 0.15 * Math.sin(z.age * 0.5)), 5);
                    }
                })
                .onCast(c -> {
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 4f, 60);
                    if (z == null) return -1;
                    z.followOwner = true;
                    Fx.shockwave(c.level, Fx.spark(FORCE_C, 0.9f), c.player.position().add(0, 0.2, 0), 4, 4);
                    Fx.sound(c.level, c.player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("kinetic_slam", "Kinetic Slam", FORCE, INSTANT).icon(Glyph.BURST).cost(24).cd(160).level(6)
                .desc("Hoist an enemy skyward, then slam it down with a ring of force. A telegraph rune shows where it lands.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 18);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    boolean heavy = Combat.isHeavy(t);
                    Vec3 spot = t.position();
                    Fx.rune(c.level, spot.add(0, 0.1, 0), 2.4, FORCE_C, 16, 0);
                    Fx.sound(c.level, spot, SoundEvents.BREEZE_CHARGE, 1f, 0.8f);
                    if (!heavy) {
                        t.setDeltaMovement(0, 1.15, 0);
                        t.hurtMarked = true;
                    }
                    ServerPlayer p = c.player;
                    Scheduler.after(c.level, 11, () -> {
                        if (!t.isAlive()) return;
                        t.setDeltaMovement(0, -2.6, 0);
                        t.hurtMarked = true;
                    });
                    Scheduler.after(c.level, 17, () -> {
                        if (!t.isAlive()) return;
                        Vec3 at = t.position();
                        Fx.shockwave(c.level, Fx.spark(FORCE_C, 1f), at.add(0, 0.2, 0), 3.5f, 5);
                        Fx.burst(c.level, ParticleTypes.EXPLOSION, at, 1, 0, 0);
                        Fx.sound(c.level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.8f);
                        int hits = 0;
                        Combat.hurt(c.level, p, p, t, c.dmg(heavy ? 3.5 : 8));
                        hits++;
                        for (LivingEntity e : Combat.enemiesIn(c.level, at, 3.5, p)) {
                            if (e == t) continue;
                            Combat.hurt(c.level, p, p, e, c.dmg(4));
                            Combat.pushAway(e, at, 0.7, 0.3);
                            hits++;
                        }
                        Magic.credit(p, c.spell, Math.min(hits, 3));
                    });
                    return 0;
                }));

        Spells.add(Spell.of("force_jump", "Force Jump", FORCE, INSTANT).icon(Glyph.WINGS).cost(12).cd(80).level(1)
                .desc("Launch yourself up and forward. Your landing is cushioned for a moment. Not flight.")
                .onCast(c -> {
                    Vec3 look = c.look();
                    Vec3 h = new Vec3(look.x, 0, look.z);
                    h = h.lengthSqr() < 1e-3 ? Vec3.ZERO : h.normalize().scale(0.55);
                    c.player.setDeltaMovement(h.x, 1.05, h.z);
                    c.player.hurtMarked = true;
                    c.player.fallDistance = 0;
                    Magic.of(c.player).softFallUntil = c.level.getGameTime() + 60;
                    Fx.shockwave(c.level, Fx.spark(FORCE_C, 0.8f), c.player.position().add(0, 0.1, 0), 2.2f, 4);
                    Fx.burst(c.level, ParticleTypes.CLOUD, c.player.position(), 10, 0.3, 0.05);
                    Fx.sound(c.level, c.player.position(), SoundEvents.BREEZE_JUMP, 1f, 1.1f);
                    return 0;
                }));

        Spells.add(Spell.of("kinetic_catch", "Kinetic Catch", FORCE, INSTANT).icon(Glyph.SHIELD).cost(10).cd(100).level(5)
                .desc("A brief field in front of you catches incoming projectiles and turns them into a little mana.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        if (owner == null) return;
                        Vec3 centre = owner.getEyePosition().add(owner.getLookAngle().scale(1.8));
                        z.setPos(centre.x, centre.y, centre.z);
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(z.radius()),
                                p -> p.getOwner() != owner && !(p instanceof WitherSkull) && !(p instanceof net.minecraft.world.entity.projectile.LargeFireball))) {
                            if (z.data[0] >= 15) break;
                            Fx.burst(l, Fx.spark(0xB8F0FF, 0.8f), p.position(), 8, 0.2, 0.15);
                            Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
                            p.discard();
                            z.data[0] += 3;
                            Magic.restore(owner, 3);
                            z.credit(1);
                        }
                    }
                    @Override public void fx(SpellZone z) {
                        var o = z.level().getPlayerByUUID(z.ownerId);
                        if (o == null) return;
                        Vec3 look = o.getLookAngle();
                        Vec3 centre = o.getEyePosition().add(look.scale(1.8));
                        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(look).normalize();
                        for (int i = 0; i < 4; i++) {
                            double a = z.level().random.nextDouble() * Math.PI * 2, r = Math.sqrt(z.level().random.nextDouble()) * z.radius();
                            Vec3 q = centre.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                            Fx.c(z.level(), Fx.glow(0xB8F0FF, 0.7f), q.x, q.y, q.z, 0, 0, 0);
                        }
                    }
                })
                .onCast(c -> {
                    SpellZone z = Proj.zone(c, c.spell, c.eye().add(c.look().scale(1.8)), 1.9f, 40);
                    if (z == null) return -1;
                    Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_CLUSTER_PLACE, 0.8f, 1.5f);
                    return 0;
                }));
    }
}
