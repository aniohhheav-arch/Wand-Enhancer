package com.infinitemultiverse.core.command;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.stand.StandManager;
import com.infinitemultiverse.stand.StandType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /multiverse} — inspection for everyone, test/admin tooling (permission level 2) for energy, cooldowns,
 * unlocks, bindings and forced activation. This is the Phase 1 testing harness.
 */
public final class MultiverseCommand {
    private static final DynamicCommandExceptionType UNKNOWN_ABILITY = new DynamicCommandExceptionType(
            id -> Component.translatable("command.infinitemultiverse.unknown_ability", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_STAND = new DynamicCommandExceptionType(
            id -> Component.translatable("command.infinitemultiverse.stand.unknown", String.valueOf(id)));
    private static final SuggestionProvider<CommandSourceStack> STAND_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(MultiverseRegistries.STAND_TYPES.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> POWER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(MultiverseRegistries.POWER_SETS.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> ABILITY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(MultiverseRegistries.ABILITIES.keySet(), builder);

    private MultiverseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("multiverse")
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("abilities")
                        .executes(ctx -> listAbilities(ctx.getSource())))
                .then(Commands.literal("energy")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                                .executes(ctx -> setEnergy(ctx, FloatArgumentType.getFloat(ctx, "amount"))))))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> setEnergy(ctx, Float.MAX_VALUE)))))
                .then(Commands.literal("cooldowns")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(MultiverseCommand::resetCooldowns))))
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("all")
                                        .executes(ctx -> unlockAll(ctx)))
                                .then(Commands.argument("ability", ResourceLocationArgument.id())
                                        .suggests(ABILITY_SUGGESTIONS)
                                        .executes(ctx -> setUnlocked(ctx, true)))))
                .then(Commands.literal("lock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("ability", ResourceLocationArgument.id())
                                        .suggests(ABILITY_SUGGESTIONS)
                                        .executes(ctx -> setUnlocked(ctx, false)))))
                .then(Commands.literal("bind")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, PlayerMultiverseData.LOADOUT_SIZE))
                                        .then(Commands.argument("ability", ResourceLocationArgument.id())
                                                .suggests(ABILITY_SUGGESTIONS)
                                                .executes(MultiverseCommand::bind)))))
                .then(Commands.literal("activate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("ability", ResourceLocationArgument.id())
                                .suggests(ABILITY_SUGGESTIONS)
                                .executes(MultiverseCommand::activate)))
                .then(Commands.literal("stand")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("stand", ResourceLocationArgument.id())
                                                .suggests(STAND_SUGGESTIONS)
                                                .executes(MultiverseCommand::setStand))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(MultiverseCommand::clearStand))))
                .then(Commands.literal("power")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("power", ResourceLocationArgument.id())
                                                .suggests(POWER_SUGGESTIONS)
                                                .executes(MultiverseCommand::setPower))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(MultiverseCommand::clearPowers)))
                        .then(Commands.literal("mastery")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                                .executes(MultiverseCommand::setMastery)))))
                .then(Commands.literal("deactivate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(MultiverseCommand::deactivateAll))));
    }

    private static Ability ability(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "ability");
        Ability ability = AbilityManager.lookup(id);
        if (ability == null) {
            throw UNKNOWN_ABILITY.create(id);
        }
        return ability;
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        PlayerMultiverseData data = AbilityManager.data(player);
        source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.header", player.getDisplayName()), false);
        source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.energy",
                java.util.Arrays.toString(data.energiesView()), String.format(Locale.ROOT, "%.0f", AbilityManager.maxEnergy())), false);
        for (int slot = 0; slot < PlayerMultiverseData.LOADOUT_SIZE; slot++) {
            int display = slot + 1;
            String bound = data.loadoutSlot(slot).map(ResourceLocation::toString).orElse("-");
            source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.slot", display, bound), false);
        }
        String cooldowns = data.cooldownsView().entrySet().stream()
                .map(e -> e.getKey().getPath() + "=" + String.format(Locale.ROOT, "%.1fs", e.getValue() / 20f))
                .collect(Collectors.joining(", "));
        String active = data.activeView().stream().map(ResourceLocation::getPath).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.cooldowns", cooldowns.isEmpty() ? "-" : cooldowns), false);
        source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.active", active.isEmpty() ? "-" : active), false);
        source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.info.unlocked", data.unlockedView().size()), false);
        return 1;
    }

    private static int listAbilities(CommandSourceStack source) {
        Map<String, String> bySystem = MultiverseRegistries.ABILITIES.stream()
                .collect(Collectors.groupingBy(a -> a.system().displayName().getString(),
                        Collectors.mapping(a -> a.id().toString(), Collectors.joining(", "))));
        if (bySystem.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.infinitemultiverse.abilities.none"), false);
        }
        bySystem.forEach((system, ids) -> source.sendSuccess(() -> Component.literal(system + ": " + ids), false));
        return bySystem.size();
    }

    private static int setEnergy(CommandContext<CommandSourceStack> ctx, float amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        float max = AbilityManager.maxEnergy();
        for (ServerPlayer player : targets) {
            for (com.infinitemultiverse.core.energy.EnergyPool pool : com.infinitemultiverse.core.energy.EnergyPool.values()) {
                AbilityManager.data(player).setEnergy(pool, Math.min(amount, max), max);
            }
            AbilityManager.syncNow(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.energy.set", targets.size()), true);
        return targets.size();
    }

    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            AbilityManager.data(player).clearCooldowns();
            AbilityManager.syncNow(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.cooldowns.reset", targets.size()), true);
        return targets.size();
    }

    private static int unlockAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            PlayerMultiverseData data = AbilityManager.data(player);
            MultiverseRegistries.ABILITIES.keySet().forEach(data::unlock);
            AbilityManager.syncNow(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.unlock.all", targets.size()), true);
        return targets.size();
    }

    private static int setUnlocked(CommandContext<CommandSourceStack> ctx, boolean unlocked) throws CommandSyntaxException {
        Ability ability = ability(ctx);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            PlayerMultiverseData data = AbilityManager.data(player);
            if (unlocked) {
                data.unlock(ability.id());
            } else {
                data.lock(ability.id());
                if (data.isActive(ability.id())) {
                    AbilityManager.deactivate(player, data, ability, DeactivationReason.ADMIN);
                }
            }
            AbilityManager.syncNow(player);
        }
        String key = unlocked ? "command.infinitemultiverse.unlock.one" : "command.infinitemultiverse.lock.one";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, ability.displayName(), targets.size()), true);
        return targets.size();
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Ability ability = ability(ctx);
        int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            AbilityManager.data(player).unlock(ability.id());
            AbilityManager.setLoadoutSlot(player, slot, Optional.of(ability.id()));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.bind", ability.displayName(), slot + 1, targets.size()), true);
        return targets.size();
    }

    private static int activate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Ability ability = ability(ctx);
        boolean success = AbilityManager.tryActivate(player, ability);
        if (success) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.activate", ability.displayName()), false);
        }
        return success ? 1 : 0;
    }

    private static int deactivateAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            AbilityManager.deactivateAll(player, DeactivationReason.ADMIN);
            AbilityManager.syncNow(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.deactivate", targets.size()), true);
        return targets.size();
    }

    private static int setStand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "stand");
        StandType type = MultiverseRegistries.STAND_TYPES.get(id);
        if (type == null) {
            throw UNKNOWN_STAND.create(id);
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(player -> StandManager.awaken(player, type));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.stand.set", type.displayName(), targets.size()), true);
        return targets.size();
    }

    private static int clearStand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(StandManager::forget);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.stand.clear", targets.size()), true);
        return targets.size();
    }

    private static int setPower(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "power");
        com.infinitemultiverse.power.PowerSet set = MultiverseRegistries.POWER_SETS.get(id);
        if (set == null) {
            throw UNKNOWN_STAND.create(id);
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(player -> com.infinitemultiverse.power.PowerManager.grant(player, set));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.power.set", set.displayName(), targets.size()), true);
        return targets.size();
    }

    private static int clearPowers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            for (com.infinitemultiverse.core.MultiverseSystem system : com.infinitemultiverse.core.MultiverseSystem.values()) {
                com.infinitemultiverse.power.PowerManager.forget(player, system);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.power.clear", targets.size()), true);
        return targets.size();
    }

    private static int setMastery(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            for (com.infinitemultiverse.core.MultiverseSystem system : com.infinitemultiverse.core.MultiverseSystem.values()) {
                com.infinitemultiverse.power.PowerSet set = com.infinitemultiverse.power.PowerManager.powerOf(player, system);
                if (set != null) {
                    com.infinitemultiverse.power.mastery.Mastery.setLevel(player, set, level);
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.infinitemultiverse.power.mastery", com.infinitemultiverse.power.mastery.Mastery.roman(level), targets.size()), true);
        return targets.size();
    }
}
