package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.event.CommonEvents;
import dev.riftverse.multiverse.event.EventManager;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseSync;
import dev.riftverse.player.PlayerMultiverseData;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.SpecFactory;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TimeMode;
import dev.riftverse.universe.UniverseTraits.WeatherKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Every reality-manipulation operation, shared by the /multiverse command tree and the Reality Remote. Destructive
 * operations always back up the universe definition first, mark the universe's status before touching terrain (so
 * world generation and travel agree with what is happening), then choreograph a cinematic with the actual rewrite.
 */
public final class RealityOps {
    public record Outcome(boolean ok, String message) {
        public static Outcome ok(String m) {
            return new Outcome(true, m);
        }

        public static Outcome fail(String m) {
            return new Outcome(false, m);
        }
    }

    /** Universes in the middle of an erasure cinematic: their occupants are evacuated on cue, not by the watchdog. */
    private static final java.util.Set<UniverseId> ERASING = new java.util.HashSet<>();

    private RealityOps() {}

    // ------------------------------------------------------------------ queries

    @Nullable
    public static UniverseId universeOf(ServerPlayer player) {
        if (player.level().dimension() != RvWorldgen.EXPANSE) return null;
        return UniverseId.ofBlock(player.getBlockX(), player.getBlockZ());
    }

    public static List<ServerPlayer> playersIn(MinecraftServer server, UniverseId id) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (id.equals(universeOf(p))) out.add(p);
        }
        return out;
    }

    public static UniverseSpec spec(MinecraftServer server, UniverseId id) {
        UniverseSpec s = UniverseRegistry.specFor(id);
        if (!InfiniteCorridor.isCorridor(id)) UniverseRegistry.get(server).remember(s);
        return s;
    }

    public static String statusLine(MinecraftServer server, UniverseId id) {
        UniverseProfile p = RealityState.get(server).profile(id);
        return p.status + " • stability " + Math.round(p.stability) + "% (" + p.stabilityLabel() + ")";
    }

    /** A full research readout for a universe. */
    public static List<String> profileLines(MinecraftServer server, UniverseId id) {
        UniverseSpec s = spec(server, id);
        RealityState state = RealityState.get(server);
        UniverseProfile p = state.profile(id);
        List<String> out = new ArrayList<>();
        out.add("§l" + s.name + "§r  [" + id.designation() + "]  slot " + id.gx() + "." + id.gz());
        out.add("  " + s.describe());
        out.add("  Status: " + statusLine(server, id));
        out.add("  Archetype: " + s.archetype.displayName + " • materials of " + s.materials.displayName);
        out.add(String.format(Locale.ROOT, "  Gravity %.2fg • time %s • weather %s • hostility %.1f • glitch %.2f",
                s.gravity, s.time.name().toLowerCase(Locale.ROOT), s.weather.name().toLowerCase(Locale.ROOT), s.hostility, s.glitch));
        out.add("  Visits " + p.visits + " • scans " + p.scans + " • events " + p.events + (p.lastEvent.isEmpty() ? "" : " (last: " + p.lastEvent + ")")
                + " • erased " + p.erasures + "× • rebuilt " + p.rebuilds + "×");
        if (!p.discovererName.isEmpty()) out.add("  First charted by " + p.discovererName);
        out.add("  Definition backups: " + state.backups(id).size());
        if (!s.prompt.isEmpty()) out.add("  Manifested from: \"" + s.prompt + "\"");
        out.add("  DNA: " + UniverseDna.encode(s));
        return out;
    }

    // ------------------------------------------------------------------ progression

    public static void research(ServerPlayer player, int points, String reason) {
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        boolean rankUp = data.addResearch(points);
        player.displayClientMessage(Component.literal("+" + points + " research • " + reason).withColor(0x7DF9FF), true);
        if (rankUp) {
            CosmicRank rank = data.rank();
            cinematic(player, CinematicType.ANNOUNCE, 0, player.getEyePosition(), rank.color, 0xFFFFFF, "COSMIC RANK: " + rank.title.toUpperCase(Locale.ROOT),
                    "Your understanding of the multiverse deepens");
            player.serverLevel().playSound(null, player.blockPosition(), RvSounds.UI_MANIFEST.get(), SoundSource.PLAYERS, 1f, 1.2f);
        }
    }

    public static void onArrival(ServerPlayer player, @Nullable UniverseSpec spec, boolean firstDiscovery) {
        if (spec == null) return;
        RealityState state = RealityState.get(player.server);
        UniverseProfile p = state.profile(spec.id);
        p.visits++;
        if (p.firstSeen < 0) {
            p.firstSeen = player.serverLevel().getGameTime();
            p.discoverer = player.getUUID();
            p.discovererName = player.getGameProfile().getName();
        }
        state.touch();
        if (firstDiscovery) research(player, 10, "new universe charted");
    }

    // ------------------------------------------------------------------ cinematics

    public static void cinematic(ServerPlayer player, CinematicType type, int ticks, Vec3 focus, int a, int b, String title, String subtitle) {
        PacketDistributor.sendToPlayer(player, new Payloads.RealityCinematic(type.ordinal(), ticks <= 0 ? type.defaultTicks : ticks,
                focus.x, focus.y, focus.z, a, b, title, subtitle));
    }

    public static void stopCinematic(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Payloads.RealityCinematic(Payloads.RealityCinematic.STOP, 0, 0, 0, 0, 0, 0, "", ""));
    }

    public static Vec3 focusFor(ServerPlayer p) {
        return p.position().add(0, 1.2, 0);
    }

    public static void shield(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, true, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 4, true, false));
    }

    private static List<BlockPos> centres(UniverseId id, List<ServerPlayer> players) {
        List<BlockPos> c = new ArrayList<>();
        c.add(id.center(64));
        for (ServerPlayer p : players) c.add(p.blockPosition());
        return c;
    }

    private static int radius() {
        return RiftverseConfig.get(RiftverseConfig.REWRITE_RADIUS, 96);
    }

    /** Erasure reaches at least as far as anyone can see, so nothing visible survives. */
    private static int visibleRadius(MinecraftServer server) {
        int view = (server.getPlayerList().getViewDistance() + 1) * 16;
        return Math.max(radius(), Math.min(view, 320));
    }

    @Nullable
    private static Outcome guard(UniverseId id) {
        if (InfiniteCorridor.isCorridor(id)) return Outcome.fail("The Infinite Corridor exists outside reality and cannot be rewritten.");
        if (RealityState.statusOf(id) == RealityStatus.ENDED) return Outcome.fail("That universe was ended forever. Nothing remains to work with.");
        if (RealityRewriter.busy(id)) return Outcome.fail("That universe is already being rewritten. Wait for the wave to finish.");
        return null;
    }

    // ------------------------------------------------------------------ operations

    /**
     * First half of every erasure: validates, backs up the definition (and a block snapshot around each occupant),
     * marks the universe ERASED so generation and travel agree, and ends its events. Returns null on success.
     */
    @Nullable
    public static Outcome prepareErase(MinecraftServer server, UniverseId id, @Nullable ServerPlayer actor, String how) {
        return prepareErase(server, id, actor, how, false);
    }

    @Nullable
    public static Outcome prepareErase(MinecraftServer server, UniverseId id, @Nullable ServerPlayer actor, String how, boolean permanent) {
        Outcome g = guard(id);
        if (g != null) return g;
        if (ERASING.contains(id)) return Outcome.fail("That universe is already being erased.");
        RealityState state = RealityState.get(server);
        if (state.profile(id).status.gone()) return Outcome.fail("That universe has already been erased.");
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        if (level == null) return Outcome.fail("The Expanse dimension is not loaded.");
        UniverseSpec spec = spec(server, id);
        if (permanent) {
            state.forgetBackups(id);
            state.setStatus(id, RealityStatus.ENDED);
        } else {
            state.backup(spec, level.getGameTime(), "before " + how, RiftverseConfig.get(RiftverseConfig.BACKUPS_PER_UNIVERSE, 5));
            int n = 0;
            for (ServerPlayer p : playersIn(server, id)) RealitySnapshots.save(level, p.blockPosition(), autoSnapshotName(id, n++));
            state.setStatus(id, RealityStatus.ERASED);
        }
        UniverseProfile profile = state.profile(id);
        profile.erasures++;
        profile.stability = 0f;
        state.record((permanent ? "ENDED FOREVER: " : "") + how + " " + id.designation() + " (" + spec.name + ")" + (actor == null ? "" : " by " + actor.getGameProfile().getName()));
        EventManager.stopIn(server, id);
        ERASING.add(id);
        if (actor != null) {
            actor.getData(RvAttachments.MULTIVERSE.get()).realitiesErased++;
            research(actor, 40, "universe erased");
        }
        return null;
    }

    public static String autoSnapshotName(UniverseId id, int index) {
        return ("auto_" + id.gx() + "_" + id.gz() + "_" + index).toLowerCase(Locale.ROOT);
    }

    /** Undoes {@link #prepareErase} when a protocol is aborted before the point of no return. */
    public static void abortErase(MinecraftServer server, UniverseId id) {
        RealityState state = RealityState.get(server);
        state.setStatus(id, RealityStatus.ACTIVE);
        UniverseProfile p = state.profile(id);
        p.erasures = Math.max(0, p.erasures - 1);
        p.stability = 60f;
        state.record("erasure of " + id.designation() + " aborted");
        ERASING.remove(id);
    }

    /**
     * Second half of every erasure: the unmaking wave around the origin and every occupant, then evacuation of anyone
     * still inside after {@code evacuateAfter} ticks (and again when the wave ends, as a safety net).
     */
    public static void eraseWave(MinecraftServer server, UniverseId id, int evacuateAfter, @Nullable Runnable afterWave) {
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        if (level == null) {
            ERASING.remove(id);
            return;
        }
        List<BlockPos> centres = centres(id, playersIn(server, id));
        RealityRewriter.start(level, RealityRewriter.Mode.ERASE, id, null, centres, visibleRadius(server), () -> {
            rebirth(server, playersIn(server, id));
            if (afterWave != null) afterWave.run();
        });
        Scheduler.later(evacuateAfter, () -> {
            ERASING.remove(id);
            rebirth(server, playersIn(server, id));
        });
    }

    /** The standard (non-protocol) erasure: a dissolve cinematic, then the wave. */
    public static Outcome erase(MinecraftServer server, UniverseId id, @Nullable ServerPlayer actor) {
        Outcome fail = prepareErase(server, id, actor, "erase");
        if (fail != null) return fail;
        UniverseSpec spec = spec(server, id);
        List<ServerPlayer> inside = playersIn(server, id);
        for (ServerPlayer p : inside) {
            cinematic(p, CinematicType.ERASURE, 0, focusFor(p), 0xFF3A5A, 0x2A0010, "UNIVERSE ERASURE", spec.name + " is being unmade");
            shield(p, 260);
        }
        if (actor != null && !inside.contains(actor)) {
            cinematic(actor, CinematicType.ANNOUNCE, 0, actor.getEyePosition(), 0xFF3A5A, 0xFFFFFF, "UNIVERSE ERASED", spec.name + " [" + id.designation() + "]");
        }
        Scheduler.later(30, () -> eraseWave(server, id, 120, null));
        return Outcome.ok("Erasing " + spec.name + " [" + id.designation() + "]. A definition backup was saved; restore it with /multiverse reality restore.");
    }

    /**
     * Rebuilds a universe. With {@code archetype} null and no mutation the current (or, if erased, the last backed-up)
     * definition is regenerated; with mutation a brand-new reality is born in the same slot.
     */
    public static Outcome rebuild(MinecraftServer server, UniverseId id, boolean mutate, @Nullable Archetype archetype, @Nullable ServerPlayer actor) {
        Outcome g = guard(id);
        if (g != null) return g;
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        if (level == null) return Outcome.fail("The Expanse dimension is not loaded.");
        RealityState state = RealityState.get(server);
        UniverseSpec current = spec(server, id);
        UniverseSpec target;
        if (mutate || archetype != null) {
            state.backup(current, level.getGameTime(), "before rebuild", RiftverseConfig.get(RiftverseConfig.BACKUPS_PER_UNIVERSE, 5));
            long seed = new Random().nextLong();
            Archetype a = archetype != null ? archetype : (id.isPrime() ? current.archetype : Archetype.byId(new Random(seed).nextInt(Archetype.values().length)));
            target = SpecFactory.random(id, seed, a, new Random(seed));
            if (id.isPrime()) target.name = "Prime " + a.displayName + " (Rebuilt)";
        } else if (state.profile(id).status == RealityStatus.ERASED && state.latestBackup(id) != null) {
            target = state.latestBackup(id).restore();
        } else {
            target = current.copy();
        }
        target.id = id;
        return reconstruct(server, level, id, target, CinematicType.REBUILD, "RECONSTRUCTION", actor);
    }

    public static Outcome restore(MinecraftServer server, UniverseId id, @Nullable ServerPlayer actor) {
        Outcome g = guard(id);
        if (g != null) return g;
        RealityState state = RealityState.get(server);
        UniverseProfile p = state.profile(id);
        if (p.status == RealityStatus.ARCHIVED) {
            state.setStatus(id, RealityStatus.ACTIVE);
            state.record("unarchive " + id.designation());
            return Outcome.ok("Universe " + id.designation() + " has been unsealed and is reachable again.");
        }
        RealityState.Backup backup = state.latestBackup(id);
        if (backup == null) return Outcome.fail("No definition backup exists for " + id.designation() + ". Use rebuild to regenerate it instead.");
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        if (level == null) return Outcome.fail("The Expanse dimension is not loaded.");
        UniverseSpec target = backup.restore();
        target.id = id;
        return reconstruct(server, level, id, target, CinematicType.RESTORE, "RESTORATION", actor);
    }

    private static Outcome reconstruct(MinecraftServer server, ServerLevel level, UniverseId id, UniverseSpec target, CinematicType cine, String verb,
                                       @Nullable ServerPlayer actor) {
        RealityState state = RealityState.get(server);
        UniverseRegistry.get(server).replace(target);
        state.setStatus(id, RealityStatus.ACTIVE);
        UniverseProfile profile = state.profile(id);
        profile.rebuilds++;
        profile.stability = 100f;
        state.record(verb.toLowerCase(Locale.ROOT) + " " + id.designation() + " → " + target.name + (actor == null ? "" : " by " + actor.getGameProfile().getName()));

        List<ServerPlayer> inside = playersIn(server, id);
        for (ServerPlayer p : inside) {
            cinematic(p, cine, 0, focusFor(p), target.accent, 0xFFF0C8, verb, target.name + " takes shape");
            shield(p, 200);
        }
        Scheduler.later(cine.defaultTicks / 2, () -> {
            for (ServerPlayer p : playersIn(server, id)) {
                UniverseSync.send(p);
                CommonEvents.applyGravity(p);
            }
        });
        if (actor != null) {
            if (!inside.contains(actor)) cinematic(actor, CinematicType.ANNOUNCE, 0, actor.getEyePosition(), target.accent, 0xFFFFFF, verb, target.name + " [" + id.designation() + "]");
            actor.getData(RvAttachments.MULTIVERSE.get()).realitiesRebuilt++;
            research(actor, 40, verb.toLowerCase(Locale.ROOT));
        }
        RealityRewriter.start(level, RealityRewriter.Mode.REBUILD, id, target, centres(id, inside), radius(), () -> {
            for (ServerPlayer p : playersIn(server, id)) {
                UniverseSync.send(p);
                p.displayClientMessage(Component.literal(verb + " of " + target.name + " complete.").withColor(target.accent), true);
            }
        });
        return Outcome.ok(verb.charAt(0) + verb.substring(1).toLowerCase(Locale.ROOT) + " of " + target.name + " [" + id.designation() + "] has begun ("
                + radius() + " block radius around its origin and anyone inside).");
    }

    public static Outcome archive(MinecraftServer server, UniverseId id) {
        if (InfiniteCorridor.isCorridor(id)) return Outcome.fail("The Infinite Corridor cannot be archived.");
        RealityState state = RealityState.get(server);
        UniverseProfile p = state.profile(id);
        if (p.status.gone()) return Outcome.fail("That universe is erased; restore it first.");
        if (p.status == RealityStatus.ARCHIVED) return Outcome.fail("That universe is already archived.");
        UniverseSpec spec = spec(server, id);
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        state.backup(spec, level == null ? 0 : level.getGameTime(), "archived", RiftverseConfig.get(RiftverseConfig.BACKUPS_PER_UNIVERSE, 5));
        state.setStatus(id, RealityStatus.ARCHIVED);
        state.record("archive " + id.designation() + " (" + spec.name + ")");
        EventManager.stopIn(server, id);
        for (ServerPlayer pl : playersIn(server, id)) {
            cinematic(pl, CinematicType.ANNOUNCE, 0, pl.getEyePosition(), 0xB0A0FF, 0xFFFFFF, "UNIVERSE ARCHIVED", spec.name + " is being sealed away");
            Scheduler.later(60, () -> evacuate(pl, "This universe has been sealed in the archive."));
        }
        return Outcome.ok(spec.name + " [" + id.designation() + "] archived. Its terrain is preserved; restore it to reopen travel.");
    }

    public static Outcome stabilize(MinecraftServer server, UniverseId id) {
        RealityState state = RealityState.get(server);
        UniverseProfile p = state.profile(id);
        if (p.status != RealityStatus.ACTIVE) return Outcome.fail("Only active universes can be stabilized.");
        int stopped = EventManager.stopIn(server, id);
        p.stability = 100f;
        UniverseSpec spec = spec(server, id);
        if (spec.glitch > 0f && !InfiniteCorridor.isCorridor(id)) {
            UniverseSpec calm = spec.copy();
            calm.glitch = 0f;
            UniverseRegistry.get(server).replace(calm);
        }
        state.touch();
        state.record("stabilize " + id.designation());
        for (ServerPlayer pl : playersIn(server, id)) {
            UniverseSync.send(pl);
            ServerLevel level = pl.serverLevel();
            level.sendParticles(RvParticles.RING.get().with(0x7DF9FF, 12f, 30), pl.getX(), pl.getY() + 1, pl.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.SPARK.get().with(0xFFFFFF, 0.6f, 30), pl.getX(), pl.getY() + 1, pl.getZ(), 60, 3, 1, 3, 0.2);
            level.playSound(null, pl.blockPosition(), RvSounds.GRAVITY_PULSE.get(), SoundSource.PLAYERS, 1f, 1.4f);
            cinematic(pl, CinematicType.SCAN, 0, pl.getEyePosition(), 0x7DF9FF, 0xFFFFFF, "", "");
        }
        return Outcome.ok("Stabilized " + spec.name + ": stability 100%, glitching removed, " + stopped + " active event(s) ended.");
    }

    /** Traits that {@link #modify} understands, for tab completion. */
    public static final List<String> TRAITS = List.of("gravity", "time", "weather", "storm", "glitch", "fog", "hostility", "saturation",
            "creature_scale", "vacuum", "dreamlike", "name");

    public static Outcome modify(MinecraftServer server, UniverseId id, String trait, String value) {
        if (InfiniteCorridor.isCorridor(id)) return Outcome.fail("The Infinite Corridor's laws are fixed.");
        if (RealityState.get(server).profile(id).status != RealityStatus.ACTIVE) return Outcome.fail("Only active universes can be modified.");
        UniverseSpec s = spec(server, id).copy();
        String v = value.trim();
        try {
            switch (trait.toLowerCase(Locale.ROOT)) {
                case "gravity" -> s.gravity = clamp(Float.parseFloat(v), 0.05f, 4f);
                case "time" -> s.time = switch (v.toLowerCase(Locale.ROOT)) {
                    case "cycle" -> TimeMode.CYCLE;
                    case "day" -> TimeMode.ETERNAL_DAY;
                    case "dusk" -> TimeMode.ETERNAL_DUSK;
                    case "night" -> TimeMode.ETERNAL_NIGHT;
                    default -> throw new IllegalArgumentException("time must be cycle, day, dusk or night");
                };
                case "weather" -> s.weather = WeatherKind.valueOf(v.toUpperCase(Locale.ROOT));
                case "storm" -> s.stormIntensity = clamp(Float.parseFloat(v), 0f, 1f);
                case "glitch" -> s.glitch = clamp(Float.parseFloat(v), 0f, 1f);
                case "fog" -> s.fogDensity = clamp(Float.parseFloat(v), 0f, 1f);
                case "hostility" -> s.hostility = clamp(Float.parseFloat(v), 0f, 3f);
                case "saturation" -> s.saturation = clamp(Float.parseFloat(v), 0f, 2f);
                case "creature_scale" -> s.creatureScale = clamp(Float.parseFloat(v), 0.5f, 4f);
                case "vacuum" -> s.vacuum = parseBool(v);
                case "dreamlike" -> s.dreamlike = parseBool(v);
                case "name" -> {
                    if (v.isEmpty() || v.length() > 64) throw new IllegalArgumentException("name must be 1-64 characters");
                    s.name = v;
                }
                default -> {
                    return Outcome.fail("Unknown trait '" + trait + "'. Known: " + String.join(", ", TRAITS));
                }
            }
        } catch (IllegalArgumentException e) {
            return Outcome.fail("Invalid value '" + value + "' for " + trait + ": " + e.getMessage());
        }
        UniverseRegistry.get(server).replace(s);
        RealityState state = RealityState.get(server);
        UniverseProfile p = state.profile(id);
        p.stability = Math.max(0f, p.stability - 4f);
        state.touch();
        state.record("modify " + id.designation() + " " + trait + "=" + v);
        for (ServerPlayer pl : playersIn(server, id)) {
            UniverseSync.send(pl);
            CommonEvents.applyGravity(pl);
            cinematic(pl, CinematicType.SCAN, 0, pl.getEyePosition(), s.accent, 0xFFFFFF, "", "");
            pl.displayClientMessage(Component.literal("Reality shifts: " + trait + " → " + v).withColor(s.accent), true);
        }
        return Outcome.ok("Set " + trait + " of " + s.name + " to " + v + ".");
    }

    private static boolean parseBool(String v) {
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("expected true or false");
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) throw new IllegalArgumentException("not a number");
        return Math.max(lo, Math.min(hi, v));
    }

    public static Outcome scan(ServerPlayer player, UniverseId id) {
        RealityState state = RealityState.get(player.server);
        UniverseProfile p = state.profile(id);
        p.scans++;
        state.touch();
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        cinematic(player, CinematicType.SCAN, 0, player.getEyePosition(), spec(player.server, id).accent, 0xFFFFFF, "", "");
        player.serverLevel().playSound(null, player.blockPosition(), RvSounds.UI_SELECT.get(), SoundSource.PLAYERS, 1f, 1.3f);
        if (data.scan(id.pack())) research(player, 15, "universe scanned");
        return Outcome.ok(String.join("\n", profileLines(player.server, id)));
    }

    public static Outcome visit(ServerPlayer player, UniverseId id) {
        if (!RealityState.isAccessible(id)) return Outcome.fail("That universe is " + RealityState.statusOf(id).name().toLowerCase(Locale.ROOT) + " and cannot be entered.");
        UniverseSpec spec = spec(player.server, id);
        boolean ok = TransitManager.begin(player, TransitKind.CONSOLE, Destination.universe(id), player.getEyePosition().add(player.getLookAngle().scale(3)),
                spec.accent, spec.nebulaB, true);
        return ok ? Outcome.ok("Opening a passage to " + spec.name + "...") : Outcome.fail("You are already travelling, or recovering from a journey.");
    }

    public static void clear() {
        ERASING.clear();
        SEALING.clear();
    }

    /** Everyone left in an unmade world witnesses the Genesis Protocol and wakes in a newborn universe. */
    public static void rebirth(MinecraftServer server, List<ServerPlayer> players) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : players) if (!p.isSpectator() && !TransitManager.inTransit(p) && !GenesisManager.inGenesis(p)) list.add(p);
        if (list.isEmpty()) return;
        if (GenesisManager.begin(server, list) == null) for (ServerPlayer p : list) evacuate(p, "The universe you stood in no longer exists.");
    }

    /** Sends a player out of a universe that is no longer reachable. */
    public static void evacuate(ServerPlayer player, String reason) {
        if (TransitManager.inTransit(player)) return;
        player.displayClientMessage(Component.literal(reason).withColor(0xFF8A9A), false);
        TransitManager.begin(player, TransitKind.CONSOLE, Destination.nexus(), player.getEyePosition().add(player.getLookAngle().scale(3)), 0xFF3A5A, 0xFFC14D, false);
    }

    // ------------------------------------------------------------------ ending whole dimensions

    /** Dimensions currently playing their ending sequence (occupants are evacuated on cue, not by the watchdog). */
    private static final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> SEALING = new java.util.HashSet<>();

    public static boolean canEndDimension(ServerLevel level) {
        return level.dimension() != RvWorldgen.NEXUS && level.dimension() != RvWorldgen.EXPANSE;
    }

    /** Seals a vanilla/modded dimension forever (Overworld, Nether, End...). Travel, portals and respawns into it are refused from now on. */
    @Nullable
    public static Outcome prepareEndDimension(MinecraftServer server, ServerLevel level, @Nullable ServerPlayer actor, String how) {
        if (!canEndDimension(level)) return Outcome.fail("The Nexus and the Expanse cannot be ended; end a universe inside the Expanse instead.");
        if (RealityState.isSealed(level.dimension())) return Outcome.fail("That dimension has already been ended.");
        if (SEALING.contains(level.dimension())) return Outcome.fail("That dimension is already ending.");
        RealityState state = RealityState.get(server);
        state.seal(level.dimension());
        state.record("ENDED FOREVER: " + how + " " + level.dimension().location() + (actor == null ? "" : " by " + actor.getGameProfile().getName()));
        SEALING.add(level.dimension());
        if (actor != null) {
            actor.getData(RvAttachments.MULTIVERSE.get()).realitiesErased++;
            research(actor, 60, "a world ended");
        }
        return null;
    }

    public static void endDimensionWave(MinecraftServer server, ServerLevel level, int evacuateAfter) {
        List<BlockPos> centres = new ArrayList<>();
        for (ServerPlayer p : level.players()) centres.add(p.blockPosition());
        if (centres.isEmpty()) centres.add(level.getSharedSpawnPos());
        RealityRewriter.start(level, RealityRewriter.Mode.ERASE, null, null, centres, visibleRadius(server), () -> {
            rebirth(server, new ArrayList<>(level.players()));
        });
        Scheduler.later(evacuateAfter, () -> {
            SEALING.remove(level.dimension());
            rebirth(server, new ArrayList<>(level.players()));
        });
    }

    public static void abortEndDimension(MinecraftServer server, ServerLevel level) {
        SEALING.remove(level.dimension());
        RealityState.get(server).unseal(level.dimension());
    }

    public static String worldName(ServerLevel level) {
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) return "Earth (the Overworld)";
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) return "the Nether";
        if (level.dimension() == net.minecraft.world.level.Level.END) return "the End";
        return level.dimension().location().toString();
    }

    /** Called every second per player: pushes anyone found inside a sealed universe or ended dimension back to the Nexus. */
    public static void playerSecond(ServerPlayer player) {
        if (RealityState.isSealed(player.level().dimension()) && !SEALING.contains(player.level().dimension()) && !TransitManager.inTransit(player)
                && !GenesisManager.inGenesis(player)
                && !player.isSpectator()) {
            evacuate(player, worldName(player.serverLevel()) + " has ended. Nothing remains there.");
            return;
        }
        UniverseId id = universeOf(player);
        if (id == null || RealityState.isAccessible(id) || ERASING.contains(id) || TransitManager.inTransit(player) || GenesisManager.inGenesis(player)) return;
        if (player.isSpectator()) return;
        evacuate(player, "This universe is " + RealityState.statusOf(id).name().toLowerCase(Locale.ROOT) + ". You are pulled back to the Nexus.");
    }
}
