package com.infinitemultiverse.core.ability;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Implemented by toggle abilities that react to damage taken by their owner while active (shields, barriers). */
public interface OwnerDamageInterceptor {
    void onOwnerIncomingDamage(AbilityContext ctx, LivingIncomingDamageEvent event);
}
