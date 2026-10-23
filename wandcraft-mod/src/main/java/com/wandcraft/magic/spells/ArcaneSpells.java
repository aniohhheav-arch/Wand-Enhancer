package com.wandcraft.magic.spells;

import static com.wandcraft.magic.Category.ARCANE;
import static com.wandcraft.magic.CastMode.CHANNEL;
import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.ProjBehavior;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import com.wandcraft.magic.util.TempBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Arcane Magic: precise projectiles, constructs and utility. */
public final class ArcaneSpells {
    private static final int C = ARCANE.color;

    private ArcaneSpells() {}

    public static void register() {
        Spells.add(Spell.of("arcane_bolt", "Arcane Bolt", ARCANE, INSTANT).icon(Glyph.BOLT).cost(6).cd(8).level(0).echoable()
                .desc("A fast, accurate bolt of concentrated magic. The dependable basic spell.")
                .perks("Slightly stronger and cheaper.", "Unlocks Arcane Barrage.")
                .projectile(Kit.bolt(C, ParticleTypes.ENCHANT, SoundEvents.AMETHYST_CLUSTER_HIT, 1f))
                .onCast(c -> {
                    Fx.sound(c.level, c.eye(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.5f, 1.7f);
                    return Proj.launch(c, c.spell, 2.2, 0, c.dmg(5), 40) == null ? -1 : 0;
                }));

        Spells.add(Spell.of("arcane_barrage", "Arcane Barrage", ARCANE, CHANNEL).icon(Glyph.BEAM).cost(14).cd(30).evolvesFrom("arcane_bolt")
                .desc("Hold to hurl a rapid stream of small bolts. Best at medium range.")
                .projectile(Kit.bolt(0x9AB0FF, ParticleTypes.ENCHANT, SoundEvents.AMETHYST_BLOCK_HIT, 0.5f))
                .onChannel((c, t) -> {
                    if (t % 3 == 0) {
                        Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.9f);
                        Proj.launch(c, c.spell, 2.0, 3.0, c.dmg(2.5), 18);
                    }
                    return 0;
                }));

        Spells.add(Spell.of("arcane_burst", "Arcane Burst", ARCANE, INSTANT).icon(Glyph.BURST).cost(18).cd(80).level(1)
                .desc("Detonate energy around you, damaging and shoving back nearby enemies.")
                .onCast(c -> {
                    Vec3 at = c.player.position().add(0, 0.9, 0);
                    float r = 5.5f;
                    Fx.shockwave(c.level, Fx.dust(C, 1.4f), c.player.position().add(0, 0.2, 0), r, 6);
                    Fx.burst(c.level, ParticleTypes.END_ROD, at, 30, 0.6, 0.35);
                    Fx.sound(c.level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.8f);
                    int hits = 0;
                    for (LivingEntity e : Combat.enemiesIn(c.level, at, r, c.player)) {
                        Combat.hurt(c.level, c.player, c.player, e, c.dmg(6));
                        Combat.pushAway(e, c.player.position(), 1.2, 0.4);
                        hits++;
                    }
                    return hits;
                }));

        Spells.add(Spell.of("arcane_beam", "Arcane Beam", ARCANE, CHANNEL).icon(Glyph.BEAM).cost(16).cd(30).level(5)
                .desc("Channel a beam that grows more intense the longer it stays on one target.")
                .onChannel((c, t) -> {
                    Aim.Result r = Aim.trace(c.player, 24);
                    Vec3 start = c.eye().add(c.look().scale(0.8)).add(0, -0.25, 0);
                    Fx.line(c.level, Fx.dust(0x7FA0FF, 0.9f), start, r.pos(), 0.9);
                    if (t % 4 != 0) return 0;
                    UUID last = c.get("last", null);
                    int focus = c.get("focus", 0);
                    if (r.hitEntity()) {
                        focus = r.entity().getUUID().equals(last) ? Math.min(40, focus + 4) : 0;
                        c.state.put("last", r.entity().getUUID());
                        float intensity = 1f + focus / 40f;
                        Combat.hurt(c.level, c.player, c.player, r.entity(), c.dmg(1.6 * intensity));
                        Fx.burst(c.level, ParticleTypes.END_ROD, r.pos(), 3 + (int) (intensity * 2), 0.15, 0.03);
                        c.state.put("focus", focus);
                        Fx.sound(c.level, r.pos(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 0.8f + intensity * 0.5f);
                        c.hit();
                    } else {
                        c.state.put("focus", Math.max(0, focus - 4));
                        Fx.burst(c.level, ParticleTypes.ELECTRIC_SPARK, r.pos(), 2, 0.1, 0.02);
                    }
                    return 0;
                }));

        Spells.add(Spell.of("rune_mine", "Rune Mine", ARCANE, INSTANT).icon(Glyph.RUNE).cost(20).cd(40).level(4)
                .desc("Plant a hidden rune that blasts the first enemy to approach. Up to three at once.")
                .zone(new ZoneBehavior() {
                    @Override public void fx(SpellZone z) {
                        if (z.age % 6 == 0) {
                            boolean armed = z.age >= 20;
                            double a = z.age * 0.15;
                            for (int i = 0; i < 6; i++) {
                                double ang = a + i * Math.PI / 3;
                                Fx.c(z.level(), Fx.dust(armed ? C : 0x445588, 0.8f), z.getX() + Math.cos(ang) * 0.9, z.getY() + 0.05, z.getZ() + Math.sin(ang) * 0.9, 0, 0, 0);
                            }
                        }
                    }
                    @Override public void tick(SpellZone z) {
                        if (z.age < 20 || z.age % 4 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        Entity owner = z.owner();
                        List<LivingEntity> near = Combat.enemiesIn(l, z.position(), 2.5, owner);
                        if (near.isEmpty()) return;
                        Vec3 at = z.position().add(0, 0.4, 0);
                        Fx.burst(l, ParticleTypes.END_ROD, at, 25, 0.5, 0.25);
                        Fx.shockwave(l, Fx.dust(C, 1.3f), z.position().add(0, 0.1, 0), 3.5f, 4);
                        Fx.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.5f);
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), 3.5, owner)) {
                            Combat.hurt(l, owner, owner, e, 9 * z.power);
                            Combat.pushAway(e, z.position(), 0.8, 0.35);
                            hits++;
                        }
                        z.credit(hits);
                        z.discard();
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 12);
                    if (g == null) return -1;
                    List<SpellZone> mines = Kit.zonesOf(c.player, c.spell.id);
                    for (int i = 0; i <= mines.size() - 3; i++) mines.get(i).discard();
                    Proj.zone(c, c.spell, g.add(0, 0.05, 0), 1f, 20 * 60);
                    Fx.rune(c.level, g.add(0, 0.1, 0), 1.0, C, 18, 0);
                    Fx.sound(c.level, g, SoundEvents.AMETHYST_BLOCK_PLACE, 0.8f, 0.7f);
                    return 0;
                }));

        Spells.add(Spell.of("arcane_orb", "Arcane Orb", ARCANE, CHARGE).icon(Glyph.ORB).cost(14).cd(40).charge(40).level(2).echoable()
                .desc("Slow orb that bursts on contact. Hold to grow its size and blast radius.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.cSphere(p.level(), Fx.dust(C, 1.2f), p.position(), p.size(), 6);
                        Fx.c(p.level(), ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) { burst(p); return true; }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) { burst(p); return true; }
                    @Override public void expire(SpellProjectile p) { burst(p); }
                    private void burst(SpellProjectile p) {
                        ServerLevel l = (ServerLevel) p.level();
                        float r = 2f + p.charge * 2.5f;
                        Fx.burst(l, ParticleTypes.END_ROD, p.position(), 30, r * 0.3, 0.25);
                        Fx.shockwave(l, Fx.dust(C, 1.5f), p.position(), r, 5);
                        Fx.sound(l, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.7f);
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, p.position(), r, p.getOwner())) {
                            Combat.hurt(l, p.getOwner(), p, e, p.damage);
                            Combat.pushAway(e, p.position(), 0.5 + p.charge * 0.5, 0.25);
                            hits++;
                        }
                        p.credit(hits);
                    }
                })
                .onCast(c -> {
                    SpellProjectile p = Proj.launch(c, c.spell, 0.6, 0, c.dmg(6 + 4 * c.charge), 50);
                    if (p == null) return -1;
                    p.setSize(0.35f + c.charge * 0.5f);
                    Fx.sound(c.level, c.eye(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.7f, 0.9f);
                    return 0;
                }));

        Spells.add(Spell.of("spell_echo", "Spell Echo", ARCANE, INSTANT).icon(Glyph.RING).cost(12).cd(100).level(6)
                .desc("Repeats your last projectile spell a moment later at reduced power. Cannot echo itself.")
                .onCast(c -> {
                    PlayerMagic m = Magic.of(c.player);
                    Spell s = Spells.get(m.lastEchoSpell);
                    if (s == null || !s.echoable) {
                        Casting.fail(c.player, "Nothing to echo yet");
                        return -1;
                    }
                    float charge = m.lastEchoCharge;
                    Fx.ring(c.level, Fx.dust(C, 1f), c.player.position().add(0, 1, 0), 0.8, 14);
                    Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
                    ServerPlayer p = c.player;
                    Spell self = c.spell;
                    Scheduler.after(c.level, 8, () -> {
                        if (!p.isAlive()) return;
                        if (Casting.run(p, s, charge, true, 0.6f)) Magic.credit(p, self, 1);
                    });
                    return 0;
                }));

        Spells.add(Spell.of("dispel", "Dispel", ARCANE, INSTANT).icon(Glyph.RING).cost(16).cd(60).level(5)
                .desc("Strip magical shields, summoned constructs and hostile spell effects from an area.")
                .onCast(c -> {
                    Vec3 at = Aim.trace(c.player, 24).pos();
                    double r = 4;
                    int removed = 0;
                    for (SpellZone z : c.level.getEntitiesOfClass(SpellZone.class, new net.minecraft.world.phys.AABB(at, at).inflate(r))) {
                        if (!c.player.getUUID().equals(z.ownerId)) { z.discard(); removed++; }
                    }
                    for (SpellProjectile p : c.level.getEntitiesOfClass(SpellProjectile.class, new net.minecraft.world.phys.AABB(at, at).inflate(r))) {
                        if (p.getOwner() != c.player) { p.discard(); removed++; }
                    }
                    for (Entity e : c.level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r),
                            e -> e instanceof EvokerFangs || e instanceof ShulkerBullet)) {
                        e.discard();
                        removed++;
                    }
                    for (net.minecraft.core.BlockPos bp : TempBlocks.within(c.level, at, r)) {
                        TempBlocks.expireNow(c.level, bp);
                        removed++;
                    }
                    for (LivingEntity e : Combat.enemiesIn(c.level, at, r, c.player)) {
                        boolean any = false;
                        for (MobEffectInstance inst : List.copyOf(e.getActiveEffects())) {
                            if (inst.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                                e.removeEffect(inst.getEffect());
                                any = true;
                            }
                        }
                        if (any) removed++;
                    }
                    Fx.shockwave(c.level, Fx.dust(0xFFFFFF, 1.2f), at, (float) r, 5);
                    Fx.burst(c.level, ParticleTypes.WAX_OFF, at, 20, 1.5, 0.1);
                    Fx.sound(c.level, at, SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.6f);
                    if (removed == 0) {
                        Casting.fail(c.player, "Nothing to dispel");
                        return -1;
                    }
                    return Math.min(removed, 4);
                }));

        Spells.add(Spell.of("arcane_mark", "Arcane Mark", ARCANE, INSTANT).icon(Glyph.EYE).cost(8).cd(40).level(1)
                .desc("Brand an enemy. Your next projectile spell curves toward the mark within a limited angle.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 40);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    Proj.setMark(c.player, t, 20 * 12);
                    Fx.rune(c.level, t.position().add(0, 0.1, 0), 0.9, C, 14, 0);
                    Fx.burst(c.level, ParticleTypes.ENCHANT, t.position().add(0, t.getBbHeight() + 0.3, 0), 12, 0.3, 0.2);
                    Fx.sound(c.level, t.position(), SoundEvents.EVOKER_CAST_SPELL, 0.6f, 1.6f);
                    return 1;
                }));

        Spells.add(Spell.of("mana_pulse", "Mana Pulse", ARCANE, INSTANT).icon(Glyph.RING).cost(10).cd(100).level(1)
                .desc("A pulse that reveals nearby creatures with a magical glow. Barely hurts; mostly a detector.")
                .onCast(c -> {
                    Vec3 at = c.player.position().add(0, 0.2, 0);
                    Fx.shockwave(c.level, Fx.dust(C, 1.2f), at, 18, 9);
                    Fx.sound(c.level, at, SoundEvents.BEACON_AMBIENT, 0.9f, 1.8f);
                    int seen = 0;
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, c.player.getBoundingBox().inflate(18),
                            e -> e != c.player && e.isAlive())) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140, 0, false, false, false));
                        if (Combat.isHostile(c.player, e)) {
                            Combat.hurt(c.level, c.player, c.player, e, 0.5f * c.power);
                            seen++;
                        }
                    }
                    return seen;
                }));
    }
}
