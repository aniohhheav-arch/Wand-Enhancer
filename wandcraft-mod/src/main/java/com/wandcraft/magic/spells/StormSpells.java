package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.ELEMENTAL;

import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/** Lightning spells. */
final class StormSpells {
    static final int SPARK = 0xFFF27A;

    private StormSpells() {}

    /** Harmless, non-igniting lightning visual with thunder, centred on {@code at}. */
    static void flash(ServerLevel l, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(l);
        if (b == null) return;
        b.moveTo(at.x, at.y, at.z);
        b.setVisualOnly(true);
        l.addFreshEntity(b);
    }

    static int strike(ServerLevel l, ServerPlayer owner, Vec3 at, double radius, float damage) {
        flash(l, at);
        Fx.burst(l, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.3, 0), 30, radius * 0.4, 0.4);
        Fx.ring(l, Fx.dust(SPARK, 1.2f), at.add(0, 0.1, 0), radius, 20);
        int hits = 0;
        for (LivingEntity e : Combat.enemiesIn(l, at, radius, owner)) {
            Combat.hurt(l, owner, owner, e, damage);
            Combat.stagger(e, 25);
            if (e.getMaxHealth() <= 30) {
                e.stopUsingItem();
                if (e instanceof Mob m) m.getNavigation().stop();
            }
            hits++;
        }
        return hits;
    }

    static void register() {
        Spells.add(Spell.of("lightning_bolt", "Lightning Bolt", ELEMENTAL, INSTANT).icon(Glyph.BOLT).cost(18).cd(60).level(2)
                .desc("Call a lightning strike on the target. Flash, thunder, and a stagger. It does not set fires.")
                .perks("Stronger strike.", "Unlocks Chain Lightning.")
                .onCast(c -> {
                    Aim.Result r = Aim.trace(c.player, 40);
                    return strike(c.level, c.player, r.pos(), 2.6, c.dmg(8));
                }));

        Spells.add(Spell.of("chain_lightning", "Chain Lightning", ELEMENTAL, INSTANT).icon(Glyph.BOLT).cost(20).cd(50).evolvesFrom("lightning_bolt")
                .desc("Electricity leaps from your target through up to four more enemies, weakening with each jump.")
                .onCast(c -> {
                    LivingEntity first = Aim.entity(c.player, 24);
                    if (first == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    Vec3 from = c.eye().add(c.look().scale(0.7)).add(0, -0.25, 0);
                    LivingEntity cur = first;
                    Set<LivingEntity> seen = new HashSet<>();
                    float dmg = c.dmg(7);
                    int hits = 0;
                    for (int i = 0; i < 5 && cur != null; i++) {
                        seen.add(cur);
                        Vec3 to = cur.position().add(0, cur.getBbHeight() * 0.5, 0);
                        zigzag(c.level, from, to);
                        Combat.hurt(c.level, c.player, c.player, cur, dmg);
                        Combat.stagger(cur, 15);
                        Fx.sound(c.level, to, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35f, 1.8f);
                        hits++;
                        dmg *= 0.75f;
                        from = to;
                        LivingEntity next = null;
                        double best = 49;
                        for (LivingEntity e : Combat.enemiesIn(c.level, to, 7, c.player)) {
                            double d = e.distanceToSqr(to);
                            if (!seen.contains(e) && d < best) {
                                best = d;
                                next = e;
                            }
                        }
                        cur = next;
                    }
                    return hits;
                }));

        Spells.add(Spell.of("thunderstep", "Thunderstep", ELEMENTAL, INSTANT).icon(Glyph.WINGS).cost(16).cd(70).level(3)
                .desc("Dash up to 7 blocks in an electric flash, staggering enemies at the start. Stops at walls.")
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    Vec3 origin = p.position();
                    Vec3 dir = c.look();
                    Vec3 best = null;
                    for (double t = 0.5; t <= 7.0; t += 0.5) {
                        Vec3 delta = dir.scale(t);
                        if (!c.level.noCollision(p, p.getBoundingBox().move(delta))) break;
                        best = origin.add(delta);
                    }
                    if (best == null) {
                        Casting.fail(p, "Path blocked");
                        return -1;
                    }
                    int hits = 0;
                    for (LivingEntity e : Combat.enemiesIn(c.level, origin, 3.2, p)) {
                        Combat.hurt(c.level, p, p, e, c.dmg(2.5));
                        Combat.stagger(e, 25);
                        Combat.pushAway(e, origin, 0.5, 0.15);
                        hits++;
                    }
                    Fx.burst(c.level, ParticleTypes.ELECTRIC_SPARK, origin.add(0, 1, 0), 40, 0.6, 0.5);
                    Fx.ring(c.level, Fx.dust(SPARK, 1.2f), origin.add(0, 0.1, 0), 1.6, 18);
                    Fx.line(c.level, ParticleTypes.ELECTRIC_SPARK, origin.add(0, 1, 0), best.add(0, 1, 0), 0.4);
                    Fx.sound(c.level, origin, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4f, 2f);
                    p.teleportTo(best.x, best.y, best.z);
                    p.fallDistance = 0;
                    Fx.burst(c.level, ParticleTypes.ELECTRIC_SPARK, best.add(0, 1, 0), 20, 0.4, 0.3);
                    return hits;
                }));

        Spells.add(Spell.of("stormcall", "Stormcall", ELEMENTAL, INSTANT).icon(Glyph.WAVE).cost(45).cd(500).level(10)
                .desc("Summon a storm over an area. Marked enemies are struck after a warning rune appears beneath them.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer owner = z.owner();
                        if (owner == null) return;
                        if (z.age % 8 == 0) {
                            Fx.burst(l, ParticleTypes.CLOUD, z.position().add(0, 11, 0), 6, z.radius() * 0.5, 0.01);
                            Fx.burst(l, ParticleTypes.LARGE_SMOKE, z.position().add(0, 11.5, 0), 4, z.radius() * 0.5, 0.01);
                        }
                        if (z.age == 0) Fx.sound(l, z.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
                        if (z.age % 30 != 10) return;
                        List<LivingEntity> foes = new ArrayList<>(Combat.enemiesIn(l, z.position(), z.radius(), owner));
                        java.util.Collections.shuffle(foes);
                        for (LivingEntity e : foes.subList(0, Math.min(2, foes.size()))) {
                            Vec3 mark = e.position();
                            Fx.rune(l, mark.add(0, 0.1, 0), 1.9, SPARK, 14, 0);
                            Fx.sound(l, mark, SoundEvents.BELL_BLOCK, 0.5f, 1.8f);
                            float power = z.power;
                            Scheduler.after(l, 20, () -> {
                                if (!owner.isAlive()) return;
                                int hits = strike(l, owner, mark, 2.3, 7 * power);
                                if (hits > 0) z.credit(hits);
                            });
                        }
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        if (z.age % 3 == 0) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                            Fx.c(z.level(), ParticleTypes.ELECTRIC_SPARK, z.getX() + Math.cos(a) * r, z.getY() + 10.5, z.getZ() + Math.sin(a) * r, 0, -0.1, 0);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 32);
                    if (g == null) return -1;
                    if (Proj.zone(c, c.spell, g, 8f, 240) == null) return -1;
                    Fx.rune(c.level, g.add(0, 0.1, 0), 8, SPARK, 40, 0);
                    return 0;
                }));
    }

    /** Jagged arc of sparks between two points. */
    static void zigzag(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int segs = Math.max(2, (int) (d.length() / 1.2));
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = a.add(d.scale(i / (double) segs));
            if (i < segs) p = p.add((l.random.nextDouble() - .5) * .6, (l.random.nextDouble() - .5) * .6, (l.random.nextDouble() - .5) * .6);
            Fx.line(l, ParticleTypes.ELECTRIC_SPARK, prev, p, 0.35);
            prev = p;
        }
    }
}
