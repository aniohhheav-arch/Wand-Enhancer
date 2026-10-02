package dev.mysticarts.power;

import dev.mysticarts.MaConfig;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.BlockHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spell explosions. Unlike vanilla explosions they never hurt their caster or the caster's allies, and they only
 * break terrain when the server allows it (every break is journaled, so Time Reversal can undo it).
 */
public final class Blast {
    private Blast() {}

    public static void detonate(ServerLevel level, @Nullable Entity caster, Vec3 pos, float radius, float damage, float knockback, int color, boolean terrain) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(radius))) {
            if (e == caster || (caster != null && e.isAlliedTo(caster)) || !e.isAlive()) continue;
            if (caster instanceof Player && e instanceof Player && !level.getServer().isPvpAllowed()) continue;
            double d = e.getBoundingBox().getCenter().distanceTo(pos);
            if (d > radius) continue;
            float falloff = (float) (1.0 - d / radius * 0.6);
            e.hurt(level.damageSources().indirectMagic(caster, caster), damage * falloff);
            Vec3 push = e.position().subtract(pos).normalize().scale(knockback * falloff).add(0, 0.25 * knockback * falloff, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
        Fx.send(level, FxKind.IMPACT, pos, Vec3.ZERO, color, radius, 0, -1);
        Fx.ring(level, pos, new Vec3(0, 1, 0), color, radius * 1.4f, 14);
        Fx.screen(level, pos, radius * 6, Math.min(1.5f, radius * 0.15f), 0, color);
        Fx.sound(level, pos, radius > 5 ? MaSounds.COSMIC_EXPLOSION.get() : MaSounds.BLAST_IMPACT.get(), 1.5f, radius > 5 ? 0.9f : 1f);
        if (terrain && MaConfig.TERRAIN_DESTRUCTION.get()) carve(level, caster, pos, radius * 0.7f);
    }

    /** Breaks a rough sphere of terrain, dropping items, skipping unbreakable blocks and block entities. */
    public static void carve(ServerLevel level, @Nullable Entity caster, Vec3 pos, float radius) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(pos);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            double d = Math.sqrt(p.distSqr(c)) + level.random.nextDouble() * 0.8;
            if (d > radius) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.hasBlockEntity()) continue;
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 50) continue;
            BlockHistory.record(level, p, s);
            level.destroyBlock(p, level.random.nextInt(3) == 0, caster);
        }
    }
}
