package dev.riftverse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.PortalEntity;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.CosmicRank;
import dev.riftverse.multiverse.EndProtocol;
import dev.riftverse.multiverse.EndProtocols;
import dev.riftverse.multiverse.InfiniteCorridor;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.RealityOps.Outcome;
import dev.riftverse.multiverse.RealityRewriter;
import dev.riftverse.multiverse.RealitySnapshots;
import dev.riftverse.multiverse.RealityState;
import dev.riftverse.multiverse.RealityStatus;
import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.multiverse.UniverseDna;
import dev.riftverse.multiverse.UniverseProfile;
import dev.riftverse.multiverse.event.ActiveEvent;
import dev.riftverse.multiverse.event.EventManager;
import dev.riftverse.multiverse.event.EventType;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseEntry;
import dev.riftverse.player.PlayerMultiverseData;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.PromptInterpreter;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.world.NexusChunkGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /multiverse — the developer and admin console for the multiverse expansion. Every subcommand performs the real
 * operation (events, cinematics, reality rewrites, protocols, universes, diagnostics). Read-only queries are open to
 * everyone; anything that changes the world needs permission level 2, and destructive operations additionally require
 * /multiverse confirm within 30 seconds.
 */
public final class MultiverseCommand {
    private static final SimpleCommandExceptionType NOT_IN_UNIVERSE = new SimpleCommandExceptionType(
            Component.literal("You are not inside a universe. Stand in the Expanse or name one (here, corridor, an archetype, u<number> or gx.gz)."));
    private static final Map<String, Pending> PENDING = new HashMap<>();

    private record Pending(String label, Supplier<Outcome> action, long expires) {}

    private MultiverseCommand() {}

