package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.ZipperEntity;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Validates and dispatches ability activations coming from clients. */
public final class AbilityExecutor {
    public static void tryActivate(ServerPlayer player, int abilityId, boolean alt) {
        StandData data = StandData.get(player);
        if (!data.hasStand() || !player.isAlive() || player.isSpectator()) return;
        AbilityType type = AbilityType.byId(abilityId);

        // Inside zipper space: activating Escape/Ambush again emerges early; everything else is blocked.
        if (data.isHidden()) {
            if (type == AbilityType.ZIPPER_ESCAPE || type == AbilityType.ZIPPER_AMBUSH) Hiding.emerge(player);
            return;
        }
        // Sneak-use while aiming at one of your zippers closes it, regardless of ability.
        if (alt) {
            ZipperEntity aimed = ZipperNetwork.raycast(player, 24);
            if (aimed != null) {
                aimed.closeAndDiscard();
                return;
            }
        }
        if (!SFConfig.enabled(type)) {
            fail(player, "message.stickyfingers.disabled");
            return;
        }
        long now = player.level().getGameTime();
        if (data.cooldownRemaining(type, now) > 0) {
            fail(player, "message.stickyfingers.cooldown");
            return;
        }
        int cost = SFConfig.cost(type);
        if (data.energy() < cost) {
            fail(player, "message.stickyfingers.no_energy");
            return;
        }
        if (Abilities.activate(type, player, data, alt)) {
            data.setEnergy(data.energy() - cost);
            data.setCooldownEnd(type, now + SFConfig.cooldown(type));
            StandManager.sync(player);
        } else if (type != AbilityType.ZIPPER_BODY) {
            fail(player, "message.stickyfingers.no_target");
        }
    }

    static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    private AbilityExecutor() {}
}
