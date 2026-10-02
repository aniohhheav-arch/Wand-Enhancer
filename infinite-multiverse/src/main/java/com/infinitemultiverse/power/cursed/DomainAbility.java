package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.power.PowerAbility;
import net.minecraft.network.chat.Component;

public final class DomainAbility extends PowerAbility {
    private final DomainType type;

    public DomainAbility(DomainType type) {
        super(MultiverseSystem.CURSED_TECHNIQUES);
        this.type = type;
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (DomainManager.hasDomain(ctx.player()) || DomainManager.isReserved(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.domain_active"));
            return false;
        }
        double radius = DomainManager.radiusFor(ctx.player(), type);
        if (DomainManager.overlapsAnother(ctx.player(), radius)) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.domain_clash"));
            return false;
        }
        // Domain Expansion cutscene: the barrier closes 46 ticks in, in sync with the client scene.
        var player = ctx.player();
        com.infinitemultiverse.core.cinematic.Cinematics.scene(ctx.level(), com.infinitemultiverse.core.cinematic.SceneIds.DOMAIN_CUTSCENE, player.position(),
                player.getLookAngle(), type.color(), 70, player, (float) DomainManager.radiusFor(player, type),
                com.infinitemultiverse.core.cinematic.Cinematics.domainFlags(type.ordinal()));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 56, 4, false, false));
        com.infinitemultiverse.core.vfx.MultiverseVfx.sound(ctx.level(), player.position(), com.infinitemultiverse.core.registry.ModSounds.STAND_EPITAPH, 1.4f, 0.5f);
        DomainManager.reserve(player);
        com.infinitemultiverse.stand.StandScheduler.later(46, () -> {
            DomainManager.unreserve(player);
            if (player.isAlive() && !DomainManager.hasDomain(player)) {
                DomainManager.open(player, type);
            }
        });
        return true;
    }
}
