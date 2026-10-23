package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.FORBIDDEN;

import com.wandcraft.entity.ShadowClone;
import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
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
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Forbidden Magic: rare, spectacular and strictly bounded. Nothing here breaks blocks or kills for good. */
final class ForbiddenSpells {
    static final int CRIMSON = FORBIDDEN.color;

    private ForbiddenSpells() {}

    static void register() {
        Spells.add(Spell.of("arcane_overload", "Arcane Overload", FORBIDDEN, INSTANT).icon(Glyph.STAR).cost(30).cd(600).level(12).masters(2).stealthSafe()
                .desc("Supercharge your next four spells for ten seconds: +35% power and more spectacular effects, at +50% mana cost each.")
                .prop("rune_sigil_red", 1.4f, 0.1)
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerPlayer o = z.owner();
                        if (o == null) { z.discard(); return; }
                        PlayerMagic m = Magic.of(o);
                        if (m.overloadCharges <= 0 || m.overloadUntil <= z.level().getGameTime()) z.discard();
                    }
                    @Override public void fx(SpellZone z) {
                        var o = z.clientOwner();
                        if (o == null) return;
                        var rnd = z.level().random;
                        double a = rnd.nextDouble() * Math.PI * 2;
                        Fx.c(z.level(), Fx.spark(0xFF5080, 0.7f), o.getX() + Math.cos(a) * 0.8, o.getY() + rnd.nextDouble() * 2, o.getZ() + Math.sin(a) * 0.8, 0, 0.05, 0);
                    }
                })
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    m.overloadCharges = 4;
                    m.overloadUntil = c.level.getGameTime() + 200;
                    m.dirty = true;
                    SpellZone z = Proj.zone(c, c.spell, c.player.position(), 1f, 200);
                    if (z != null) z.followOwner = true;
                    Fx.shockwave(c.level, Fx.spark(0xFF5080, 1f), c.player.position().add(0, 0.3, 0), 4, 5);
                    Fx.burst(c.level, Fx.glow(CRIMSON, 1.4f), c.player.position().add(0, 1, 0), 40, 0.6, 0.3);
                    Fx.sound(c.level, c.player.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("black_hole", "Black Hole", FORBIDDEN, INSTANT).icon(Glyph.SWIRL).cost(55).cd(900).level(14).masters(3).health(3f)
                .desc("Open a small singularity where you aim. It drags ordinary enemies and projectiles toward it, then collapses in a controlled implosion. It never destroys blocks and never traps players.")
                .prop("black_hole", 0.14f, 0)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 c = z.position();
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), z.owner())) {
                            if (Combat.isBoss(e)) continue;
                            // Nothing is pulled through walls.
                            net.minecraft.world.phys.HitResult clip = l.clip(new net.minecraft.world.level.ClipContext(c, e.position().add(0, e.getBbHeight() / 2, 0),
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
                            if (clip.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) continue;
                            double k = (e instanceof net.minecraft.world.entity.player.Player ? 0.45 : 1.0) * (Combat.isHeavy(e) ? 0.3 : 1.0);
                            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                            if (to.lengthSqr() > 0.6) {
                                Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.13 * k));
                                double sp = Math.sqrt(v.x * v.x + v.z * v.z);
                                if (sp > 0.8) v = new Vec3(v.x / sp * 0.8, v.y, v.z / sp * 0.8);
                                e.setDeltaMovement(v.x, Math.min(0.4, v.y), v.z);
                                e.hurtMarked = true;
                            }
                            if (z.age % 10 == 0) {
                                Combat.hurt(l, z.owner(), z.owner(), e, 1.5f * z.power);
                                hits++;
                            }
                        }
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(z.radius()), p -> p.getOwner() != z.owner())) {
                            Vec3 v = p.getDeltaMovement().add(c.subtract(p.position()).normalize().scale(0.12));
                            p.setDeltaMovement(v);
                            p.hurtMarked = true;
                            if (p.position().distanceTo(c) < 1.2) p.discard();
                        }
                        z.credit(Math.min(hits, 3));
                        if (z.age % 15 == 0) Fx.sound(l, c, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.5f);
                    }
                    @Override public void end(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        DarkSpells.implode(l, z.position(), z.radius(), 70);
                        Fx.burst(l, Fx.spark(0xFFFFFF, 1.4f), z.position(), 50, 0.3, 0.8);
                        Fx.shockwave(l, Fx.glow(0xFF9A40, 1.5f), z.position(), 6, 6);
                        Fx.sound(l, z.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.4f);
                        Fx.sound(l, z.position(), SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), 5.0, z.owner())) {
                            Combat.hurt(l, z.owner(), z.owner(), e, 14 * z.power);
                            Combat.pushAway(e, z.position(), 0.9, 0.4);
                            n++;
                        }
                        z.credit(n);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        Vec3 c = z.position();
                        for (int i = 0; i < 5; i++) {
                            double a = z.age * 0.4 + i * Math.PI * 2 / 5, r = 1.5 + rnd.nextDouble() * (z.radius() - 1.5);
                            double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
                            Fx.c(z.level(), Fx.spark(rnd.nextBoolean() ? 0xFF9A40 : 0xB06BFF, 0.6f), c.x + ox, c.y + (rnd.nextDouble() - .5) * r * .4, c.z + oz, -ox * 0.08 - oz * 0.06, 0, -oz * 0.08 + ox * 0.06);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 30).pos().add(0, 1.4, 0);
                    if (Proj.zone(c, c.spell, at, 8f, 140) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.WARDEN_EMERGE, 1f, 0.4f);
                    return 0;
                }));

        Spells.add(Spell.of("time_fracture", "Time Fracture", FORBIDDEN, INSTANT).icon(Glyph.RING).cost(50).cd(800).level(16).masters(4).health(2f)
                .desc("Crack time inside a field: enemies and hostile projectiles within slow to a crawl for eight seconds. Your own movement is untouched.")
                .prop("time_ring", 0.62f, 0.1)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer o = z.owner();
                        Vec3 c = z.position();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), o)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, Combat.isBoss(e) ? 1 : 3, false, false, true));
                            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 10, 2, false, false, false));
                            if (!Combat.isBoss(e)) e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1, 0.6));
                            n++;
                        }
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(z.radius()), p -> p.getOwner() != o)) {
                            p.setDeltaMovement(p.getDeltaMovement().scale(0.8));
                            p.hurtMarked = true;
                            if (z.age % 6 == 0) Fx.burst(l, Fx.spark(0xFFD070, 0.6f), p.position(), 2, 0.1, 0.02);
                            n++;
                        }
                        if (z.age % 20 == 0) {
                            z.credit(Math.min(n, 3));
                            Fx.sound(l, c, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 0.5f);
                        }
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                        Fx.c(z.level(), Fx.spark(0xFFD070, 0.5f), z.getX() + Math.cos(a) * r, z.getY() + 0.3 + rnd.nextDouble() * 2.5, z.getZ() + Math.sin(a) * r, 0, 0.005, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 24);
                    Vec3 at = g == null ? c.player.position() : g;
                    if (Proj.zone(c, c.spell, at, 7f, 160) == null) return -1;
                    Fx.rune(c.level, at.add(0, 0.1, 0), 7, 0xFFD070, 40, 0);
                    Fx.sound(c.level, at, SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f);
                    return 0;
                }));

        Spells.add(Spell.of("reality_tear", "Reality Tear", FORBIDDEN, INSTANT).icon(Glyph.BEAM).cost(40).cd(600).level(18).masters(5).health(2f)
                .desc("Rip a jagged crack across the ground ahead. Enemies caught on it are damaged and staggered. The crack closes without leaving a mark.")
                .onCast(c -> {
                    Vec3 d = new Vec3(c.look().x, 0, c.look().z);
                    if (d.lengthSqr() < 1e-3) d = Vec3.directionFromRotation(0, c.player.getYRot());
                    final Vec3 dir = d.normalize();
                    final Vec3 origin = c.player.position().add(dir.scale(1.2));
                    SpellProp crack = SpellProp.spawn(c.level, "reality_crack", origin.add(0, 0.05, 0), 1f, 46);
                    crack.facing((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + 180f, 0f);
                    Fx.sound(c.level, origin, SoundEvents.GLASS_BREAK, 1f, 0.5f);
                    Fx.sound(c.level, origin, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.2f);
                    Set<LivingEntity> hit = new HashSet<>();
                    for (int i = 1; i <= 18; i++) {
                        final int step = i;
                        Scheduler.after(c.level, i / 2, () -> {
                            Vec3 p = origin.add(dir.scale(step));
                            Fx.burst(c.level, Fx.spark(0xFFFFFF, 0.8f), p.add(0, 0.4, 0), 6, 0.5, 0.15);
                            Fx.burst(c.level, Fx.glow(0xA070FF, 1f), p.add(0, 0.2, 0), 4, 0.5, 0.05);
                            for (LivingEntity e : Combat.enemiesIn(c.level, p.add(0, 0.5, 0), 2.2, c.player)) {
                                if (!hit.add(e)) continue;
                                Combat.hurt(c.level, c.player, c.player, e, c.dmg(10));
                                Combat.stagger(e, 40);
                                Combat.push(e, new Vec3(0, 0.5, 0));
                                Magic.credit(c.player, c.spell, 1);
                            }
                        });
                    }
                    return 0;
                }));

        Spells.add(Spell.of("spectral_legion", "Spectral Legion", FORBIDDEN, INSTANT).icon(Glyph.SKULL).cost(60).cd(1200).level(20).masters(6).health(3f)
                .desc("Call five translucent warriors that fight your enemies for fifteen seconds. They are fragile, cannot cast and cannot summon more.")
                .onCast(c -> {
                    IllusionSpells.clearClones(c, ShadowClone.STYLE_SPECTRAL);
                    Vec3 look = c.look();
                    Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
                    Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                    for (int i = -2; i <= 2; i++) {
                        Vec3 at = c.player.position().add(fwd.scale(2.0 - Math.abs(i) * 0.5)).add(side.scale(i * 1.4));
                        ShadowClone w = IllusionSpells.summon(c, ShadowClone.STYLE_SPECTRAL, at, 300, 10, c.dmg(4.5));
                        Fx.burst(c.level, Fx.glow(0x9FE4FF, 1.2f), at.add(0, 1, 0), 16, 0.4, 0.08);
                        Fx.burst(c.level, Fx.spark(0xFFFFFF, 0.8f), at.add(0, 1, 0), 8, 0.4, 0.15);
                    }
                    Fx.shockwave(c.level, Fx.glow(0x9FE4FF, 1.3f), c.player.position().add(0, 0.2, 0), 4, 5);
                    Fx.sound(c.level, c.player.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.8f);
                    Fx.sound(c.level, c.player.position(), SoundEvents.WARDEN_EMERGE, 0.5f, 1.6f);
                    return 0;
                }));

        Spells.add(Spell.of("cataclysm", "Cataclysm", FORBIDDEN, CHARGE).icon(Glyph.BURST).cost(90).cd(1800).charge(80).level(22).masters(7).health(3f)
                .desc("Inscribe a vast rune at a distant point. After a long, dramatic warning a contained burst of arcane fire erupts, hurting everything in the circle. Leaves no craters.")
                .chargeFx((p, f) -> {
                    Vec3 g = Aim.ground(p, 40);
                    if (g != null) {
                        p.serverLevel().sendParticles(Fx.runeDecal(0xFF6040, 3 + f * 7), g.x, g.y + 0.05, g.z, 1, 0, 0, 0, 0);
                    }
                })
                .prop("rune_sigil_red", 0.115f, 0.05)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 c = z.position();
                        double r = z.radius();
                        if (z.age < 100) {
                            if (z.age % 6 == 0) Fx.ring(l, Fx.spark(0xFF6040, 0.6f), c.add(0, 0.2, 0), r * Math.min(1, z.age / 60.0), 24);
                            if (z.age % 10 == 0) Fx.sound(l, c, SoundEvents.WARDEN_HEARTBEAT, 1.5f, 0.4f + z.age * 0.008f);
                            if (z.age >= 30 && z.age % 7 == 0) {
                                double a = l.random.nextDouble() * Math.PI * 2, rr = r * (0.5 + l.random.nextDouble() * 0.5);
                                Vec3 p = c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                                SpellProp.spawn(l, "fire_pillar", p, 0.45f, 18);
                                Fx.sound(l, p, SoundEvents.BLAZE_SHOOT, 0.6f, 0.6f);
                            }
                            if (z.age >= 50 && z.age % 12 == 0) {
                                double a = l.random.nextDouble() * Math.PI * 2, rr = r * l.random.nextDouble();
                                StormSpells.flash(l, c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr));
                            }
                            return;
                        }
                        if (z.age == 100) {
                            SpellProp.spawn(l, "fire_pillar", c, 2.6f, 30);
                            Fx.shockwave(l, Fx.glow(0xFF6040, 2f), c.add(0, 0.3, 0), (float) r, 10);
                            Fx.shockwave(l, Fx.spark(0xFFFFFF, 1.2f), c.add(0, 0.6, 0), (float) r, 10);
                            Fx.burst(l, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 1, 0), 1, 0, 0);
                            Fx.burst(l, ParticleTypes.FLAME, c.add(0, 1, 0), 160, r * 0.5, 0.4);
                            Fx.burst(l, ParticleTypes.END_ROD, c.add(0, 2, 0), 80, r * 0.4, 0.5);
                            Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.4f);
                            Fx.sound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.5f);
                            Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
                            int n = 0;
                            for (LivingEntity e : Combat.enemiesIn(l, c, r, z.owner())) {
                                double fall = 1.0 - 0.6 * (e.position().distanceTo(c) / r);
                                Combat.hurt(l, z.owner(), z.owner(), e, (float) (22 * z.power * fall));
                                Combat.ignite(e, 3);
                                Combat.push(e, new Vec3(0, 0.9, 0));
                                Combat.pushAway(e, c, 1.0 * fall, 0.2);
                                n++;
                            }
                            z.credit(n);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 40);
                    if (g == null) return -1;
                    SpellZone z = Proj.zone(c, c.spell, g, 8f + 4f * c.charge, 130);
                    if (z == null) return -1;
                    Fx.sound(c.level, g, SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 0.6f);
                    return 0;
                }));

        Spells.add(Spell.of("void_collapse", "Void Collapse", FORBIDDEN, CHARGE).icon(Glyph.SWIRL).cost(70).cd(1200).charge(60).level(21).masters(6).health(3f)
                .desc("Charge a huge spatial distortion (you are rooted and vulnerable while preparing), then it draws enemies inward and releases a single powerful shockwave.")
                .chargeFx((p, f) -> {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 3, false, false, false));
                    ServerLevel l = p.serverLevel();
                    Vec3 g = Aim.ground(p, 25);
                    if (g != null) DarkSpells.implode(l, g.add(0, 1.5, 0), 2 + f * 6, 8);
                })
                .prop("void_orb", 0.12f, 1.5)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        Vec3 c = z.position().add(0, 1.5, 0);
                        double r = z.radius();
                        if (z.age < 45) {
                            int n = 0;
                            for (LivingEntity e : Combat.enemiesIn(l, c, r, z.owner())) {
                                if (Combat.isBoss(e)) continue;
                                double k = Combat.isHeavy(e) ? 0.35 : 1.0;
                                Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                                if (to.lengthSqr() > 1.0) {
                                    Vec3 v = e.getDeltaMovement().add(to.normalize().scale(0.16 * k));
                                    double sp = Math.sqrt(v.x * v.x + v.z * v.z);
                                    if (sp > 0.85) v = new Vec3(v.x / sp * 0.85, v.y, v.z / sp * 0.85);
                                    e.setDeltaMovement(v.x, Math.min(0.4, v.y), v.z);
                                    e.hurtMarked = true;
                                    n++;
                                }
                            }
                            if (z.age % 6 == 0) DarkSpells.implode(l, c, r, 14);
                            if (z.age % 15 == 0) Fx.sound(l, c, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.4f);
                            return;
                        }
                        if (z.age == 45) {
                            DarkSpells.implode(l, c, r, 60);
                            Fx.shockwave(l, Fx.glow(0xB06BFF, 1.8f), z.position().add(0, 0.4, 0), (float) r, 9);
                            Fx.shockwave(l, Fx.spark(0xFFFFFF, 1.1f), z.position().add(0, 1.0, 0), (float) r, 9);
                            Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
                            Fx.sound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 0.6f);
                            int n = 0;
                            for (LivingEntity e : Combat.enemiesIn(l, c, r, z.owner())) {
                                double fall = 1.0 - 0.4 * (e.position().distanceTo(c) / r);
                                Combat.hurt(l, z.owner(), z.owner(), e, (float) (18 * z.power * fall));
                                Combat.pushAway(e, c, 1.8 * fall, 0.5);
                                n++;
                            }
                            z.credit(n);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 25);
                    Vec3 at = g == null ? c.player.position().add(c.look().scale(8)) : g;
                    if (Proj.zone(c, c.spell, at, 9f, 60) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.END_PORTAL_SPAWN, 1.2f, 0.5f);
                    return 0;
                }));
    }
}
