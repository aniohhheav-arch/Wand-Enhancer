package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.TIME;

import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.TimeEvents;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Time: slowing, freezing, rewinding and ageing. No permanent terrain damage; growth is the only world change. */
final class TimeSpells {
    static final int TEAL = TIME.color, GOLD = 0xFFD678;

    private TimeSpells() {}

    static void ring(ServerLevel l, Vec3 c, double r, int pts) {
        Fx.ring(l, Fx.glow(TEAL, 0.7f), c, r, pts);
        Fx.ring(l, Fx.spark(GOLD, 0.5f), c, r * 0.8, pts / 2);
    }

    static void register() {
        Spells.add(Spell.of("slow_field", "Slow Field", TIME, INSTANT).icon(Glyph.HOURGLASS).cost(20).cd(260).level(5).prop("chrono_dial", 0.5f, 0.05)
                .desc("Lay a ticking clock-face on the ground. Enemies and hostile projectiles inside move at a fraction of their speed for six seconds, while you and your allies are hastened.")
                .onCast(c -> {
                    Vec3 at = Aim.ground(c.player, 24);
                    if (at == null) at = c.player.position();
                    if (Proj.zone(c, c.spell, at, 5f, 120) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.5f);
                    ring(c.level, at.add(0, 0.1, 0), 5, 36);
                    return 0;
                })
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer o = z.owner();
                        Vec3 c = z.position();
                        int n = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, c, z.radius(), o)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, Combat.isBoss(e) ? 1 : 2, false, false, true));
                            if (!Combat.isBoss(e)) e.setDeltaMovement(e.getDeltaMovement().multiply(0.7, 1, 0.7));
                            n++;
                        }
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(z.radius()), p -> p.getOwner() != o)) {
                            p.setDeltaMovement(p.getDeltaMovement().scale(0.75));
                            p.hurtMarked = true;
                            n++;
                        }
                        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(z.radius()), e -> e == o || (o != null && e.isAlliedTo(o)))) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 0, false, false, true));
                        }
                        if (z.age % 20 == 0) {
                            z.credit(Math.min(n, 3));
                            Fx.sound(l, c, SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 0.6f);
                        }
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * z.radius();
                        Fx.c(z.level(), Fx.spark(rnd.nextBoolean() ? TEAL : GOLD, 0.45f), z.getX() + Math.cos(a) * r, z.getY() + 0.1, z.getZ() + Math.sin(a) * r, 0, 0.03, 0);
                    }
                }));

        Spells.add(Spell.of("quicken", "Quicken", TIME, INSTANT).icon(Glyph.WINGS).cost(14).cd(300).level(3)
                .desc("Speed up your own time for eight seconds: faster movement and faster hands.")
                .onCast(c -> {
                    ServerPlayer p = c.player;
                    int t = (int) (160 * c.power);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 1, false, false, true));
                    p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, t, 1, false, false, true));
                    ring(c.level, p.position().add(0, 0.2, 0), 1.2, 24);
                    Fx.burst(c.level, Fx.glow(TEAL, 0.9f), p.position().add(0, 1, 0), 20, 0.4, 0.15);
                    Fx.sound(c.level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
                    return 0;
                }));

        Spells.add(Spell.of("rewind", "Rewind", TIME, INSTANT).icon(Glyph.SWIRL).cost(28).cd(600).level(8)
                .desc("Snap back to where you stood four seconds ago, with the health you had then if it was higher.")
                .onCast(c -> {
                    if (!TimeEvents.rewind(c.player, 80)) {
                        Casting.fail(c.player, "Nothing to rewind to");
                        return -1;
                    }
                    return 0;
                }));

        Spells.add(Spell.of("stasis", "Stasis", TIME, INSTANT).icon(Glyph.RING).cost(30).cd(400).level(10)
                .desc("Freeze a creature in a single moment for three seconds. It cannot act, and everything you do to it is stored, then released all at once with interest when time resumes.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 28);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    boolean boss = Combat.isBoss(t);
                    TimeEvents.stasis(c.level, c.player, t, boss ? 40 : 60, boss ? 1.0f : 1.4f);
                    c.hit();
                    return 0;
                }));

        Spells.add(Spell.of("chrono_bubble", "Chrono Bubble", TIME, INSTANT).icon(Glyph.SHIELD).cost(26).cd(360).level(12).prop("dome_blue", 0.34f, 0)
                .desc("Raise a bubble that stops every hostile projectile in mid-air for five seconds, then lets them all go again, a little faster.")
                .onCast(c -> {
                    Vec3 at = c.player.position();
                    if (Proj.zone(c, c.spell, at, 6f, 100) == null) return -1;
                    Fx.sound(c.level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 1.5f);
                    return 0;
                })
                .zone(new ZoneBehavior() {
                    @Override public void start(SpellZone z) { z.followOwner = true; }
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        ServerPlayer o = z.owner();
                        int n = 0;
                        for (Projectile p : l.getEntitiesOfClass(Projectile.class, new AABB(z.position(), z.position()).inflate(z.radius()), p -> p.getOwner() != o && p.isAlive())) {
                            TimeEvents.freeze(p);
                            if (z.age % 8 == 0) Fx.burst(l, Fx.spark(GOLD, 0.5f), p.position(), 2, 0.1, 0.01);
                            n++;
                        }
                        if (n > 0 && z.age % 10 == 0) z.credit(1);
                    }
                    @Override public void end(SpellZone z) {
                        TimeEvents.releaseFrozen((ServerLevel) z.level(), 1.3);
                        Fx.shockwave((ServerLevel) z.level(), Fx.glow(TEAL, 1f), z.position(), z.radius(), 5);
                    }
                }));

        Spells.add(Spell.of("accelerate", "Accelerate", TIME, INSTANT).icon(Glyph.LEAF).cost(18).cd(500).level(6)
                .desc("Spin time forward around you: crops and saplings grow, and baby animals mature.")
                .onCast(c -> {
                    ServerLevel l = c.level;
                    BlockPos o = c.player.blockPosition();
                    int grown = 0, r = 6;
                    for (BlockPos p : BlockPos.betweenClosed(o.offset(-r, -2, -r), o.offset(r, 2, r))) {
                        if (p.distSqr(o) > r * r) continue;
                        BlockState s = l.getBlockState(p);
                        if (s.getBlock() instanceof CropBlock cb && !cb.isMaxAge(s)) {
                            cb.growCrops(l, p, s);
                            Fx.burst(l, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(p), 3, 0.3, 0.02);
                            grown++;
                        } else if (s.getBlock() instanceof SaplingBlock sb && l.random.nextInt(2) == 0) {
                            sb.advanceTree(l, p, s, l.random);
                            grown++;
                        }
                    }
                    for (AgeableMob a : l.getEntitiesOfClass(AgeableMob.class, c.player.getBoundingBox().inflate(r), AgeableMob::isBaby)) {
                        a.setAge(0);
                        Fx.burst(l, ParticleTypes.HAPPY_VILLAGER, a.position().add(0, 0.5, 0), 6, 0.3, 0.02);
                        grown++;
                    }
                    ring(l, c.player.position().add(0, 0.2, 0), r, 48);
                    Fx.sound(l, c.player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.3f);
                    c.hit(Math.min(grown, 3));
                    return 0;
                }));

        Spells.add(Spell.of("entropy", "Entropy", TIME, INSTANT).icon(Glyph.CRACK).cost(34).cd(500).level(14)
                .desc("Age a creature in an instant. Sand pours from it for three seconds, then its body gives out, losing a quarter of its health. Bosses lose far less.")
                .onCast(c -> {
                    LivingEntity t = Aim.entity(c.player, 26);
                    if (t == null) {
                        Casting.fail(c.player, "No target");
                        return -1;
                    }
                    TimeEvents.entropy(c.level, c.player, t, c.power);
                    c.hit();
                    return 0;
                }));
    }
}
