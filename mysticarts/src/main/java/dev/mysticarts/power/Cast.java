package dev.mysticarts.power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Everything a spell needs while it runs. {@code charge} is 0..1 for CHARGE abilities, otherwise 1. */
public record Cast(ServerPlayer player, ServerLevel level, PowerData data, Ability ability, float charge) {
    public boolean sneaking() {
        return player.isShiftKeyDown();
    }

    public int color() {
        return ability.color();
    }

    public Vec3 eye() {
        return player.getEyePosition();
    }

    public Vec3 look() {
        return player.getLookAngle();
    }

    public Vec3 hands() {
        return Aim.hands(player);
    }

    /** Damage scaled by the config and the Dark Dimension vestments. */
    public float damage(float base) {
        return Spells.damage(player, ability, base);
    }
}
