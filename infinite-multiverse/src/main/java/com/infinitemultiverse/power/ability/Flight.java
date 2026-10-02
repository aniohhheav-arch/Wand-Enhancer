package com.infinitemultiverse.power.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Grants creative-style flight through NeoForge's flight attribute, so it stacks cleanly with other flight sources. */
public final class Flight {
    private Flight() {
    }

    public static void grant(ServerPlayer player, ResourceLocation source) {
        AttributeInstance attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute != null && !attribute.hasModifier(source)) {
            attribute.addTransientModifier(new AttributeModifier(source, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void revoke(ServerPlayer player, ResourceLocation source) {
        AttributeInstance attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute != null) {
            attribute.removeModifier(source);
        }
        if (!player.isCreative() && !player.isSpectator() && (attribute == null || attribute.getValue() < 1.0)) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
}
