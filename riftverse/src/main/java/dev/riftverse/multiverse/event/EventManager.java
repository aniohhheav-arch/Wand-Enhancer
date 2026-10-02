package dev.riftverse.multiverse.event;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.InfiniteCorridor;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.RealityState;
import dev.riftverse.multiverse.UniverseProfile;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.UniverseId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Runs multiverse events: rolls natural occurrences from configurable rarities and cooldowns, starts events on demand
 * (commands, the Reality Remote), ticks every active instance and cleans up after it. Natural events never start in
 * the Nexus, the Infinite Corridor or a sealed universe; manual starts only refuse the latter two.
 */
public final class EventManager {
    private static final Map<EventType, MultiverseEvent> HANDLERS = EventHandlers.create();
    private static final List<ActiveEvent> ACTIVE = new ArrayList<>();
    private static int nextId = 1;

    private EventManager() {}

    public static List<ActiveEvent> active() {
        return List.copyOf(ACTIVE);
    }

    public static int rarity(MinecraftServer server, EventType type) {
        Integer o = RealityState.get(server).rarityOverride(type.id);
        return o != null ? o : RiftverseConfig.get(RiftverseConfig.EVENT_RARITY.get(type), type.defaultRarity);
    }

    public static int cooldownMinutes(MinecraftServer server, EventType type) {
        Integer o = RealityState.get(server).cooldownOverride(type.id);
        return o != null ? o : RiftverseConfig.get(RiftverseConfig.EVENT_COOLDOWN.get(type), type.defaultCooldownMinutes);
    }

    /** Remaining natural cooldown in ticks. */
    public static long cooldownLeft(MinecraftServer server, EventType type) {
        return Math.max(0, RealityState.get(server).eventReadyAt(type.id) - server.overworld().getGameTime());
    }

    @Nullable
    private static String locationProblem(ServerLevel level, BlockPos pos, boolean natural) {
        if (level.dimension() == RvWorldgen.EXPANSE) {
            UniverseId id = UniverseId.ofBlock(pos.getX(), pos.getZ());
            if (InfiniteCorridor.isCorridor(id)) return "events cannot reach the Infinite Corridor";
            if (!RealityState.isAccessible(id)) return "that universe is sealed";
            return null;
        }
        if (!natural) return null;
        if (level.dimension() == RvWorldgen.NEXUS) return "the Nexus is shielded from natural events";
        if (!RiftverseConfig.get(RiftverseConfig.EVENTS_IN_VANILLA_DIMENSIONS, true)) return "natural events are disabled in vanilla dimensions";
        return null;
    }

    public static RealityOps.Outcome start(EventType type, ServerLevel level, Vec3 center, @Nullable ServerPlayer instigator, boolean natural) {
        String problem = locationProblem(level, BlockPos.containing(center), natural);
        if (problem != null) return RealityOps.Outcome.fail("Cannot start " + type.id + ": " + problem + ".");
        UniverseId universe = level.dimension() == RvWorldgen.EXPANSE ? UniverseId.ofBlock((int) center.x, (int) center.z) : null;
        ActiveEvent e = new ActiveEvent(nextId++, type, level, center, instigator == null ? null : instigator.getUUID(), natural, universe);
        EventDirector.bind(level.getServer());
        e.duration = EventDirector.durationFor(type);
        String fail = HANDLERS.get(type).start(e);
        if (fail != null) {
            EventKit.closeRifts(e);
            EventKit.discardAll(e);
            return RealityOps.Outcome.fail("Cannot start " + type.id + ": " + fail + ".");
        }
        ACTIVE.add(e);
        MinecraftServer server = level.getServer();
        RealityState state = RealityState.get(server);
        state.setEventReadyAt(type.id, level.getGameTime() + cooldownMinutes(server, type) * 1200L);
        if (universe != null) {
            UniverseProfile p = state.profile(universe);
            p.events++;
            p.lastEvent = type.id;
            if (type == EventType.REALITY_COLLAPSE || type == EventType.DIMENSIONAL_ANOMALY || type == EventType.RIFT_STORM) p.stability = Math.max(0f, p.stability - 15f);
        }
        state.record(type.id + " #" + e.id + (natural ? " (natural)" : instigator != null ? " by " + instigator.getGameProfile().getName() : " (command)")
                + " in " + level.dimension().location().getPath());
        announce(e);
        return RealityOps.Outcome.ok("Started " + type.id + " #" + e.id + " at " + (int) center.x + ", " + (int) center.y + ", " + (int) center.z + ".");
    }

