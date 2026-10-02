package com.infinitemultiverse.power;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Per-tick and lifecycle hooks for power systems that keep server-side state (astral projection, mirror dimension, portals). */
public final class PowerHooks {
    private PowerHooks() {
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sixEyes(player);
            }
        }
        com.infinitemultiverse.power.mystic.MysticArts.tick(server);
    }

    static void onLogin(ServerPlayer player) {
        com.infinitemultiverse.power.mystic.MysticArts.onLogin(player);
    }

    static void onLogout(ServerPlayer player) {
    }

    /** Limitless mastery II — Six Eyes: every hostile within 20 blocks is perceived (glowing) through walls. */
    private static void sixEyes(ServerPlayer player) {
        com.infinitemultiverse.power.PowerSet set = PowerManager.powerOf(player, com.infinitemultiverse.core.MultiverseSystem.CURSED_TECHNIQUES);
        if (set == null || !set.id().getPath().equals("limitless")
                || com.infinitemultiverse.power.mastery.Mastery.level(player, com.infinitemultiverse.core.MultiverseSystem.CURSED_TECHNIQUES) < 2) {
            return;
        }
        for (net.minecraft.world.entity.LivingEntity e : com.infinitemultiverse.core.ability.AbilityTargeting.hostilesInRadius(player, 20)) {
            if (e instanceof net.minecraft.world.entity.monster.Enemy) {
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 30, 0, false, false), player);
            }
        }
    }
}
