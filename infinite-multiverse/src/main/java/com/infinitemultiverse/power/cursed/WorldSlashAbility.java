package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.Beams;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.power.mastery.Mastery;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Shrine Mastery V: the World-Cutting Slash — a cut through space that ignores armour and defences. */
public final class WorldSlashAbility extends PowerAbility {
    public WorldSlashAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        if (masteryLevel(player, system()) < Mastery.MAX_LEVEL) {
            AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.needs_mastery", Mastery.roman(Mastery.MAX_LEVEL)));
            return false;
        }
        Beams.Result trace = Beams.trace(player, 40, 3.0, 24);
        Cinematics.scene(ctx.level(), SceneIds.WORLD_SLASH, player.getEyePosition(), player.getLookAngle(), 0xFFFFFF, 30, player, 40f);
        for (LivingEntity target : trace.hits()) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().fellOutOfWorld(), 40f * mastery(player, system()));
        }
        MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_TIME_ERASE, 1.6f, 0.5f);
        MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable("message.infinitemultiverse.shout.world_slash")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 48);
        return true;
    }
}
