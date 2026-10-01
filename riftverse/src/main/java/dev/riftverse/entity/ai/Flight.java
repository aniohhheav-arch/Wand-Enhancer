package dev.riftverse.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/** Shared helpers for creatures that swim through the sky. */
public final class Flight {
    private Flight() {}

    public static PathNavigation navigation(Mob mob, Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(mob, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return this.level.getBlockState(pos).isAir();
            }
        };
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }
}
