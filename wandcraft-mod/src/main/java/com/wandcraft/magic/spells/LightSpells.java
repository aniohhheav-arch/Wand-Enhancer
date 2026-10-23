package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.CHARGE;
import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.LIGHT;

import com.wandcraft.entity.SpellBeam;
import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Light Magic: radiance, cleansing and protection. Shadow and undead creatures take extra. */
final class LightSpells {
    static final int GOLD = LIGHT.color;

    private LightSpells() {}

    static ParticleOptions gleam(float s) { return Fx.glow(0xFFE9A0, s); }

    static boolean vulnerable(LivingEntity e) {
        return e.getType().is(EntityTypeTags.UNDEAD) || e instanceof com.wandcraft.entity.ShadowClone c && c.style() == com.wandcraft.entity.ShadowClone.STYLE_SHADOW;
    }

    static void radiance(ServerLevel l, Vec3 at, int n, double spread) {
        Fx.burst(l, gleam(1.4f), at, n, spread, 0.05);
        Fx.burst(l, Fx.spark(0xFFFFFF, 0.9f), at, Math.max(3, n / 2), spread, 0.12);
        Fx.burst(l, ParticleTypes.END_ROD, at, Math.max(2, n / 4), spread, 0.06);
    }

    static void register() {
        Spells.add(Spell.of("radiant_bolt", "Radiant Bolt", LIGHT, INSTANT).icon(Glyph.BOLT).cost(8).cd(12).level(0).echoable()
                .desc("A bright golden bolt that lights its path. Undead and shadow creatures take 60% more damage.")
                .perks("Brighter and cheaper.", "Unlocks Radiant Lance.")
                .projectile(new ProjBehavior() {
                    @Override public void trail(SpellProjectile p) {
                        Fx.c(p.level(), gleam(1.2f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                        Fx.c(p.level(), Fx.spark(0xFFFFFF, 0.5f), p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                        if (p.age % 2 == 0) Fx.c(p.level(), ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
                    }
                    @Override public void tick(SpellProjectile p) {
                        // Leave a short-lived glow along the flight path.
                        if (p.age % 6 == 3) {
                            BlockPos bp = p.blockPosition();
                            if (p.level().getBlockState(bp).isAir()) TempBlocks.place((ServerLevel) p.level(), bp, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12), 18, 1, p.caster());
                        }
                    }
                    @Override public boolean hitEntity(SpellProjectile p, LivingEntity t) {
                        ServerLevel l = (ServerLevel) p.level();
                        float d = p.damage * (vulnerable(t) ? 1.6f : 1f);
                        Combat.hurt(l, p.getOwner(), p, t, d);
                        p.credit(1);
                        radiance(l, p.position(), 12, 0.25);
                        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.8f);
                        return true;
                    }
                    @Override public boolean hitBlock(SpellProjectile p, BlockHitResult r) {
                        radiance((ServerLevel) p.level(), r.getLocation(), 8, 0.2);
                        return true;
                    }
                })
                .onCast(c -> {
                    Fx.sound(c.level, c.eye(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.9f);
                    return Proj.launch(c, c.spell, 2.4, 0, c.dmg(5), 40) == null ? -1 : 0;
                }));

        Spells.add(Spell.of("radiant_lance", "Radiant Lance", LIGHT, CHARGE).icon(Glyph.BEAM).cost(20).cd(60).charge(25).evolvesFrom("radiant_bolt")
                .desc("Radiant Bolt evolved: charge a narrow beam and release it through up to six enemies, weakening with each. A bright trail lingers briefly.")
                .onCast(c -> {
                    double reach = 18 + 14 * c.charge;
                    Vec3 from = c.eye().add(c.look().scale(0.6)).add(0, -0.2, 0);
                    Vec3 to = from.add(c.look().scale(reach));
                    BlockHitResult b = c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
                    if (b.getType() == HitResult.Type.BLOCK) to = b.getLocation();
                    final Vec3 end = to;
                    List<LivingEntity> hit = new ArrayList<>();
                    for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5), e -> Combat.canTarget(c.player, e))) {
                        if (e.getBoundingBox().inflate(0.45 - 0.2 * c.charge).clip(from, end).isPresent()) hit.add(e);
                    }
                    hit.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
                    float dmg = c.dmg(8 + 4 * c.charge);
                    int n = 0;
                    for (LivingEntity e : hit.subList(0, Math.min(6, hit.size()))) {
                        Combat.hurt(c.level, c.player, c.player, e, dmg * (vulnerable(e) ? 1.6f : 1f));
                        radiance(c.level, e.position().add(0, e.getBbHeight() / 2, 0), 10, 0.3);
                        dmg *= 0.82f;
                        n++;
                    }
                    SpellBeam beam = new SpellBeam(c.level, c.player, 0xFFD060);
                    c.level.addFreshEntity(beam);
                    final float len = (float) from.distanceTo(end) + 0.6f;
                    Scheduler.repeat(c.level, 0, 1, 7, i -> {
                        if (!beam.isRemoved()) beam.update(len, 1f);
                        if (i == 6) beam.discard();
                    });
                    Fx.line(c.level, gleam(1f), from, end, 0.7);
                    Fx.sound(c.level, from, SoundEvents.BEACON_POWER_SELECT, 1f, 1.8f);
                    Fx.sound(c.level, from, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.2f, 2f);
                    return n;
                }));

        Spells.add(Spell.of("sunburst", "Sunburst", LIGHT, INSTANT).icon(Glyph.STAR).cost(14).cd(120).level(2)
                .desc("Release a flash that blinds and interrupts ordinary hostile mobs, lights the area and sears undead. The effect is brief.")
                .prop("light_orb", 0.1f, 1.2)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age == 1 && z.prop != null) z.prop.act();
                    }
                })
                .onCast(c -> {
                    Vec3 at = c.player.position().add(0, 1, 0);
                    SpellZone z = Proj.zone(c, c.spell, c.player.position().add(0, 0.5, 0), 6f, 26);
                    radiance(c.level, at, 50, 1.2);
                    Fx.shockwave(c.level, gleam(1.4f), c.player.position().add(0, 0.3, 0), 6, 6);
                    Fx.sound(c.level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 0.7f);
                    BlockPos lp = c.player.blockPosition().above(2);
                    TempBlocks.place(c.level, lp, Blocks.LIGHT.defaultBlockState(), 50, 1, c.player);
                    int hits = 0;
                    for (LivingEntity e : Combat.enemiesIn(c.level, at, 6, c.player)) {
                        if (!Combat.isBoss(e)) {
                            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false));
                            Combat.slow(e, 40, 1);
                            if (e instanceof net.minecraft.world.entity.Mob m) m.setTarget(null);
                        }
                        if (vulnerable(e)) Combat.hurt(c.level, c.player, c.player, e, c.dmg(5));
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, false));
                        hits++;
                    }
                    return z == null ? -1 : hits;
                }));

        Spells.add(Spell.of("holy_chains", "Holy Chains", LIGHT, INSTANT).icon(Glyph.WALL).cost(14).cd(140).level(4)
                .desc("Bind a target in luminous chains that slow it for a few seconds before snapping. Powerful enemies shrug them off faster.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 22);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    int ticks = Combat.isBoss(t) ? 20 : Combat.isHeavy(t) ? 35 : 70;
                    Combat.slow(t, ticks, Combat.isHeavy(t) ? 1 : 4);
                    Fx.sound(c.level, t.position(), SoundEvents.CHAIN_PLACE, 1f, 1.4f);
                    Scheduler.repeat(c.level, 0, 3, ticks / 3, i -> {
                        if (!t.isAlive()) return;
                        for (int k = 0; k < 3; k++) {
                            double a = i * 0.5 + k * Math.PI * 2 / 3;
                            for (double h = 0; h < t.getBbHeight() + 0.2; h += 0.25) {
                                double ang = a + h * 3;
                                Vec3 p = t.position().add(Math.cos(ang) * 0.6, h, Math.sin(ang) * 0.6);
                                c.level.sendParticles(h % 0.5 < 0.25 ? gleam(0.6f) : Fx.spark(0xFFFFFF, 0.4f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                            }
                        }
                    });
                    Scheduler.after(c.level, ticks, () -> {
                        radiance(c.level, t.position().add(0, 1, 0), 18, 0.4);
                        Fx.sound(c.level, t.position(), SoundEvents.CHAIN_BREAK, 1f, 1.2f);
                    });
                    Combat.hurt(c.level, c.player, c.player, t, c.dmg(vulnerable(t) ? 4 : 1.5));
                    return 1;
                }));

        Spells.add(Spell.of("beacon", "Beacon", LIGHT, INSTANT).icon(Glyph.STAR).cost(26).cd(300).level(6).stealthSafe()
                .desc("Conjure a floating light that illuminates dark places for a minute. Cast it normally to leave it where you aim; cast while sneaking and it drifts after you.")
                .prop("light_orb", 0.6f, 0)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer o = z.owner();
                        if (z.data[0] == 1 && o != null) {
                            Vec3 want = o.position().add(0, 2.4, 0).add(-Math.sin(Math.toRadians(o.getYRot() + 90)) * 1.4, 0, Math.cos(Math.toRadians(o.getYRot() + 90)) * 1.4);
                            Vec3 step = want.subtract(z.position()).scale(0.12);
                            z.setPos(z.getX() + step.x, z.getY() + step.y, z.getZ() + step.z);
                        }
                        if (z.age % 5 != 0) return;
                        BlockPos now = BlockPos.containing(z.position());
                        BlockPos old = (BlockPos) z.extra;
                        if (old != null && old.equals(now)) return;
                        if (old != null) TempBlocks.expireNow(l, old);
                        z.extra = TempBlocks.place(l, now, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14), 1200, 1, o) ? now : null;
                    }
                    @Override public void end(SpellZone z) {
                        if (z.extra instanceof BlockPos old) TempBlocks.expireNow((ServerLevel) z.level(), old);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        if (rnd.nextInt(2) == 0) Fx.c(z.level(), gleam(0.5f), z.getX() + (rnd.nextDouble() - .5) * 1.2, z.getY() + (rnd.nextDouble() - .5) * 1.2, z.getZ() + (rnd.nextDouble() - .5) * 1.2, 0, 0.01, 0);
                    }
                })
                .onCast(c -> {
                    for (SpellZone old : Kit.zonesOf(c.player, c.spell.id)) old.discard();
                    Vec3 at = c.player.isShiftKeyDown() ? c.player.position().add(0, 2.4, 0) : Aim.trace(c.player, 24).pos().add(0, 0.6, 0);
                    SpellZone z = Proj.zone(c, c.spell, at, 1f, 1200);
                    if (z == null) return -1;
                    z.data[0] = c.player.isShiftKeyDown() ? 1 : 0;
                    radiance(c.level, at, 20, 0.5);
                    Fx.sound(c.level, at, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.9f);
                    return 0;
                }));

        Spells.add(Spell.of("sanctuary", "Sanctuary", LIGHT, INSTANT).icon(Glyph.SHIELD).cost(28).cd(400).level(8)
                .desc("Raise a glowing circle of pillars. Allies inside take 40% less damage from magic and projectiles. It does not stack and lasts ten seconds.")
                .prop("sanctuary", 0.26f, 0.05)
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        if (z.age % 10 != 0) return;
                        ServerLevel l = (ServerLevel) z.level();
                        int n = 0;
                        for (LivingEntity e : Kit.friendsIn(l, z.position(), z.radius(), z.owner())) {
                            e.getPersistentData().putLong("wc_sanct", l.getGameTime() + 20);
                            if (e.getHealth() < e.getMaxHealth()) e.heal(0.5f);
                            n++;
                        }
                        if (z.age % 40 == 0) z.credit(Math.min(n, 2));
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * z.radius();
                        Fx.c(z.level(), gleam(0.7f), z.getX() + Math.cos(a) * r, z.getY() + 0.2, z.getZ() + Math.sin(a) * r, 0, 0.04, 0);
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 14);
                    Vec3 at = g == null ? c.player.position() : g;
                    if (Proj.zone(c, c.spell, at, 4f, 200) == null) return -1;
                    Fx.rune(c.level, at.add(0, 0.1, 0), 4, 0xFFE08A, 30, 0);
                    radiance(c.level, at.add(0, 0.5, 0), 24, 2.5);
                    Fx.sound(c.level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
                    return 0;
                }));
    }
}