    private static CinematicType cinematicFor(EventType t) {
        return switch (t) {
            case REALITY_COLLAPSE -> CinematicType.COLLAPSE;
            case DIMENSIONAL_INVASION -> CinematicType.INVASION;
            case COSMIC_LEVIATHAN -> CinematicType.LEVIATHAN;
            case ANCIENT_GUARDIAN, VOID_WANDERER, COSMIC_DEITY -> CinematicType.GUARDIAN;
            case DIMENSIONAL_MIGRATION -> CinematicType.INVASION;
            case DIMENSIONAL_ANOMALY -> CinematicType.ANOMALY;
            case UNIVERSE_BIRTH -> CinematicType.BIRTH;
            case BLACK_HOLE -> CinematicType.SINGULARITY;
            case RIFT_STORM -> CinematicType.STORM;
            case COSMIC_CONVERGENCE -> CinematicType.CONVERGENCE;
            default -> switch (t.category()) {
                case 1 -> CinematicType.CONVERGENCE;
                case 2 -> CinematicType.STORM;
                case 3 -> CinematicType.TIME_TRAVEL;
                case 4 -> CinematicType.STORM;
                case 5 -> CinematicType.ANOMALY;
                default -> CinematicType.ANOMALY;
            };
        };
    }

    private static void announce(ActiveEvent e) {
        EventKit.burst(e, e.focus, e.type.color, 1.2f);
        if (!e.focus.equals(e.center)) EventKit.burst(e, e.center, e.type.color, 0.7f);
        CinematicType cine = cinematicFor(e.type);
        boolean bossTitle = e.type == EventType.COSMIC_LEVIATHAN || e.type == EventType.ANCIENT_GUARDIAN || e.type == EventType.VOID_WANDERER
                || e.type == EventType.COSMIC_DEITY;
        for (ServerPlayer p : EventKit.playersNear(e, 160)) {
            if (TransitManager.inTransit(p)) continue;
            RealityOps.cinematic(p, cine, e.type == EventType.UNIVERSE_BIRTH ? 380 : 0, e.focus, e.type.color, 0xFFFFFF, bossTitle ? "" : e.type.title, bossTitle ? "" : e.type.subtitle);
            p.getData(RvAttachments.MULTIVERSE.get()).eventsWitnessed++;
            RealityOps.research(p, 25, "witnessed " + e.type.id.replace('_', ' '));
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<ActiveEvent> it = ACTIVE.iterator();
        List<ActiveEvent> ended = new ArrayList<>();
        while (it.hasNext()) {
            ActiveEvent e = it.next();
            try {
                HANDLERS.get(e.type).tick(e);
            } catch (RuntimeException ex) {
                dev.riftverse.Riftverse.LOGGER.error("Event {} failed; ending it", e.describe(), ex);
                e.finished = true;
            }
            e.age++;
            if (e.finished || e.age >= e.duration) {
                it.remove();
                ended.add(e);
            }
        }
        for (ActiveEvent e : ended) {
            HANDLERS.get(e.type).end(e, false);
            EventKit.burst(e, e.center, e.type.color, 0.8f);
            EventDirector.onEnded(e, false);
        }
        rollNatural(server);
    }

    private static void rollNatural(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        int interval = Math.max(5, RiftverseConfig.get(RiftverseConfig.EVENT_CHECK_SECONDS, 60)) * 20;
        if (time % interval != 0) return;
        if (!RiftverseConfig.get(RiftverseConfig.NATURAL_EVENTS, true)) return;
        EventDirector.bind(server);
        if (!EventDirector.naturalEnabled()) return;
        RealityState state = RealityState.get(server);
        long gap = RiftverseConfig.get(RiftverseConfig.EVENT_MIN_GAP_MINUTES, 20) * 1200L;
        if (time - state.lastNaturalEvent < gap) return;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        Collections.shuffle(players);
        for (ServerPlayer p : players) {
            if (p.isSpectator() || TransitManager.inTransit(p)) continue;
            ServerLevel level = p.serverLevel();
            BlockPos pos = p.blockPosition();
            if (locationProblem(level, pos, true) != null) continue;
            List<EventType> types = new ArrayList<>(List.of(EventType.values()));
            Collections.shuffle(types);
            for (EventType t : types) {
                int rarity = rarity(server, t);
                if (rarity <= 0 || state.eventReadyAt(t.id) > time) continue;
                if (!HANDLERS.get(t).canOccurNaturally(level, pos)) continue;
                if (level.random.nextInt(rarity) != 0) continue;
                Vec3 at = p.position().add((level.random.nextDouble() - 0.5) * 24, 0, (level.random.nextDouble() - 0.5) * 24);
                if (start(t, level, at, null, true).ok()) {
                    state.lastNaturalEvent = time;
                    state.touch();
                    return;
                }
            }
        }
    }

    public static void stop(ActiveEvent e) {
        if (ACTIVE.remove(e)) HANDLERS.get(e.type).end(e, true);
    }

    public static int stopType(EventType type) {
        int n = 0;
        for (ActiveEvent e : active()) {
            if (e.type == type) {
                stop(e);
                n++;
            }
        }
        return n;
    }

    public static int stopAll() {
        int n = 0;
        for (ActiveEvent e : active()) {
            stop(e);
            n++;
        }
        return n;
    }

    public static int stopIn(MinecraftServer server, UniverseId universe) {
        int n = 0;
        for (ActiveEvent e : active()) {
            if (universe.equals(e.universe)) {
                stop(e);
                n++;
            }
        }
        return n;
    }

    /** Server shutdown: end everything so no orphaned rifts or bosses survive the restart. */
    public static void shutdown() {
        stopAll();
        ACTIVE.clear();
    }
}
