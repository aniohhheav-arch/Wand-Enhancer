package dev.riftverse.transit;

import dev.riftverse.block.RiftBlock;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseSync;
import dev.riftverse.player.PlayerMultiverseData;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.util.Advancements;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives every journey between realities on the server. The client plays the matching cinematic; the server keeps the
 * traveller safe, moves them at the right beat and leaves a temporary return rift behind.
 */
public final class TransitManager {
    private static final Map<UUID, Transit> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final int PROTECT_AFTER_ARRIVAL = 80;
    private static final int RETURN_RIFT_LIFETIME = 2400;

    private TransitManager() {}

    private static final class Transit {
        final TransitKind kind;
        final UniverseTravel.Target target;
        final String originDim;
        final BlockPos originPos;
        final boolean returnRift;
        int tick;
        boolean arrived;
        int arrivedTick;

        Transit(TransitKind kind, UniverseTravel.Target target, String originDim, BlockPos originPos, boolean returnRift) {
            this.kind = kind;
            this.target = target;
            this.originDim = originDim;
            this.originPos = originPos;
            this.returnRift = returnRift;
        }
    }

    public static boolean inTransit(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean isProtected(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Begins a journey. Returns false if the player is already travelling or the destination is unreachable. */
    public static boolean begin(ServerPlayer player, TransitKind kind, Destination dest, Vec3 focus, int colorA, int colorB, boolean leaveReturnRift) {
        UUID id = player.getUUID();
        if (ACTIVE.containsKey(id) || COOLDOWN.getOrDefault(id, 0) > 0) return false;
        UniverseTravel.Target target = UniverseTravel.resolve(player, dest, player.getRandom());
        if (target == null) return false;

        ServerLevel from = player.serverLevel();
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        if (!UniverseTravel.isRiftverseDimension(from)) data.setHome(from.dimension().location().toString(), player.blockPosition());

        Vec3 look = player.getLookAngle();
        BlockPos back = BlockPos.containing(player.getX() - look.x * 3.0, player.getY(), player.getZ() - look.z * 3.0);
        ACTIVE.put(id, new Transit(kind, target, from.dimension().location().toString(), back, leaveReturnRift));

        BlockPos tp = BlockPos.containing(target.pos());
        target.level().getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(tp), 3, tp);

        PacketDistributor.sendToPlayer(player, new Payloads.Cinematic(kind.ordinal(), kind.teleportTick, focus.x, focus.y, focus.z, colorA, colorB, 1f));
        SoundEvent sound = switch (kind) {
            case BLACK_HOLE -> RvSounds.BLACK_HOLE_PULL.get();
            case PORTAL -> RvSounds.PORTAL_ENTER.get();
            default -> RvSounds.RIFT_ENTER.get();
        };
        from.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    public static void tick(MinecraftServer server) {
        COOLDOWN.replaceAll((k, v) -> v - 1);
        COOLDOWN.values().removeIf(v -> v <= 0);
        Iterator<Map.Entry<UUID, Transit>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Transit> e = it.next();
            Transit t = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            t.tick++;
            player.fallDistance = 0;
            if (!t.arrived) departureFx(player, t);
            if (!t.arrived && t.tick >= t.kind.teleportTick) {
                arrive(player, t);
                t.arrived = true;
                t.arrivedTick = t.tick;
            }
            if (t.arrived && t.tick - t.arrivedTick > PROTECT_AFTER_ARRIVAL) {
                it.remove();
                COOLDOWN.put(e.getKey(), 40);
            }
        }
    }

    /**
     * What everyone else sees while a player is leaving: a tightening vortex of light around them, a column of energy
     * and finally a collapse flash at the moment they vanish.
     */
    private static void departureFx(ServerPlayer player, Transit t) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        float k = Math.min(1f, t.tick / (float) Math.max(1, t.kind.teleportTick));
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        int color = t.kind == TransitKind.BLACK_HOLE ? 0xFF8A3A : 0x8F6BFF;
        for (int i = 0; i < 3; i++) {
            double a = t.tick * 0.45 + i * Math.PI * 2 / 3;
            double r = 1.6 * (1.0 - k) + 0.3;
            level.sendParticles(dev.riftverse.registry.RvParticles.STREAK.get().with(i == 1 ? 0xFFFFFF : color, 0.8f, 14),
                    x + Math.cos(a) * r, y + 0.2 + (t.tick % 20) * 0.1, z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
        }
        if (t.tick % 6 == 0) level.sendParticles(dev.riftverse.registry.RvParticles.RING.get().with(color, 1.5f + 2f * k, 12), x, y + 0.1, z, 1, 0, 0, 0, 0);
        if (k > 0.5f && t.tick % 2 == 0) level.sendParticles(dev.riftverse.registry.RvParticles.INFALL.get().with(0xFFFFFF, 0.6f, 20), x, y + 1, z, 4, 0.6, 1, 0.6, 0.05);
        if (t.tick == t.kind.teleportTick - 1) {
            level.sendParticles(dev.riftverse.registry.RvParticles.RING.get().with(0xFFFFFF, 6f, 16), x, y + 1, z, 1, 0, 0, 0, 0);
            level.sendParticles(dev.riftverse.registry.RvParticles.SPARK.get().with(color, 0.8f, 30), x, y + 1, z, 60, 0.4, 0.9, 0.4, 0.4);
            level.playSound(null, x, y, z, RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1.2f, 1.6f);
        }
    }

    private static void arrive(ServerPlayer player, Transit t) {
        UniverseTravel.Target target = t.target;
        ServerLevel from = player.serverLevel();
        Vec3 p = target.pos();
        Vec3 departedFrom = player.position();
        player.teleportTo(target.level(), p.x, p.y, p.z, target.yaw(), 0f);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        data.journeys++;
        Advancements.award(player, "first_rift");
        if (target.spec() != null) {
            boolean first = data.discover(target.spec().id.pack());
            dev.riftverse.multiverse.RealityOps.onArrival(player, target.spec(), first);
            if (data.discoveredCount() >= 10) Advancements.award(player, "cartographer");
            if (target.spec().prompt != null && !target.spec().prompt.isEmpty()) Advancements.award(player, "dreamwalker");
        }
        if (target.nexus()) {
            data.visitedNexus = true;
            Advancements.award(player, "nexus");
        }
        if (t.kind == TransitKind.BLACK_HOLE) Advancements.award(player, "event_horizon");

        if (t.returnRift && (from != target.level() || from.dimension() != target.level().dimension() || player.distanceToSqr(Vec3.atCenterOf(t.originPos)) > 64 * 64)) {
            placeReturnRift(player, target, t);
        }

        target.level().sendParticles(dev.riftverse.registry.RvParticles.RING.get().with(target.color(), 5f, 18), p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
        target.level().sendParticles(dev.riftverse.registry.RvParticles.SPARK.get().with(target.color(), 0.8f, 30), p.x, p.y + 1, p.z, 50, 0.4, 0.9, 0.4, 0.3);
        companions(player, from, departedFrom, target);
        UniverseSync.send(player);
        PacketDistributor.sendToPlayer(player, new Payloads.Arrival(target.title(), target.subtitle(), target.color(), t.kind.ordinal()));
        target.level().playSound(null, p.x, p.y, p.z, RvSounds.UNIVERSE_ARRIVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Tamed companions follow their owner through the passage; hunters on the player's trail sometimes pursue. */
    private static void companions(ServerPlayer player, ServerLevel from, Vec3 departedFrom, UniverseTravel.Target target) {
        if (!dev.riftverse.multiverse.MigrationManager.enabled(from)) return;
        Destination there = Destination.location(target.level().dimension(), BlockPos.containing(target.pos()));
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(departedFrom, departedFrom).inflate(16);
        for (net.minecraft.world.entity.TamableAnimal pet : from.getEntitiesOfClass(net.minecraft.world.entity.TamableAnimal.class, box,
                a -> player.getUUID().equals(a.getOwnerUUID()) && !a.isOrderedToSit())) {
            dev.riftverse.multiverse.MigrationManager.migrate(pet, there, target.color());
        }
        for (net.minecraft.world.entity.Mob hunter : from.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> m.getTarget() == player)) {
            if (player.getRandom().nextFloat() > 0.5f) continue;
            dev.riftverse.multiverse.Scheduler.later(40 + player.getRandom().nextInt(40), () -> {
                if (!hunter.isAlive()) return;
                var arrived = dev.riftverse.multiverse.MigrationManager.migrate(hunter, there, 0xFF2440);
                if (arrived instanceof net.minecraft.world.entity.Mob m && player.isAlive() && m.level() == player.level()) m.setTarget(player);
            });
        }
    }

    private static void placeReturnRift(ServerPlayer player, UniverseTravel.Target target, Transit t) {
        ServerLevel level = target.level();
        Direction facing = Direction.fromYRot(target.yaw());
        BlockPos base = BlockPos.containing(target.pos());
        for (int dist = 4; dist <= 6; dist++) {
            BlockPos at = base.relative(facing.getOpposite(), dist).above();
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
            level.setBlockAndUpdate(at, RvBlocks.RIFT.get().defaultBlockState().setValue(RiftBlock.TYPE, RiftType.RETURN));
            if (level.getBlockEntity(at) instanceof RiftBlockEntity rift) {
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(t.originDim);
                if (rl != null) {
                    var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
                    rift.configure(Destination.location(key, t.originPos), level.getGameTime() + RETURN_RIFT_LIFETIME, false);
                }
            }
            return;
        }
    }

    public static void onLogout(Player player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void clear() {
        ACTIVE.clear();
        COOLDOWN.clear();
    }
}
