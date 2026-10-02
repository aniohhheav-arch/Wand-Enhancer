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
        if (DomainManager.hasDomain(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.domain_active"));
            return false;
        }
        double radius = MultiverseConfig.SERVER.domainRadius.get() * type.radiusScale();
        if (DomainManager.overlapsAnother(ctx.player(), radius)) {
            AbilityManager.deny(ctx.player(), Component.translatable("message.infinitemultiverse.domain_clash"));
            return false;
        }
        DomainManager.open(ctx.player(), type);
        return true;
    }
}
