package com.wandcraft.magic.spells;

import static com.wandcraft.magic.CastMode.INSTANT;
import static com.wandcraft.magic.Category.ELEMENTAL;

import com.wandcraft.entity.SpellZone;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Glyph;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.ZoneBehavior;
import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.Combat;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.Proj;
import com.wandcraft.magic.util.Scheduler;
import com.wandcraft.magic.util.TempBlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Earth and sand spells. */
final class EarthSpells {
    private EarthSpells() {}

    static BlockParticleOption crumble(BlockState s) { return new BlockParticleOption(ParticleTypes.BLOCK, s); }

    /** Throws visual copies of the ground into the air along the wave front. Nothing in the world changes. */
    static void launchDebris(ServerLevel l, net.minecraft.server.level.ServerPlayer owner, Vec3 centre, Vec3 dir, BlockPos standing) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int n = com.wandcraft.magic.util.Fx.scaled(3);
        for (int i = 0; i < n; i++) {
            double off = (l.random.nextDouble() - 0.5) * 2.6;
            BlockPos bp = BlockPos.containing(centre.add(side.scale(off))).below();
            BlockState st = l.getBlockState(bp);
            if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
                st = l.getBlockState(standing.below());
                if (st.isAir() || st.hasBlockEntity() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
            }
            com.wandcraft.entity.DebrisEntity d = new com.wandcraft.entity.DebrisEntity(l, st);
            Vec3 p = centre.add(side.scale(off)).add(0, 0.6, 0);
            d.setPos(p.x, p.y, p.z);
            d.setDeltaMovement(dir.scale(0.12 + l.random.nextDouble() * 0.15).add(side.scale((l.random.nextDouble() - 0.5) * 0.25)).add(0, 0.45 + l.random.nextDouble() * 0.4, 0));
            l.addFreshEntity(d);
        }
    }

    static void register() {
        Spells.add(Spell.of("stone_spike", "Stone Spike", ELEMENTAL, INSTANT).icon(Glyph.SPIKE).cost(14).cd(70).level(1)
                .desc("Raise a stone pillar under a target or the ground you aim at. It launches and staggers, then crumbles.")
                .perks("Stronger impact.", "Unlocks Seismic Wave.")
                .onCast(c -> {
                    Aim.Result r = Aim.trace(c.player, 20);
                    BlockPos base;
                    int hits = 0;
                    if (r.hitEntity()) {
                        LivingEntity t = r.entity();
                        base = t.blockPosition();
                        Combat.hurt(c.level, c.player, c.player, t, c.dmg(5));
                        Combat.push(t, new Vec3(0, 0.9, 0));
                        Combat.stagger(t, 30);
                        hits = 1;
                    } else {
                        Vec3 g = Aim.ground(c.player, 20);
                        if (g == null) return -1;
                        base = BlockPos.containing(g);
                    }
                    Fx.burst(c.level, crumble(Blocks.STONE.defaultBlockState()), Vec3.atBottomCenterOf(base), 25, 0.4, 0.2);
                    Fx.sound(c.level, Vec3.atCenterOf(base), SoundEvents.STONE_BREAK, 1f, 0.6f);
                    final BlockPos b = base;
                    Scheduler.after(c.level, 6, () -> {
                        for (int h = 0; h < 3; h++) {
                            BlockPos p = b.above(h);
                            if (!Proj.pointFree(c.player, p)) continue;
                            BlockState state = h < 2 ? Blocks.STONE.defaultBlockState() : Blocks.POINTED_DRIPSTONE.defaultBlockState();
                            TempBlocks.place(c.level, p, state, 120, 1, c.player);
                        }
                    });
                    return hits;
                }));

        Spells.add(Spell.of("earth_wall", "Earth Wall", ELEMENTAL, INSTANT).icon(Glyph.WALL).cost(24).cd(160).level(5)
                .desc("Raise a curved wall of cobblestone in front of you. Each block withstands three hits and fades after 10 seconds.")
                .onCast(c -> {
                    float yaw = c.player.getYRot();
                    int placed = 0;
                    for (int i = -4; i <= 4; i++) {
                        double ang = Math.toRadians(yaw + i * 14);
                        Vec3 v = new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(3.4);
                        BlockPos probe = BlockPos.containing(c.player.getX() + v.x, c.player.getY() + 1, c.player.getZ() + v.z);
                        var g = Aim.stepGround(c.level, probe.below());
                        if (g.isEmpty()) continue;
                        BlockPos gp = g.get();
                        final int col = Math.abs(i);
                        for (int h = 0; h < 3; h++) {
                            final BlockPos bp = gp.above(h);
                            Scheduler.after(c.level, h * 2 + col / 2, () -> {
                                if (!Proj.pointFree(c.player, bp)) return;
                                if (TempBlocks.place(c.level, bp, Blocks.COBBLESTONE.defaultBlockState(), 200, 3, c.player)) {
                                    Fx.burst(c.level, crumble(Blocks.COBBLESTONE.defaultBlockState()), Vec3.atCenterOf(bp), 6, 0.3, 0.1);
                                    Fx.sound(c.level, Vec3.atCenterOf(bp), SoundEvents.STONE_PLACE, 0.6f, 0.7f);
                                }
                            });
                        }
                        placed++;
                    }
                    return placed == 0 ? -1 : 0;
                }));

        Spells.add(Spell.of("seismic_wave", "Seismic Wave", ELEMENTAL, INSTANT).icon(Glyph.WAVE).cost(24).cd(120).evolvesFrom("stone_spike")
                .desc("A shockwave runs along the ground, staggering and shoving everything in its path. Walls stop it.")
                .onCast(c -> {
                    Vec3 d = new Vec3(c.look().x, 0, c.look().z);
                    if (d.lengthSqr() < 1e-3) d = Vec3.directionFromRotation(0, c.player.getYRot());
                    final Vec3 dir = d.normalize();
                    final Vec3 origin = c.player.position();
                    Set<LivingEntity> hit = new HashSet<>();
                    boolean[] stopped = {false};
                    Fx.sound(c.level, origin, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 0.5f);
                    for (int i = 1; i <= 16; i++) {
                        final int step = i;
                        Scheduler.after(c.level, i, () -> {
                            if (stopped[0]) return;
                            Vec3 p = origin.add(dir.scale(step));
                            var g = Aim.stepGround(c.level, BlockPos.containing(p).below());
                            if (g.isEmpty()) { stopped[0] = true; return; }
                            Vec3 centre = Vec3.atBottomCenterOf(g.get());
                            BlockState under = c.level.getBlockState(g.get().below());
                            Fx.burst(c.level, crumble(under), centre.add(0, 0.2, 0), 14, 0.7, 0.2);
                            launchDebris(c.level, c.player, centre, dir, g.get());
                            if (step % 3 == 0) Fx.sound(c.level, centre, SoundEvents.STONE_BREAK, 0.5f, 0.6f);
                            for (LivingEntity e : Combat.enemiesIn(c.level, centre.add(0, 0.5, 0), 1.7, c.player)) {
                                if (!hit.add(e)) continue;
                                Combat.hurt(c.level, c.player, c.player, e, c.dmg(5));
                                Combat.stagger(e, 30);
                                Combat.push(e, dir.scale(0.9).add(0, 0.45, 0));
                                Magic.credit(c.player, c.spell, 1);
                            }
                        });
                    }
                    return 0;
                }));

        Spells.add(Spell.of("sand_vortex", "Sand Vortex", ELEMENTAL, INSTANT).icon(Glyph.SWIRL).cost(28).cd(260).level(8)
                .desc("A spinning column of sand drags nearby enemies inward and lifts them. A control spell; strong foes resist.")
                .zone(new ZoneBehavior() {
                    @Override public void tick(SpellZone z) {
                        ServerLevel l = (ServerLevel) z.level();
                        var owner = z.owner();
                        int hits = 0;
                        for (LivingEntity e : Combat.enemiesIn(l, z.position(), z.radius(), owner)) {
                            if (Combat.isBoss(e)) continue;
                            double k = Combat.isHeavy(e) ? 0.25 : 1.0;
                            Vec3 to = z.position().subtract(e.position());
                            Vec3 flat = new Vec3(to.x, 0, to.z);
                            if (flat.lengthSqr() < 0.04) continue;
                            Vec3 in = flat.normalize().scale(0.1 * k);
                            Vec3 tang = new Vec3(-flat.z, 0, flat.x).normalize().scale(0.08 * k);
                            Vec3 v = e.getDeltaMovement().add(in).add(tang);
                            double hs = Math.sqrt(v.x * v.x + v.z * v.z);
                            if (hs > 0.55) v = new Vec3(v.x / hs * 0.55, v.y, v.z / hs * 0.55);
                            e.setDeltaMovement(v.x, Math.min(0.3, v.y + 0.05 * k), v.z);
                            e.hurtMarked = true;
                            e.fallDistance = 0;
                            if (z.age % 20 == 0) {
                                Combat.hurt(l, owner, owner, e, 1.5f * z.power);
                                hits++;
                            }
                        }
                        z.credit(hits);
                        if (z.age % 20 == 0) Fx.sound(l, z.position(), SoundEvents.SAND_BREAK, 0.8f, 0.5f);
                    }
                    @Override public void fx(SpellZone z) {
                        var rnd = z.level().random;
                        BlockParticleOption sand = crumble(Blocks.SAND.defaultBlockState());
                        for (int i = 0; i < 6; i++) {
                            double h = rnd.nextDouble() * 4;
                            double a = z.age * 0.4 + h * 1.2 + i;
                            double r = 0.6 + h * 0.35 * z.radius() / 5;
                            Fx.c(z.level(), sand, z.getX() + Math.cos(a) * r, z.getY() + h, z.getZ() + Math.sin(a) * r, 0, 0.05, 0);
                        }
                    }
                })
                .onCast(c -> {
                    Vec3 g = Aim.ground(c.player, 20);
                    if (g == null) return -1;
                    if (Proj.zone(c, c.spell, g, 5f, 120) == null) return -1;
                    Fx.sound(c.level, g, SoundEvents.SAND_BREAK, 1f, 0.4f);
                    return 0;
                }));
    }
}
