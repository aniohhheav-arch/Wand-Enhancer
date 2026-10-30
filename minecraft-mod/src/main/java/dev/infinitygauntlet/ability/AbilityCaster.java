package dev.infinitygauntlet.ability;

import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.network.CooldownSyncPayload;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModDataComponents;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.registry.ModTriggers;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import dev.infinitygauntlet.network.ModNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The single server-side path every ability cast goes through: validation, cooldowns, feedback and sync. */
public final class AbilityCaster {
    public static void tryCast(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        GauntletState state = stack.getOrDefault(ModDataComponents.GAUNTLET_STATE, GauntletState.EMPTY);
        Ability ability = selectedAbility(state);
        if (ability == null) {
            fail(player, Component.translatable(state.stones().isEmpty()
                    ? "message.infinitygauntlet.no_stones" : "message.infinitygauntlet.stone_dormant", state.selected().displayName()));
            return;
        }
        cast(player, hand, stack, ability, false);
    }

    /**
     * Runs {@code ability}. With {@code ignoreCooldown} (testing commands) the cooldown is neither checked nor started.
     */
    public static boolean cast(ServerPlayer player, InteractionHand hand, ItemStack stack, Ability ability, boolean ignoreCooldown) {
        ServerLevel level = player.serverLevel();
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        long now = level.getGameTime();
        long end = data.cooldownEnd(ability.id());
        AbilityContext ctx = new AbilityContext(player, level, stack, hand);
        if (!ignoreCooldown && end > now && !ability.bypassesCooldown(ctx)) {
            fail(player, Component.translatable("message.infinitygauntlet.cooldown", ability.displayName(),
                    String.format("%.1f", (end - now) / 20.0)));
            return false;
        }
        CastResult result = ability.cast(ctx);
        if (result.message() != null) {
            player.displayClientMessage(result.message(), true);
        }
        if (!result.success()) {
            level.playSound(null, player.blockPosition(), ModSounds.GAUNTLET_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        if (result.startCooldown() && !ignoreCooldown && !player.isCreative()) {
            data.setCooldownEnd(ability.id(), now + ServerConfig.cooldown(ability));
            syncCooldowns(player);
        }
        if (stack.getItem() instanceof InfinityGauntletItem gauntlet) {
            gauntlet.playCastAnimation(player, stack, ability.stone());
        }
        ModTriggers.ABILITY_USED.get().trigger(player, ability.id().toString());
        return true;
    }

    @Nullable
    public static Ability selectedAbility(GauntletState state) {
        InfinityStone stone = state.selected();
        if (!state.has(stone)) {
            return null;
        }
        List<Ability> list = Abilities.forStone(stone);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(Math.floorMod(state.abilityIndex(stone), list.size()));
    }

    /** Handles the client's selection request, validating it against the gauntlet actually held. */
    public static void select(ServerPlayer player, int stoneOrdinal, int abilityIndex) {
        if (stoneOrdinal < 0 || stoneOrdinal >= InfinityStone.values().length) {
            return;
        }
        InfinityStone stone = InfinityStone.values()[stoneOrdinal];
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof InfinityGauntletItem)) {
                continue;
            }
            GauntletState state = stack.getOrDefault(ModDataComponents.GAUNTLET_STATE, GauntletState.EMPTY);
            if (!state.has(stone)) {
                return;
            }
            int count = Math.max(1, Abilities.forStone(stone).size());
            stack.set(ModDataComponents.GAUNTLET_STATE, state.withSelection(stone, Math.floorMod(abilityIndex, count)));
            return;
        }
    }

    public static void syncCooldowns(ServerPlayer player) {
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        long now = player.serverLevel().getGameTime();
        Map<String, Integer> remaining = new HashMap<>();
        data.cooldowns().forEach((id, end) -> {
            if (end > now) {
                remaining.put(id, (int) Math.min(Integer.MAX_VALUE, end - now));
            }
        });
        ModNetwork.sendTo(player, new CooldownSyncPayload(remaining));
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.GAUNTLET_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    private AbilityCaster() {
    }
}
