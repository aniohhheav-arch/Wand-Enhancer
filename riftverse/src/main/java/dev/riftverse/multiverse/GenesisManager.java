package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseSync;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.transit.UniverseTravel;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Genesis Protocol on the server. When a universe is unmade, everyone inside is not sent to the Nexus: a brand new
 * universe is created (a real, persisted registry entry in a fresh slot of the Expanse), each witness is held
 * motionless and invulnerable behind the opaque cinematic, moved to a safe spot in the new reality while its terrain
 * generates in the background, and released when the gateway opens. If anything fails they fall back to the Nexus.
 */
public final class GenesisManager {
    private static final int MOVE_AT = 30;

    private static final class Session {
        final UniverseSpec spec;
        final int duration;
        int tick;
        @Nullable
        ServerLevel holdLevel;
        @Nullable
        Vec3 hold;
        boolean moved;
        boolean failed;

        Session(UniverseSpec spec, int duration) {
            this.spec = spec;
            this.duration = duration;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private GenesisManager() {}

    public static boolean inGenesis(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static int durationTicks() {
        return RiftverseConfig.get(RiftverseConfig.GENESIS_SECONDS, 80) * 20;
    }

    /** Begins rebirth for everyone listed (all share one newborn universe). Returns the universe, or null if none was made. */
    @Nullable
    public static UniverseSpec begin(MinecraftServer server, List<ServerPlayer> players) {
        players.removeIf(p -> SESSIONS.containsKey(p.getUUID()) || p.isRemoved());
        if (players.isEmpty()) return null;
        UniverseSpec spec = UniverseRegistry.get(server).randomUniverse(new Random(), null);
        RealityState state = RealityState.get(server);
        UniverseProfile prof = state.profile(spec.id);
        prof.stability = 100f;
        state.record("genesis: " + spec.name + " [" + spec.id.designation() + "] born for " + players.size() + " witness(es)");
        int duration = durationTicks();
        float seed = (spec.seed & 0xFFFF) / 997f;
        for (ServerPlayer p : players) {
            SESSIONS.put(p.getUUID(), new Session(spec, duration));
            p.setInvulnerable(true);
            p.setDeltaMovement(Vec3.ZERO);
            RealityOps.stopCinematic(p);
            PacketDistributor.sendToPlayer(p, new Payloads.Genesis(duration, spec.name, spec.id.designation(), spec.accent,
                    spec.archetype.signatureColor, seed));
        }
        return spec;
    }

    public static void skip(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (!s.moved) move(player, s);
        s.tick = Math.max(s.tick, s.duration);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            s.tick++;
            p.fallDistance = 0;
            if (!s.moved && s.tick >= MOVE_AT) move(p, s);
            // hold the witness still: gameplay is paused for them while existence is rewritten
            if (s.hold != null && s.holdLevel != null && p.level() == s.holdLevel && p.position().distanceToSqr(s.hold) > 0.25) {
                p.connection.teleport(s.hold.x, s.hold.y, s.hold.z, p.getYRot(), p.getXRot());
            }
            p.setDeltaMovement(Vec3.ZERO);
            if (s.tick >= s.duration) {
                it.remove();
                release(p, s);
            }
        }
    }

    private static void move(ServerPlayer p, Session s) {
        s.moved = true;
        UniverseTravel.Target target = RealityState.isAccessible(s.spec.id) ? UniverseTravel.resolve(p, Destination.universe(s.spec.id), p.getRandom()) : null;
        if (target == null) {
            s.failed = true;
            return;
        }
        BlockPos tp = BlockPos.containing(target.pos());
        target.level().getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(tp), 4, tp);
        p.teleportTo(target.level(), target.pos().x, target.pos().y, target.pos().z, target.yaw(), 0f);
        s.holdLevel = target.level();
        s.hold = target.pos();
        UniverseSync.send(p);
    }

    private static void release(ServerPlayer p, Session s) {
        p.setInvulnerable(p.isCreative() || p.isSpectator());
        PacketDistributor.sendToPlayer(p, new Payloads.GenesisSkip(0));
        if (s.failed || p.level().dimension() != RvWorldgen.EXPANSE) {
            p.displayClientMessage(Component.literal("The newborn universe could not hold you. You drift to the Nexus.").withColor(0xFF8A9A), false);
            TransitManager.begin(p, TransitKind.CONSOLE, Destination.nexus(), p.getEyePosition(), 0xFFFFFF, 0xFFC14D, false);
            return;
        }
        var data = p.getData(RvAttachments.MULTIVERSE.get());
        boolean first = data.discover(s.spec.id.pack());
        RealityOps.onArrival(p, s.spec, first);
        RealityOps.research(p, 50, "witnessed the birth of a universe");
        RealityOps.cinematic(p, CinematicType.ANNOUNCE, 120, p.getEyePosition(), s.spec.accent, 0xFFFFFF, "A NEW UNIVERSE IS BORN",
                s.spec.name + "  •  " + s.spec.id.designation() + "  •  Stability 100%");
    }

    public static void clear() {
        SESSIONS.clear();
    }
}