    // ------------------------------------------------------------------ suggestions

    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(EventType.values()).map(t -> t.id), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS_OR_ALL = (c, b) -> {
        List<String> s = new ArrayList<>();
        s.add("all");
        for (EventType t : EventType.values()) s.add(t.id);
        for (ActiveEvent e : EventManager.active()) s.add("#" + e.id);
        return SharedSuggestionProvider.suggest(s, b);
    };
    private static final SuggestionProvider<CommandSourceStack> CINEMATICS = (c, b) ->
            SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("genesis"),
                    Arrays.stream(CinematicType.values()).map(t -> t.id)), b);
    private static final SuggestionProvider<CommandSourceStack> PROTOCOLS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(EndProtocol.values()).map(p -> p.id), b);
    private static final SuggestionProvider<CommandSourceStack> TRAITS = (c, b) -> SharedSuggestionProvider.suggest(RealityOps.TRAITS, b);
    private static final SuggestionProvider<CommandSourceStack> SNAPSHOTS = (c, b) ->
            SharedSuggestionProvider.suggest(RealitySnapshots.list(c.getSource().getServer()), b);
    private static final SuggestionProvider<CommandSourceStack> UNIVERSES = (c, b) -> {
        List<String> s = new ArrayList<>(List.of("here", "corridor"));
        for (Archetype a : Archetype.values()) s.add(a.id);
        int n = 0;
        for (UniverseSpec spec : UniverseRegistry.get(c.getSource().getServer()).all()) {
            if (n++ > 200) break;
            s.add(token(spec.id));
        }
        return SharedSuggestionProvider.suggest(s, b);
    };
    private static final SuggestionProvider<CommandSourceStack> ARCHETYPES = (c, b) -> {
        List<String> s = new ArrayList<>(List.of("new"));
        for (Archetype a : Archetype.values()) s.add(a.id);
        return SharedSuggestionProvider.suggest(s, b);
    };

    /** Short command token for a universe: u + its designation number. */
    public static String token(UniverseId id) {
        if (InfiniteCorridor.isCorridor(id)) return "corridor";
        return "u" + id.designation().split("-")[1];
    }

    static UniverseId parseUniverse(CommandSourceStack src, String raw) throws CommandSyntaxException {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        MinecraftServer server = src.getServer();
        if (t.equals("here")) {
            ServerPlayer p = src.getPlayerOrException();
            UniverseId id = RealityOps.universeOf(p);
            if (id == null) throw NOT_IN_UNIVERSE.create();
            return id;
        }
        if (t.equals("corridor")) return InfiniteCorridor.ID;
        Archetype a = Archetype.byName(t);
        if (a != null) return UniverseId.prime(a);
        if (t.matches("u\\d{5}")) {
            String num = t.substring(1);
            for (UniverseSpec s : UniverseRegistry.get(server).all()) if (s.id.designation().split("-")[1].equals(num)) return s.id;
            for (UniverseProfile p : RealityState.get(server).profiles()) {
                UniverseId id = UniverseId.unpack(p.id);
                if (id.designation().split("-")[1].equals(num)) return id;
            }
            throw new SimpleCommandExceptionType(Component.literal("No charted universe has designation number " + num + ".")).create();
        }
        String[] parts = t.split("\\.");
        if (parts.length == 2) {
            try {
                int gx = Integer.parseInt(parts[0]);
                int gz = Integer.parseInt(parts[1]);
                if (Math.abs(gx) <= UniverseId.MAX_SLOT * 2 && Math.abs(gz) <= UniverseId.MAX_SLOT * 4) return new UniverseId(gx, gz);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new SimpleCommandExceptionType(Component.literal("Unknown universe '" + raw + "'. Use here, corridor, an archetype id, u<number> or gx.gz.")).create();
    }

    private static UniverseId universeArg(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return parseUniverse(c.getSource(), StringArgumentType.getString(c, "universe"));
    }

    private static UniverseId here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return parseUniverse(c.getSource(), "here");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> universe() {
        return Commands.argument("universe", StringArgumentType.word()).suggests(UNIVERSES);
    }

    // ------------------------------------------------------------------ output

    private static int reply(CommandSourceStack src, Outcome o) {
        String[] lines = o.message().split("\n");
        for (String line : lines) {
            if (o.ok()) src.sendSuccess(() -> Component.literal(line), false);
            else src.sendFailure(Component.literal(line));
        }
        return o.ok() ? 1 : 0;
    }

    private static int lines(CommandSourceStack src, List<String> lines) {
        for (String l : lines) src.sendSuccess(() -> Component.literal(l), false);
        return Math.max(1, lines.size());
    }

    private static int confirmLater(CommandSourceStack src, String label, Supplier<Outcome> action) {
        PENDING.put(src.getTextName(), new Pending(label, action, System.currentTimeMillis() + 30_000));
        src.sendSuccess(() -> Component.literal("⚠ " + label + " is destructive. Run /multiverse confirm within 30 seconds to proceed, or /multiverse cancel.")
                .withColor(0xFFB040), false);
        return 1;
    }

    private static ServerPlayer playerOrNull(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? p : null;
    }

    // ------------------------------------------------------------------ registration

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("multiverse");
        root.executes(c -> help(c.getSource()));
        root.then(Commands.literal("help").executes(c -> help(c.getSource())));
        root.then(Commands.literal("confirm").requires(s -> s.hasPermission(2)).executes(c -> {
            Pending p = PENDING.remove(c.getSource().getTextName());
            if (p == null || p.expires < System.currentTimeMillis()) return reply(c.getSource(), Outcome.fail("Nothing is waiting for confirmation."));
            return reply(c.getSource(), p.action.get());
        }));
        root.then(Commands.literal("cancel").executes(c -> reply(c.getSource(), PENDING.remove(c.getSource().getTextName()) != null
                ? Outcome.ok("Cancelled.") : Outcome.fail("Nothing to cancel."))));
        root.then(eventTree());
        root.then(cinematicTree());
        root.then(realityTree());
        root.then(universeTree());
        root.then(Commands.literal("profile").executes(c -> profile(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> profile(c.getSource(), EntityArgument.getPlayer(c, "player")))));
        root.then(Commands.literal("hub").requires(s -> s.hasPermission(2))
                .then(Commands.literal("regenerate").executes(c -> {
                    ServerLevel nexus = c.getSource().getServer().getLevel(RvWorldgen.NEXUS);
                    if (nexus == null) return reply(c.getSource(), Outcome.fail("The Nexus is not loaded."));
                    return reply(c.getSource(), Outcome.ok(NexusChunkGenerator.regenerateExpansion(nexus)));
                })));
        root.then(entitiesTree());
        root.then(WeaponCommands.weapon());
        root.then(TimeCommands.time());
        root.then(TimeCommands.tsa());
        root.then(TimeCommands.wormhole());
        root.then(TimeCommands.fusion());
        root.then(WeaponCommands.rupture());
        root.then(debugTree());
        d.register(root);
    }

    private static int help(CommandSourceStack src) {
        return lines(src, List.of(
                "§6/multiverse§r — multiverse console",
                " event list|start|summon|stop|info|cooldown|rarity|history",
                " cinematic list|play|stop",
                " reality scan|inspect|erase|rebuild|restore|stabilize|modify|snapshot|protocols|protocol",
                " universe list|info|create|visit|archive|restore|dna",
                " profile [player] • hub regenerate • confirm • cancel",
                " debug events|dimensions|portals|reality|performance",
                " Universes: here, corridor, <archetype>, u<number>, or gx.gz"));
    }

    // ------------------------------------------------------------------ events

    private static EventType eventArg(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "event");
        EventType t = EventType.byId(raw);
        if (t == null) throw new SimpleCommandExceptionType(Component.literal("Unknown event '" + raw + "'. Try /multiverse event list.")).create();
        return t;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventTree() {
        var intensity = Commands.literal("intensity").executes(c -> {
            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
            return reply(c.getSource(), Outcome.ok("Event intensity: " + dev.riftverse.multiverse.event.EventDirector.intensity().name()));
        });
        for (var lvl : dev.riftverse.multiverse.event.EventDirector.Intensity.values()) {
            intensity.then(Commands.literal(lvl.name().toLowerCase()).requires(s -> s.hasPermission(2)).executes(c -> {
                dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                dev.riftverse.multiverse.event.EventDirector.setIntensity(lvl);
                return reply(c.getSource(), Outcome.ok("Event intensity set to " + lvl.name() + " (x" + lvl.scale + ")."));
            }));
        }
        return Commands.literal("event")
                .then(intensity)
                .then(Commands.literal("center").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new dev.riftverse.network.Payloads.Vehicle(
                            dev.riftverse.multiverse.event.EventDirector.intensity().ordinal(), dev.riftverse.network.Payloads.Vehicle.OPEN_EVENTS,
                            (dev.riftverse.multiverse.event.EventDirector.chains() ? 1 : 0) | (dev.riftverse.multiverse.event.EventDirector.naturalEnabled() ? 2 : 0)));
                    return 1;
                }))
                .then(Commands.literal("duration").requires(s -> s.hasPermission(2)).then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                        .then(Commands.literal("default").executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setDuration(eventArg(c), -1);
                            return reply(c.getSource(), Outcome.ok("Duration reset."));
                        }))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 7200)).executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setDuration(eventArg(c), IntegerArgumentType.getInteger(c, "seconds"));
                            return reply(c.getSource(), Outcome.ok("Duration of " + eventArg(c).id + " set to " + IntegerArgumentType.getInteger(c, "seconds") + "s."));
                        }))))
                .then(Commands.literal("chain").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("on").executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setChains(true);
                            return reply(c.getSource(), Outcome.ok("Event chains enabled (max " + dev.riftverse.multiverse.event.EventDirector.MAX_CHAIN + " links, no repeats)."));
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setChains(false);
                            return reply(c.getSource(), Outcome.ok("Event chains disabled."));
                        })))
                .then(Commands.literal("natural").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("enable").executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setNatural(true);
                            return reply(c.getSource(), Outcome.ok("Natural events enabled."));
                        }))
                        .then(Commands.literal("disable").executes(c -> {
                            dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                            dev.riftverse.multiverse.event.EventDirector.setNatural(false);
                            return reply(c.getSource(), Outcome.ok("Natural events disabled."));
                        })))
                .then(Commands.literal("active").executes(c -> {
                    List<String> out = new ArrayList<>();
                    out.add("§6Active events:§r " + EventManager.active().size());
                    for (ActiveEvent e : EventManager.active()) out.add(" " + e.describe());
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("preview").requires(s -> s.hasPermission(2)).then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS).executes(c -> {
                    EventType t = eventArg(c);
                    dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                    var out = startAt(c.getSource(), t, c.getSource().getLevel(), c.getSource().getPosition(), playerOrNull(c.getSource()));
                    for (ActiveEvent e : EventManager.active()) if (e.type == t && e.age == 0) e.duration = Math.min(e.duration, 200);
                    return out;
                })))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    dev.riftverse.multiverse.event.EventDirector.bind(c.getSource().getServer());
                    dev.riftverse.multiverse.event.EventDirector.reset();
                    int n = EventManager.stopAll();
                    return reply(c.getSource(), Outcome.ok("Event director reset to defaults; stopped " + n + " event(s)."));
                }))
                .then(Commands.literal("list").executes(c -> {
                    List<String> out = new ArrayList<>();
                    MinecraftServer server = c.getSource().getServer();
                    out.add("§6Multiverse events§r (" + EventManager.active().size() + " active):");
                    for (EventType t : EventType.values()) {
                        long cd = EventManager.cooldownLeft(server, t);
                        int active = (int) EventManager.active().stream().filter(e -> e.type == t).count();
                        out.add(" " + t.id + " — rarity 1/" + EventManager.rarity(server, t) + (cd > 0 ? " • cooldown " + cd / 1200 + "m" : " • ready")
                                + (active > 0 ? " • §a" + active + " active§r" : ""));
                    }
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("history").executes(c -> {
                    List<String> h = RealityState.get(c.getSource().getServer()).history();
                    List<String> out = new ArrayList<>();
                    out.add("§6Recent multiverse history:");
                    for (int i = Math.max(0, h.size() - 15); i < h.size(); i++) out.add(" " + h.get(i));
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                                .executes(c -> startAt(c.getSource(), eventArg(c), c.getSource().getLevel(), c.getSource().getPosition(), playerOrNull(c.getSource())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> startFor(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("summon").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                                .executes(c -> startFor(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> startFor(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("all").executes(c -> reply(c.getSource(), Outcome.ok("Stopped " + EventManager.stopAll() + " event(s)."))))
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS_OR_ALL).executes(c -> {
                            String raw = StringArgumentType.getString(c, "event");
                            if (raw.startsWith("#")) {
                                for (ActiveEvent e : EventManager.active()) {
                                    if (("#" + e.id).equals(raw)) {
                                        EventManager.stop(e);
                                        return reply(c.getSource(), Outcome.ok("Stopped event " + raw + "."));
                                    }
                                }
                                return reply(c.getSource(), Outcome.fail("No active event " + raw + "."));
                            }
                            int n = EventManager.stopType(eventArg(c));
                            return reply(c.getSource(), n > 0 ? Outcome.ok("Stopped " + n + " " + raw + " event(s).") : Outcome.fail("No " + raw + " event is active."));
                        })))
                .then(Commands.literal("info").then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS).executes(c -> {
                    EventType t = eventArg(c);
                    MinecraftServer server = c.getSource().getServer();
                    List<String> out = new ArrayList<>();
                    out.add("§6" + t.title + "§r (" + t.id + ")");
                    out.add(" " + t.description);
                    out.add(" Duration " + t.durationTicks / 20 + "s • natural rarity 1/" + EventManager.rarity(server, t) + " per check • cooldown "
                            + EventManager.cooldownMinutes(server, t) + "m (" + EventManager.cooldownLeft(server, t) / 1200 + "m left)");
                    for (ActiveEvent e : EventManager.active()) if (e.type == t) out.add(" Active: " + e.describe());
                    return lines(c.getSource(), out);
                })))
                .then(Commands.literal("cooldown").then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                        .executes(c -> {
                            EventType t = eventArg(c);
                            MinecraftServer server = c.getSource().getServer();
                            return reply(c.getSource(), Outcome.ok(t.id + ": cooldown " + EventManager.cooldownMinutes(server, t) + " minutes, "
                                    + EventManager.cooldownLeft(server, t) / 20 + "s remaining."));
                        })
                        .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                            EventType t = eventArg(c);
                            RealityState.get(c.getSource().getServer()).setEventReadyAt(t.id, 0);
                            return reply(c.getSource(), Outcome.ok(t.id + " is ready to occur again."));
                        }))
                        .then(Commands.literal("default").requires(s -> s.hasPermission(2)).executes(c -> {
                            EventType t = eventArg(c);
                            RealityState.get(c.getSource().getServer()).setCooldownOverride(t.id, null);
                            return reply(c.getSource(), Outcome.ok(t.id + " cooldown reverts to the config value."));
                        }))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100000)).requires(s -> s.hasPermission(2)).executes(c -> {
                            EventType t = eventArg(c);
                            int m = IntegerArgumentType.getInteger(c, "minutes");
                            RealityState.get(c.getSource().getServer()).setCooldownOverride(t.id, m);
                            return reply(c.getSource(), Outcome.ok(t.id + " cooldown set to " + m + " minutes (saved with the world)."));
                        }))))
                .then(Commands.literal("rarity").then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                        .executes(c -> {
                            EventType t = eventArg(c);
                            return reply(c.getSource(), Outcome.ok(t.id + ": natural rarity 1/" + EventManager.rarity(c.getSource().getServer(), t) + " per eligible check."));
                        })
                        .then(Commands.literal("default").requires(s -> s.hasPermission(2)).executes(c -> {
                            EventType t = eventArg(c);
                            RealityState.get(c.getSource().getServer()).setRarityOverride(t.id, null);
                            return reply(c.getSource(), Outcome.ok(t.id + " rarity reverts to the config value."));
                        }))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 1_000_000)).requires(s -> s.hasPermission(2)).executes(c -> {
                            EventType t = eventArg(c);
                            int v = IntegerArgumentType.getInteger(c, "value");
                            RealityState.get(c.getSource().getServer()).setRarityOverride(t.id, v);
                            return reply(c.getSource(), Outcome.ok(t.id + " rarity set to " + (v == 0 ? "never (natural occurrences disabled)" : "1/" + v) + "."));
                        }))));
    }

    private static int startAt(CommandSourceStack src, EventType t, ServerLevel level, Vec3 pos, ServerPlayer instigator) {
        return reply(src, EventManager.start(t, level, pos, instigator, false));
    }

    private static int startFor(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        EventType t = eventArg(c);
        int ok = 0;
        for (ServerPlayer p : targets) ok += startAt(c.getSource(), t, p.serverLevel(), p.position(), p);
        return ok;
    }

    // ------------------------------------------------------------------ cinematics

    private static LiteralArgumentBuilder<CommandSourceStack> cinematicTree() {
        return Commands.literal("cinematic")
                .then(Commands.literal("list").executes(c -> {
                    List<String> out = new ArrayList<>();
                    out.add("§6Cinematics:§r");
                    for (CinematicType t : CinematicType.values()) out.add(" " + t.id + " (" + t.defaultTicks / 20 + "s" + (t.camera ? ", camera" : "") + ")");
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("play").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(CINEMATICS)
                                .executes(c -> play(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> play(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2))
                        .executes(c -> stopCine(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> stopCine(c.getSource(), EntityArgument.getPlayers(c, "targets")))));
    }

    private static int play(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "name");
        if (raw.equalsIgnoreCase("genesis")) {
            // the Genesis Protocol is not a mock-up: it really births a universe and carries the targets into it
            var spec = dev.riftverse.multiverse.GenesisManager.begin(c.getSource().getServer(), new ArrayList<>(targets));
            return reply(c.getSource(), spec == null ? Outcome.fail("Those players are already witnessing a genesis.")
                    : Outcome.ok("Genesis Protocol: " + spec.name + " [" + spec.id.designation() + "] is being born for " + targets.size() + " player(s)."));
        }
        CinematicType t = CinematicType.byId(raw);
        if (t == null) return reply(c.getSource(), Outcome.fail("Unknown cinematic '" + raw + "'. Try /multiverse cinematic list."));
        int[] colors = switch (t) {
            case ERASURE, COLLAPSE -> new int[] {0xFF3A5A, 0x2A0010};
            case REBUILD, RESTORE -> new int[] {0x7DF9FF, 0xFFF0C8};
            case BIRTH -> new int[] {0xFFF0C8, 0xFF7AF0};
            case CONVERGENCE -> new int[] {0xFF7AF0, 0x7DF9FF};
            case SINGULARITY -> new int[] {0xFF8A3A, 0x8F6BFF};
            default -> {
                EndProtocol p = Arrays.stream(EndProtocol.values()).filter(x -> x.cinematic == t).findFirst().orElse(null);
                yield p != null ? new int[] {p.colorA, p.colorB} : new int[] {0x8F6BFF, 0xFFFFFF};
            }
        };
        for (ServerPlayer p : targets) {
            Vec3 focus = p.getEyePosition().add(p.getLookAngle().scale(8));
            RealityOps.cinematic(p, t, 0, focus, colors[0], colors[1], t.id.replace('_', ' ').toUpperCase(Locale.ROOT), "Cinematic preview");
        }
        return reply(c.getSource(), Outcome.ok("Playing " + t.id + " for " + targets.size() + " player(s)."));
    }

    private static int stopCine(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer p : targets) RealityOps.stopCinematic(p);
        return reply(src, Outcome.ok("Stopped cinematics for " + targets.size() + " player(s)."));
    }

    // ------------------------------------------------------------------ reality

    private static LiteralArgumentBuilder<CommandSourceStack> realityTree() {
        return Commands.literal("reality")
                .then(Commands.literal("scan").executes(c -> reply(c.getSource(), RealityOps.scan(c.getSource().getPlayerOrException(), here(c)))))
                .then(Commands.literal("inspect")
                        .executes(c -> lines(c.getSource(), RealityOps.profileLines(c.getSource().getServer(), here(c))))
                        .then(universe().executes(c -> lines(c.getSource(), RealityOps.profileLines(c.getSource().getServer(), universeArg(c))))))
                .then(Commands.literal("erase").requires(s -> s.hasPermission(2))
                        .executes(c -> erase(c, here(c)))
                        .then(universe().executes(c -> erase(c, universeArg(c)))))
                .then(Commands.literal("rebuild").requires(s -> s.hasPermission(2))
                        .executes(c -> rebuild(c, here(c), null))
                        .then(universe().executes(c -> rebuild(c, universeArg(c), null))
                                .then(Commands.argument("into", StringArgumentType.word()).suggests(ARCHETYPES)
                                        .executes(c -> rebuild(c, universeArg(c), StringArgumentType.getString(c, "into"))))))
                .then(Commands.literal("restore").requires(s -> s.hasPermission(2))
                        .executes(c -> restore(c, here(c)))
                        .then(universe().executes(c -> restore(c, universeArg(c)))))
                .then(Commands.literal("stabilize").requires(s -> s.hasPermission(2))
                        .executes(c -> reply(c.getSource(), RealityOps.stabilize(c.getSource().getServer(), here(c))))
                        .then(universe().executes(c -> reply(c.getSource(), RealityOps.stabilize(c.getSource().getServer(), universeArg(c))))))
                .then(Commands.literal("modify").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("trait", StringArgumentType.word()).suggests(TRAITS)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> reply(c.getSource(), RealityOps.modify(
                                        c.getSource().getServer(), here(c), StringArgumentType.getString(c, "trait"), StringArgumentType.getString(c, "value")))))))
                .then(Commands.literal("snapshot").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(c -> {
                            List<String> names = RealitySnapshots.list(c.getSource().getServer());
                            return reply(c.getSource(), Outcome.ok(names.isEmpty() ? "No snapshots saved." : "Snapshots: " + String.join(", ", names)));
                        }))
                        .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word()).executes(c -> reply(c.getSource(),
                                RealitySnapshots.save(c.getSource().getLevel(), net.minecraft.core.BlockPos.containing(c.getSource().getPosition()),
                                        StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("load").then(Commands.argument("name", StringArgumentType.word()).suggests(SNAPSHOTS).executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            MinecraftServer server = c.getSource().getServer();
                            return confirmLater(c.getSource(), "Restoring snapshot '" + name + "' (overwrites the area)", () -> RealitySnapshots.load(server, name));
                        })))
                        .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(SNAPSHOTS).executes(c ->
                                reply(c.getSource(), RealitySnapshots.delete(c.getSource().getServer(), StringArgumentType.getString(c, "name")))))))
                .then(Commands.literal("protocols").executes(c -> {
                    List<String> out = new ArrayList<>();
                    out.add("§6End Protocols§r — /multiverse reality protocol <id> [universe] [reconstruct|permanent] | <id> world");
                    for (EndProtocol p : EndProtocol.values()) out.add(" " + p.id + " — " + p.title + " (" + EndProtocols.durationTicks(p) / 20 + "s): " + p.subtitle);
                    for (EndProtocols.Run r : EndProtocols.runs()) out.add(" §cRunning§r " + r.describe());
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("protocol").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("stop")
                                .executes(c -> reply(c.getSource(), EndProtocols.stop(c.getSource().getServer(), null, null)))
                                .then(universe().executes(c -> reply(c.getSource(), EndProtocols.stop(c.getSource().getServer(), universeArg(c), null)))))
                        .then(Commands.literal("preview").then(Commands.argument("protocol", StringArgumentType.word()).suggests(PROTOCOLS)
                                .executes(c -> reply(c.getSource(), EndProtocols.preview(c.getSource().getPlayerOrException(), protocolArg(c))))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) n += reply(c.getSource(), EndProtocols.preview(p, protocolArg(c)));
                                    return n;
                                }))))
                        .then(Commands.argument("protocol", StringArgumentType.word()).suggests(PROTOCOLS)
                                .executes(c -> protocol(c, here(c), false, false))
                                .then(Commands.literal("world").executes(MultiverseCommand::protocolWorld))
                                .then(universe().executes(c -> protocol(c, universeArg(c), false, false))
                                        .then(Commands.literal("reconstruct").executes(c -> protocol(c, universeArg(c), true, false)))
                                        .then(Commands.literal("permanent").executes(c -> protocol(c, universeArg(c), false, true))))));
    }

    private static EndProtocol protocolArg(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "protocol");
        EndProtocol p = EndProtocol.byId(raw);
        if (p == null) throw new SimpleCommandExceptionType(Component.literal("Unknown protocol '" + raw + "'. See /multiverse reality protocols.")).create();
        return p;
    }

    private static int protocol(CommandContext<CommandSourceStack> c, UniverseId id, boolean reconstruct, boolean permanent) throws CommandSyntaxException {
        EndProtocol p = protocolArg(c);
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer actor = playerOrNull(c.getSource());
        return confirmLater(c.getSource(), p.title + " on " + id.designation() + (reconstruct ? " (then reconstruct)" : permanent ? " (PERMANENT)" : ""),
                () -> EndProtocols.execute(server, id, p, actor, reconstruct, permanent));
    }

    /** Ends the whole dimension the executor stands in (Overworld, Nether, End, modded) — permanently. */
    private static int protocolWorld(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        EndProtocol p = protocolArg(c);
        ServerLevel level = c.getSource().getLevel();
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer actor = playerOrNull(c.getSource());
        if (level.dimension() == RvWorldgen.EXPANSE) return reply(c.getSource(), Outcome.fail("In the Expanse, end a universe: /multiverse reality protocol " + p.id + " here permanent"));
        return confirmLater(c.getSource(), p.title + " on " + RealityOps.worldName(level) + " — FOREVER",
                () -> EndProtocols.executeDimension(server, level, p, actor));
    }

    private static int erase(CommandContext<CommandSourceStack> c, UniverseId id) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer actor = playerOrNull(c.getSource());
        return confirmLater(c.getSource(), "Erasing universe " + id.designation(), () -> RealityOps.erase(server, id, actor));
    }

    private static int rebuild(CommandContext<CommandSourceStack> c, UniverseId id, String into) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer actor = playerOrNull(c.getSource());
        boolean mutate = "new".equalsIgnoreCase(into);
        Archetype a = into == null || mutate ? null : Archetype.byName(into);
        if (into != null && !mutate && a == null) return reply(c.getSource(), Outcome.fail("Unknown archetype '" + into + "'. Use new or an archetype id."));
        String label = "Rebuilding universe " + id.designation() + (mutate ? " into a brand-new reality" : a != null ? " as " + a.displayName : "");
        return confirmLater(c.getSource(), label, () -> RealityOps.rebuild(server, id, mutate, a, actor));
    }

    private static int restore(CommandContext<CommandSourceStack> c, UniverseId id) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer actor = playerOrNull(c.getSource());
        if (RealityState.statusOf(id) == RealityStatus.ARCHIVED) return reply(c.getSource(), RealityOps.restore(server, id, actor));
        return confirmLater(c.getSource(), "Restoring universe " + id.designation() + " from its last backup (rewrites terrain)",
                () -> RealityOps.restore(server, id, actor));
    }

    // ------------------------------------------------------------------ universes

    private static LiteralArgumentBuilder<CommandSourceStack> universeTree() {
        return Commands.literal("universe")
                .then(Commands.literal("list").executes(c -> list(c.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> list(c.getSource(), IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("info")
                        .executes(c -> lines(c.getSource(), RealityOps.profileLines(c.getSource().getServer(), here(c))))
                        .then(universe().executes(c -> lines(c.getSource(), RealityOps.profileLines(c.getSource().getServer(), universeArg(c))))))
                .then(Commands.literal("dna")
                        .executes(c -> reply(c.getSource(), Outcome.ok("DNA: " + UniverseDna.encode(RealityOps.spec(c.getSource().getServer(), here(c))))))
                        .then(universe().executes(c -> reply(c.getSource(), Outcome.ok("DNA: " + UniverseDna.encode(RealityOps.spec(c.getSource().getServer(), universeArg(c))))))))
                .then(Commands.literal("create").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("description", StringArgumentType.greedyString()).executes(MultiverseCommand::create)))
                .then(Commands.literal("visit").requires(s -> s.hasPermission(2))
                        .then(universe().executes(c -> reply(c.getSource(), RealityOps.visit(c.getSource().getPlayerOrException(), universeArg(c))))))
                .then(Commands.literal("archive").requires(s -> s.hasPermission(2))
                        .then(universe().executes(c -> reply(c.getSource(), RealityOps.archive(c.getSource().getServer(), universeArg(c))))))
                .then(Commands.literal("restore").requires(s -> s.hasPermission(2))
                        .then(universe().executes(c -> restore(c, universeArg(c)))));
    }

    private static int list(CommandSourceStack src, int page) {
        MinecraftServer server = src.getServer();
        List<UniverseSpec> all = new ArrayList<>(UniverseRegistry.get(server).all());
        all.sort((a, b) -> Long.compare(a.id.pack(), b.id.pack()));
        int per = 10;
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int p = Math.min(page, pages);
        List<String> out = new ArrayList<>();
        out.add("§6Charted universes§r (" + all.size() + ") — page " + p + "/" + pages);
        for (int i = (p - 1) * per; i < Math.min(all.size(), p * per); i++) {
            UniverseSpec s = all.get(i);
            RealityStatus st = RealityState.statusOf(s.id);
            out.add(" " + token(s.id) + "  " + s.name + " — " + s.archetype.displayName + (st == RealityStatus.ACTIVE ? "" : " §c[" + st + "]§r"));
        }
        return lines(src, out);
    }

    private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String text = StringArgumentType.getString(c, "description").trim();
        UniverseRegistry reg = UniverseRegistry.get(player.server);
        UniverseSpec spec;
        List<String> notes = new ArrayList<>();
        if (text.toLowerCase(Locale.ROOT).startsWith("dna:")) {
            String code = text.substring(4).trim();
            if (UniverseDna.decode(code, new UniverseId(0, -1)) == null) return reply(c.getSource(), Outcome.fail("That is not valid Universe DNA (expected RV-..-.-.-.-..-.-.-.-......)."));
            spec = reg.manifestCustom(id -> {
                UniverseSpec s = UniverseDna.decode(code, id);
                s.prompt = "DNA " + code;
                return s;
            }, player.getUUID());
            notes.add("Grown from Universe DNA " + code);
        } else {
            PromptInterpreter.Result result = reg.manifest(text, player.getUUID());
            spec = result.spec();
            notes.addAll(result.notes());
        }
        c.getSource().sendSuccess(() -> Component.literal("Reality Architect: manifested " + spec.name + " [" + spec.id.designation() + "] (" + token(spec.id) + ")"), false);
        for (String n : notes) c.getSource().sendSuccess(() -> Component.literal("  • " + n), false);
        PacketDistributor.sendToPlayer(player, new Payloads.ManifestResult(UniverseEntry.of(spec, UniverseEntry.MANIFESTED), notes));
        TransitManager.begin(player, TransitKind.CONSOLE, Destination.universe(spec.id), player.getEyePosition().add(player.getLookAngle().scale(3)), spec.accent, spec.nebulaB, true);
        return 1;
    }

    private static int profile(CommandSourceStack src, ServerPlayer player) {
        PlayerMultiverseData d = player.getData(RvAttachments.MULTIVERSE.get());
        CosmicRank rank = d.rank();
        CosmicRank next = rank.next();
        return lines(src, List.of(
                "§6Multiverse profile: " + player.getGameProfile().getName(),
                " Cosmic rank: " + rank.title + " (" + d.research + " research" + (next != rank ? ", " + (next.points - d.research) + " to " + next.title : ", maximum") + ")",
                " Universes discovered " + d.discoveredCount() + " • scanned " + d.scannedCount() + " • journeys " + d.journeys,
                " Events witnessed " + d.eventsWitnessed + " • realities erased " + d.realitiesErased + " • rebuilt " + d.realitiesRebuilt));
    }

    // ------------------------------------------------------------------ entity migration

    private static LiteralArgumentBuilder<CommandSourceStack> entitiesTree() {
        return Commands.literal("entities").then(Commands.literal("migration")
                .then(Commands.literal("status").executes(c -> {
                    RealityState st = RealityState.get(c.getSource().getServer());
                    int migrants = 0;
                    for (ServerLevel l : c.getSource().getServer().getAllLevels()) {
                        for (Entity e : l.getAllEntities()) {
                            if (e.getPersistentData().contains(dev.riftverse.multiverse.MigrationManager.COOLDOWN_TAG)) migrants++;
                        }
                    }
                    return reply(c.getSource(), Outcome.ok("Entity migration is " + (st.migrationEnabled ? "ENABLED" : "DISABLED")
                            + " (config entityMigration=" + dev.riftverse.RiftverseConfig.get(dev.riftverse.RiftverseConfig.ENTITY_MIGRATION, true)
                            + ", rift seeking " + dev.riftverse.RiftverseConfig.get(dev.riftverse.RiftverseConfig.RIFT_SEEKING_CHANCE, 15) + "‰). "
                            + migrants + " loaded creatures have crossed between worlds."));
                }))
                .then(Commands.literal("enable").requires(s -> s.hasPermission(2)).executes(c -> {
                    RealityState st = RealityState.get(c.getSource().getServer());
                    st.migrationEnabled = true;
                    st.touch();
                    return reply(c.getSource(), Outcome.ok("Creatures may now travel through rifts and black holes."));
                }))
                .then(Commands.literal("disable").requires(s -> s.hasPermission(2)).executes(c -> {
                    RealityState st = RealityState.get(c.getSource().getServer());
                    st.migrationEnabled = false;
                    st.touch();
                    return reply(c.getSource(), Outcome.ok("Entity migration disabled: rifts and black holes no longer carry creatures."));
                }))
                .then(Commands.literal("test").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerLevel level = c.getSource().getLevel();
                    Vec3 at = c.getSource().getPosition();
                    net.minecraft.world.entity.Mob mob = null;
                    double bd = 32 * 32;
                    for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(at, at).inflate(32))) {
                        double d = m.distanceToSqr(at);
                        if (d < bd) {
                            bd = d;
                            mob = m;
                        }
                    }
                    if (mob == null) return reply(c.getSource(), Outcome.fail("No creature within 32 blocks to send."));
                    mob.getPersistentData().remove(dev.riftverse.multiverse.MigrationManager.COOLDOWN_TAG);
                    String name = mob.getName().getString();
                    Entity arrived = dev.riftverse.multiverse.MigrationManager.migrate(mob, Destination.random(), 0x5CFF9D);
                    if (arrived == null) return reply(c.getSource(), Outcome.fail(name + " could not be sent (no valid destination)."));
                    var spec = arrived.level().dimension() == RvWorldgen.EXPANSE ? UniverseRegistry.specAt(arrived.getBlockX(), arrived.getBlockZ()) : null;
                    return reply(c.getSource(), Outcome.ok(name + " crossed into " + (spec != null ? spec.name + " [" + spec.id.designation() + "]"
                            : arrived.level().dimension().location().toString()) + " at " + arrived.blockPosition().toShortString() + "."));
                })));
    }

    // ------------------------------------------------------------------ debug

    private static LiteralArgumentBuilder<CommandSourceStack> debugTree() {
        return Commands.literal("debug").requires(s -> s.hasPermission(2))
                .then(Commands.literal("events").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    RealityState st = RealityState.get(server);
                    long now = server.overworld().getGameTime();
                    List<String> out = new ArrayList<>();
                    out.add("§6Events:§r " + EventManager.active().size() + " active; last natural " + (st.lastNaturalEvent < 0 ? "never" : (now - st.lastNaturalEvent) / 1200 + "m ago"));
                    for (ActiveEvent e : EventManager.active()) out.add(" " + e.describe() + " • " + e.entities.size() + " entities, " + e.rifts.size() + " rifts");
                    for (EventType t : EventType.values()) {
                        long cd = EventManager.cooldownLeft(server, t);
                        if (cd > 0) out.add(" cooldown " + t.id + ": " + cd / 20 + "s");
                    }
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("dimensions").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    List<String> out = new ArrayList<>();
                    out.add("§6Dimensions:");
                    for (ServerLevel l : server.getAllLevels()) {
                        out.add(" " + l.dimension().location() + " — " + l.players().size() + " players, " + l.getChunkSource().getLoadedChunksCount() + " chunks loaded");
                    }
                    RealityState st = RealityState.get(server);
                    int[] counts = new int[RealityStatus.values().length];
                    for (UniverseProfile p : st.profiles()) counts[p.status.ordinal()]++;
                    out.add(" Universes stored " + UniverseRegistry.get(server).all().size() + " • profiled " + st.profiles().size() + " (active " + counts[0]
                            + ", archived " + counts[1] + ", erased " + counts[2] + ") • backups " + st.backupCount());
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("portals").executes(c -> {
                    ServerLevel level = c.getSource().getLevel();
                    ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
                    int rifts = 0;
                    int fields = 0;
                    for (int dx = -8; dx <= 8; dx++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            LevelChunk ch = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                            if (ch == null) continue;
                            for (BlockEntity be : ch.getBlockEntities().values()) {
                                if (be instanceof RiftBlockEntity) rifts++;
                                else if (be instanceof PortalFieldBlockEntity) fields++;
                            }
                        }
                    }
                    int portals = 0;
                    int holes = 0;
                    for (Entity e : level.getAllEntities()) {
                        if (e instanceof PortalEntity) portals++;
                        else if (e instanceof BlackHoleEntity) holes++;
                    }
                    int travelling = 0;
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) if (TransitManager.inTransit(p)) travelling++;
                    return reply(c.getSource(), Outcome.ok("Within 8 chunks: " + rifts + " rift blocks, " + fields + " portal-field blocks. In this dimension: " + portals
                            + " portals, " + holes + " black holes. Players in transit: " + travelling + "."));
                }))
                .then(Commands.literal("reality").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    List<String> out = new ArrayList<>();
                    out.add("§6Reality engine:§r " + RealityRewriter.jobs().size() + " rewrite jobs, " + EndProtocols.runs().size() + " protocols, "
                            + Scheduler.pending() + " scheduled tasks");
                    for (RealityRewriter.Job j : RealityRewriter.jobs()) out.add(" job #" + j.id + " " + j.mode + " " + j.universe.designation() + " " + j.done() + "/" + j.total() + " chunks");
                    for (EndProtocols.Run r : EndProtocols.runs()) out.add(" " + r.describe());
                    List<String> h = RealityState.get(server).history();
                    for (int i = Math.max(0, h.size() - 6); i < h.size(); i++) out.add(" › " + h.get(i));
                    return lines(c.getSource(), out);
                }))
                .then(Commands.literal("performance").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
                    Runtime rt = Runtime.getRuntime();
                    long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
                    int entities = 0;
                    ServerLevel expanse = server.getLevel(RvWorldgen.EXPANSE);
                    if (expanse != null) for (Entity ignored : expanse.getAllEntities()) entities++;
                    return reply(c.getSource(), Outcome.ok(String.format(Locale.ROOT,
                            "%.2f ms/tick (≈%.1f TPS) • memory %d/%d MB • Expanse entities %d • rewrite jobs %d • active events %d • protocols %d",
                            mspt, Math.min(20.0, 1000.0 / Math.max(0.001, mspt)), used, rt.maxMemory() >> 20, entities, RealityRewriter.jobs().size(),
                            EventManager.active().size(), EndProtocols.runs().size())));
                }));
    }
}
