package dev.riftverse.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftverse.temporal.TemporalManager;
import dev.riftverse.temporal.TemporalManager.Timeline;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /multiverse time ... and /multiverse tsa ... */
final class TimeCommands {
    private TimeCommands() {}

    private static int say(CommandSourceStack s, String m) {
        s.sendSuccess(() -> Component.literal(m).withColor(0x7DF9FF), false);
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> time() {
        var travel = Commands.literal("travel").then(Commands.argument("year", IntegerArgumentType.integer(-1_000_000, 1_000_000)).executes(c -> {
            TemporalManager.travel(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "year"), null);
            return 1;
        }));
        for (Timeline line : Timeline.values()) {
            travel.then(Commands.literal(line.name().toLowerCase()).then(Commands.argument("toYear", IntegerArgumentType.integer(-1_000_000, 1_000_000)).executes(c -> {
                TemporalManager.travel(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "toYear"), line);
                return 1;
            })));
        }
        return Commands.literal("time").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return say(c.getSource(), "Coordinate " + TemporalManager.coordinate(p) + " • Paradox " + TemporalManager.paradox(p) + "%");
                }))
                .then(travel)
                .then(Commands.literal("present").executes(c -> {
                    TemporalManager.travel(c.getSource().getPlayerOrException(), TemporalManager.PRESENT, Timeline.PRIME);
                    return 1;
                }))
                .then(Commands.literal("paradox").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    TemporalManager.setParadox(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "percent"));
                    return say(c.getSource(), "Paradox set.");
                })));
    }

    static LiteralArgumentBuilder<CommandSourceStack> tsa() {
        return Commands.literal("tsa").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> say(c.getSource(), "TSA enforcement " + (TemporalManager.tsaEnabled() ? "ACTIVE" : "STOOD DOWN")
                        + " • airspace ceiling Y=" + TemporalManager.altitudeLimit)))
                .then(Commands.literal("enable").executes(c -> {
                    TemporalManager.setTsaEnabled(true);
                    return say(c.getSource(), "TSA enforcement active.");
                }))
                .then(Commands.literal("disable").executes(c -> {
                    TemporalManager.setTsaEnabled(false);
                    return say(c.getSource(), "TSA stood down.");
                }))
                .then(Commands.literal("ceiling").then(Commands.argument("y", IntegerArgumentType.integer(64, 2000)).executes(c -> {
                    TemporalManager.altitudeLimit = IntegerArgumentType.getInteger(c, "y");
                    return say(c.getSource(), "Temporal airspace ceiling set to Y=" + TemporalManager.altitudeLimit + ".");
                })))
                .then(Commands.literal("dispatch").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    TemporalManager.dispatch(EntityArgument.getPlayer(c, "player"), "MANUAL DISPATCH");
                    return 1;
                })))
                .then(Commands.literal("arrest").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    TemporalManager.arrest(EntityArgument.getPlayer(c, "player"), "BY ORDER OF THE AUTHORITY");
                    return 1;
                })))
                .then(Commands.literal("pardon").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    TemporalManager.setParadox(EntityArgument.getPlayer(c, "player"), 0);
                    return say(c.getSource(), "Pardoned.");
                })))
                .then(Commands.literal("recall").executes(c -> say(c.getSource(), "Recalled " + TemporalManager.recallAgents(c.getSource().getServer()) + " agents.")));
    }

    static LiteralArgumentBuilder<CommandSourceStack> wormhole() {
        var open = Commands.literal("open").executes(c -> {
            dev.riftverse.wormhole.WormholeManager.openMouth(c.getSource().getPlayerOrException(), null);
            return 1;
        });
        var enter = Commands.literal("enter").executes(c -> {
            dev.riftverse.wormhole.WormholeManager.enter(c.getSource().getServer(), java.util.List.of(c.getSource().getPlayerOrException()), null);
            return 1;
        });
        for (dev.riftverse.universe.Archetype a : dev.riftverse.universe.Archetype.values()) {
            open.then(Commands.literal(a.id).executes(c -> {
                dev.riftverse.wormhole.WormholeManager.openMouth(c.getSource().getPlayerOrException(), a);
                return 1;
            }));
            enter.then(Commands.literal(a.id).executes(c -> {
                dev.riftverse.wormhole.WormholeManager.enter(c.getSource().getServer(), java.util.List.of(c.getSource().getPlayerOrException()), a);
                return 1;
            }));
        }
        return Commands.literal("wormhole").requires(s -> s.hasPermission(2)).then(open).then(enter)
                .then(Commands.literal("status").executes(c -> say(c.getSource(), dev.riftverse.wormhole.WormholeManager.active() + " wormhole tunnels active.")));
    }
}
