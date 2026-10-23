package com.wandcraft.magic;

import com.wandcraft.entity.SpellProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

/** Per-spell projectile logic. Server hooks run on the logical server, {@link #trail} runs on the client. */
public interface ProjBehavior {
    default void tick(SpellProjectile p) {}
    default void trail(SpellProjectile p) {}
    /** Return true if the projectile should be consumed. */
    default boolean hitEntity(SpellProjectile p, LivingEntity target) { return true; }
    default boolean hitBlock(SpellProjectile p, BlockHitResult hit) { return true; }
    default void expire(SpellProjectile p) {}
}
