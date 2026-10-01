package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Made in Heaven: toggle that keeps accelerating you, one speed tier every 3 seconds up to Speed V, with afterimages. */
public final class AccelerationAbility extends StandAbility {
    private static final int TICKS_PER_TIER = 60;
    private static final int MAX_TIER = 5;
    private static final int COLOR = 0xB8FFE0;

    public AccelerationAbility() {
        super(ActivationType.TOGGLE);
    }

    @Override
    public float upkeepPerSecond() {
        return 4f;
    }

    @Override
    protected boolean requiresReadyStand() {
        return false;
    }

    @Override
    protected boolean activateWithStand(AbilityContext ctx, StandEntity stand) {
        MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.STAND_ACCELERATE, 1.0f, 1.0f);
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        if (StandManager.get(ctx.player()) == null) {
            AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.MANUAL);
            return;
        }
        int tier = Math.min(MAX_TIER, 1 + activeTicks / TICKS_PER_TIER);
        if (activeTicks % 10 == 1) {
            ctx.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, tier - 1, false, false, true));
            ctx.player().addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, Math.min(2, tier - 1), false, false, true));
        }
        if (activeTicks % TICKS_PER_TIER == 0 && tier < MAX_TIER) {
            MultiverseVfx.sound(ctx.level(), ctx.player().position(), ModSounds.STAND_ACCELERATE, 0.6f, 1.0f + tier * 0.1f);
        }
        if (activeTicks % 3 == 0 && ctx.player().getDeltaMovement().horizontalDistanceSqr() > 0.004) {
            MultiverseVfx.broadcast(ctx.level(), VfxIds.AFTERIMAGE, ctx.player().position(), ctx.player().getDeltaMovement(), COLOR);
        }
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        ctx.player().removeEffect(MobEffects.MOVEMENT_SPEED);
        ctx.player().removeEffect(MobEffects.DIG_SPEED);
    }
}
