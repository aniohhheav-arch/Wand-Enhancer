package dev.arcana.spells;

import dev.arcana.ArcanaConfig;
import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellContext;
import dev.arcana.magic.SpellSlot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The Forbidden Wand: no school, only stolen and unstable magic. */
public final class ForbiddenSpells {
    private static final Block[] WARPED = {Blocks.CRYING_OBSIDIAN, Blocks.AMETHYST_BLOCK, Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.MAGENTA_GLAZED_TERRACOTTA, Blocks.OBSIDIAN};

    /** Casts another school's primary spell. Only schools cast within the mimic window are available (checked by the cast pipeline). */
    public static boolean mimic(SpellContext c, School copied) {
        Spell target = Spell.of(copied, SpellSlot.PRIMARY);
        if (target == null) return false;
        Vec3 tip = dev.arcana.magic.CastService.tip(c.player);
        Magic.fx(c.level, School.dust(copied.color, 1.5f), tip, 16, 0.25, 0.25, 0.25, 0.1);
        SpellContext inner = SpellContext.of(c.player, c.wand, target, c.charge);
        return target.impl.cast(inner);
    }

    /** An unstable rift that pulls, shoves, scatters and lashes enemies at random. */
    public static boolean unstableRift(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 24).add(0, 1.6, 0);
        double r = 7 * c.radius;
        var dust = Magic.dust(c, 1.6f);
        Magic.snd(c.level, center, SoundEvents.END_PORTAL_SPAWN, 0.8f, 1.6f);
        EffectManager.run(c.level, 200, age -> {
            if (!c.valid()) return false;
            double spin = age * 0.4;
            for (int i = 0; i < Magic.count(6); i++) {
                double a = spin + i * 1.047, d = 0.6 + (age % 8) * 0.12;
                c.level.sendParticles(i % 2 == 0 ? dust : ParticleTypes.REVERSE_PORTAL, center.x + Math.cos(a) * d, center.y + Math.sin(a * 2) * d, center.z + Math.sin(a) * d, 1, 0, 0, 0, 0.02);
            }
            Magic.fx(c.level, ParticleTypes.PORTAL, center, 6, 0.3, 0.9, 0.3, 0.6);
            if (age % 10 != 0) return true;
            List<LivingEntity> targets = Magic.enemies(c, center, r);
            switch (c.level.random.nextInt(4)) {
                case 0 -> targets.forEach(e -> Magic.pull(e, center, 0.9));
                case 1 -> targets.forEach(e -> {
                    Vec3 kb = e.position().subtract(center).normalize().scale(1.2);
                    Magic.push(e, new Vec3(kb.x, 0.4, kb.z));
                });
                case 2 -> {
                    if (!targets.isEmpty()) {
                        LivingEntity e = targets.get(c.level.random.nextInt(targets.size()));
                        for (int t = 0; t < 8; t++) {
                            Vec3 dest = e.position().add(c.level.random.nextInt(13) - 6, 0, c.level.random.nextInt(13) - 6);
                            if (Magic.canStand(c.level, e, dest)) {
                                Magic.fx(c.level, ParticleTypes.PORTAL, e.position().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.5);
                                e.teleportTo(dest.x, dest.y, dest.z);
                                Magic.fx(c.level, ParticleTypes.PORTAL, dest.add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.5);
                                break;
                            }
                        }
                    }
                }
                default -> {
                    for (LivingEntity e : targets) {
                        Magic.line(c.level, dust, center, e.position().add(0, 1, 0), 0.5);
                        Magic.hurt(c, e, 4f);
                    }
                }
            }
            return true;
        }, null);
        return true;
    }

    /** Warps the surface around the caster for a few seconds and tosses enemies into the air. Blocks are always restored. */
    public static boolean terrainWarp(SpellContext c) {
        warp(c, c.player.position(), 8 * c.radius);
        return true;
    }

    public static void warp(SpellContext c, Vec3 center, double r) {
        Magic.snd(c.level, center, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5f, 0.5f);
        Magic.ring(c.level, School.dust(School.NONE.color, 1.6f), center, r, 60, 0.2);
        for (LivingEntity e : Magic.enemies(c, center, r)) {
            Magic.hurt(c, e, 5f);
            Magic.push(e, new Vec3(0, 0.9, 0));
            Magic.effect(e, MobEffects.SLOW_FALLING, 30, 0);
        }
        if (!ArcanaConfig.TERRAIN_DISTORTION.get() || !ArcanaConfig.BLOCK_CHANGES.get()) return;
        List<BlockPos> changed = new ArrayList<>();
        List<BlockState> originals = new ArrayList<>();
        int max = ArcanaConfig.WARP_BLOCKS.get();
        for (int i = 0; i < max * 3 && changed.size() < max; i++) {
            double a = c.level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(c.level.random.nextDouble()) * r;
            int x = (int) Math.floor(center.x + Math.cos(a) * d), z = (int) Math.floor(center.z + Math.sin(a) * d);
            BlockPos pos = new BlockPos(x, c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (Math.abs(pos.getY() - center.y) > 6 || changed.contains(pos)) continue;
            BlockState st = c.level.getBlockState(pos);
            boolean natural = st.is(BlockTags.DIRT) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL);
            if (!natural || c.level.getBlockEntity(pos) != null) continue;
            changed.add(pos);
            originals.add(st);
            c.level.setBlockAndUpdate(pos, WARPED[c.level.random.nextInt(WARPED.length)].defaultBlockState());
        }
        if (changed.isEmpty()) return;
        EffectManager.runSafe(c.level, 140, age -> {
            if (age % 5 == 0) for (BlockPos p : changed) if (c.level.random.nextInt(6) == 0) Magic.fx(c.level, ParticleTypes.PORTAL, Vec3.atCenterOf(p).add(0, 0.8, 0), 2, 0.3, 0.2, 0.3, 0.3);
            return true;
        }, () -> {
            for (int i = 0; i < changed.size(); i++) {
                BlockPos p = changed.get(i);
                Block now = c.level.getBlockState(p).getBlock();
                boolean ours = false;
                for (Block b : WARPED) if (b == now) ours = true;
                if (ours) c.level.setBlockAndUpdate(p, originals.get(i));
            }
        });
    }

    /** Ultimate: tears open a collapsing rift that unleashes every school's power before it implodes. */
    public static boolean riftCollapse(SpellContext c) {
        Vec3 center = Magic.aimPoint(c, 28).add(0, 3, 0);
        double r = 14 * c.radius;
        VoidSpells.well(c, center, r, 120, 0.7, 2f, 28f, true);
        School[] schools = School.real();
        for (int i = 0; i < schools.length; i++) {
            final School s = schools[i];
            EffectManager.later(c.level, 12 + i * 12, () -> {
                Magic.ring(c.level, School.dust(s.color, 2f), center, r * 0.6, 48, 0);
                Magic.fx(c.level, School.dust(s.color, 2.2f), center, 60, 1.5, 1.5, 1.5, 0.5);
                Magic.snd(c.level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.5f + 0.1f * s.ordinal());
                if (!c.valid()) return;
                for (LivingEntity e : Magic.enemies(c, center, r)) Magic.hurt(c, e, 5f);
            });
        }
        EffectManager.later(c.level, 118, () -> warp(c, center.subtract(0, 3, 0), 9 * c.radius));
        return true;
    }

    private ForbiddenSpells() {}
}
