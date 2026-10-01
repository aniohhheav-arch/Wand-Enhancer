package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandManager;
import com.infinitemultiverse.stand.StandType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Summon / dismiss toggle shared by every Stand. Follows its user across dimensions while active. */
public final class ManifestStandAbility extends Ability {
    public ManifestStandAbility() {
        super(MultiverseSystem.STANDS, ActivationType.TOGGLE, false);
    }

    @Override
    public float energyCost() {
        return MultiverseConfig.SERVER.standManifest.cost();
    }

    @Override
    public int cooldownTicks() {
        return MultiverseConfig.SERVER.standManifest.cooldown();
    }

    @Override
    public float upkeepPerSecond() {
        return MultiverseConfig.SERVER.standUpkeep.get().floatValue();
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        StandType type = StandManager.standTypeOf(player);
        if (type == null) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_stand"));
            return false;
        }
        StandEntity stand = StandManager.summon(player, type);
        MultiverseVfx.sound(ctx.level(), stand.position(), ModSounds.STAND_SUMMON, 1.0f, 1.0f);
        MultiverseVfx.broadcast(ctx.level(), VfxIds.STAND_SUMMON, stand.position(), Vec3.ZERO, colorScale(type));
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        if (StandManager.get(ctx.player()) == null) {
            StandType type = StandManager.standTypeOf(ctx.player());
            if (type == null) {
                AbilityManager.deactivate(ctx.player(), ctx.data(), this, DeactivationReason.ADMIN);
                return;
            }
            StandManager.summon(ctx.player(), type);
        }
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        StandEntity stand = StandManager.get(ctx.player());
        if (stand != null && reason != DeactivationReason.LOGGED_OUT) {
            MultiverseVfx.sound(ctx.level(), stand.position(), ModSounds.STAND_DISMISS, 0.9f, 1.0f);
            StandType type = stand.standType();
            MultiverseVfx.broadcast(ctx.level(), VfxIds.STAND_DISMISS, stand.position(), Vec3.ZERO, type == null ? 0f : colorScale(type));
        }
        StandManager.dismiss(ctx.player());
    }

    /** Packs the Stand's RGB colour into the effect's float scale so clients can tint the effect. */
    private static float colorScale(StandType type) {
        return (float) type.color();
    }
}
