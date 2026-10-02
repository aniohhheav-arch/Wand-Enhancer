package dev.riftverse.transit;

import dev.riftverse.block.RiftType;
import dev.riftverse.multiverse.InfiniteCorridor;
import dev.riftverse.multiverse.RealityState;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.universe.UniverseTraits.TerrainMode;
import dev.riftverse.world.NexusLayout;
import dev.riftverse.world.TerrainSampler;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves destinations into concrete, safe arrival points. */
public final class UniverseTravel {
    public record Target(ServerLevel level, Vec3 pos, float yaw, @Nullable UniverseSpec spec, String title, String subtitle, int color, boolean nexus) {}

    private UniverseTravel() {}

    public static boolean isRiftverseDimension(Level level) {
        ResourceKey<Level> key = level.dimension();
        return key == RvWorldgen.EXPANSE || key == RvWorldgen.NEXUS;
    }

    @Nullable
    public static Target resolve(ServerPlayer player, Destination dest, RandomSource random) {
        MinecraftServer server = player.server;
        UniverseRegistry reg = UniverseRegistry.get(server);
        Random jr = new Random(random.nextLong());
        return switch (dest.kind()) {
            case UNIVERSE -> {
                UniverseId uid = UniverseId.unpack(dest.universe());
                if (!RealityState.isAccessible(uid)) yield null;
                UniverseSpec spec = UniverseRegistry.specFor(uid);
                if (!InfiniteCorridor.isCorridor(uid)) reg.remember(spec);
                yield toUniverse(server, spec, random);
            }
            case ARCHETYPE -> {
                Archetype a = Archetype.byId(dest.archetype());
                yield RealityState.isAccessible(UniverseId.prime(a)) ? toUniverse(server, reg.primeSpec(a), random) : null;
            }
            case RANDOM -> toUniverse(server, reg.randomUniverse(jr, null), random);
            case FLAVOR -> toUniverse(server, reg.randomUniverse(jr, RiftType.byId(dest.flavor()).destinations()), random);
            case NEXUS -> toNexus(server);
            case LOCATION -> {
                ResourceKey<Level> key = dest.dimensionKey();
                ServerLevel level = key == null ? null : server.getLevel(key);
                if (level == null) yield home(player);
                BlockPos p = safeAround(level, dest.pos(), 12);
                yield new Target(level, Vec3.atBottomCenterOf(p), player.getYRot(), level.dimension() == RvWorldgen.EXPANSE ? UniverseRegistry.specAt(p.getX(), p.getZ()) : null,
                        "RETURN PASSAGE", "The rift remembers where you came from", 0xE8F4FF, level.dimension() == RvWorldgen.NEXUS);
            }
            case HOME -> home(player);
        };
    }

    @Nullable
    private static Target toUniverse(MinecraftServer server, UniverseSpec spec, RandomSource random) {
        ServerLevel level = server.getLevel(RvWorldgen.EXPANSE);
        if (level == null) return null;
        if (InfiniteCorridor.isCorridor(spec.id)) {
            BlockPos p = InfiniteCorridor.arrival(random);
            level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            return new Target(level, Vec3.atBottomCenterOf(p), -90f, spec, spec.name.toUpperCase(), "Every door leads somewhere else", spec.accent, false);
        }
        TerrainSampler sampler = TerrainSampler.of(spec);
        int cx = spec.id.centerX() + random.nextInt(97) - 48;
        int cz = spec.id.centerZ() + random.nextInt(97) - 48;
        int[] found = sampler.findSurface(cx, cz, 180);
        BlockPos pos;
        if (found == null) {
            int y = spec.hasSea && spec.terrain != TerrainMode.FLOATING ? spec.seaLevel + 1 : 110;
            pos = platform(level, new BlockPos(cx, y, cz));
        } else {
            BlockPos guess = new BlockPos(found[0], found[1], found[2]);
            BlockPos safe = scanColumn(level, guess, spec.terrain == TerrainMode.INVERTED);
            pos = safe != null ? safe : platform(level, guess.above(2));
        }
        String subtitle = spec.id.designation() + "  •  " + spec.describe();
        return new Target(level, Vec3.atBottomCenterOf(pos), random.nextFloat() * 360f, spec, spec.name.toUpperCase(), subtitle, spec.accent, false);
    }

    private static Target toNexus(MinecraftServer server) {
        ServerLevel level = server.getLevel(RvWorldgen.NEXUS);
        if (level == null) level = server.overworld();
        BlockPos p = NexusLayout.ARRIVAL;
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        return new Target(level, Vec3.atBottomCenterOf(p), 180f, null, "THE MULTIVERSE NEXUS", "Where every reality converges", 0xFFC14D, true);
    }

    private static Target home(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel level = null;
        BlockPos pos = null;
        if (player.getRespawnPosition() != null) {
            level = server.getLevel(player.getRespawnDimension());
            pos = player.getRespawnPosition();
        }
        if (level == null) {
            var data = player.getData(RvAttachments.MULTIVERSE.get());
            if (data.hasHome) {
                ResourceKey<Level> key = new Destination(Destination.Kind.LOCATION, 0, 0, data.homeDimension, BlockPos.ZERO, 0).dimensionKey();
                if (key != null) level = server.getLevel(key);
                pos = BlockPos.of(data.homePos);
            }
        }
        if (level == null || pos == null) {
            level = server.overworld();
            pos = level.getSharedSpawnPos();
        }
        BlockPos safe = safeAround(level, pos, 16);
        return new Target(level, Vec3.atBottomCenterOf(safe), player.getYRot(), null, "HOME", "The familiar sky of your own reality", 0xFFFFFF, false);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return below.isFaceSturdy(level, feet.below(), Direction.UP) && below.getFluidState().isEmpty()
                && at.getCollisionShape(level, feet).isEmpty() && at.getFluidState().isEmpty()
                && head.getCollisionShape(level, feet.above()).isEmpty() && head.getFluidState().isEmpty();
    }

    @Nullable
    private static BlockPos scanColumn(ServerLevel level, BlockPos guess, boolean underCeiling) {
        level.getChunk(guess.getX() >> 4, guess.getZ() >> 4);
        int top = underCeiling ? guess.getY() + 8 : Math.min(level.getMaxBuildHeight() - 3, guess.getY() + 48);
        int bottom = Math.max(level.getMinBuildHeight() + 2, guess.getY() - 40);
        for (int y = top; y >= bottom; y--) {
            BlockPos p = new BlockPos(guess.getX(), y, guess.getZ());
            if (standable(level, p)) return p;
        }
        return null;
    }

    /** Finds a safe spot near a position in any dimension, building a small platform as a last resort. */
    public static BlockPos safeAround(ServerLevel level, BlockPos around, int radius) {
        level.getChunk(around.getX() >> 4, around.getZ() >> 4);
        if (standable(level, around)) return around;
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += Math.max(1, r)) {
                for (int dz = -r; dz <= r; dz += Math.max(1, r)) {
                    int x = around.getX() + dx;
                    int z = around.getZ() + dz;
                    for (int dy = 6; dy >= -12; dy--) {
                        BlockPos p = new BlockPos(x, around.getY() + dy, z);
                        if (p.getY() <= level.getMinBuildHeight() + 1) continue;
                        if (standable(level, p)) return p;
                    }
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.getX(), around.getZ());
        BlockPos top = new BlockPos(around.getX(), Math.max(y, level.getMinBuildHeight() + 5), around.getZ());
        if (standable(level, top)) return top;
        return platform(level, top.getY() > level.getMinBuildHeight() + 6 ? top : around);
    }

    /** A small glowing landing pad suspended in place, so travellers never arrive over the void. */
    public static BlockPos platform(ServerLevel level, BlockPos at) {
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        BlockState stone = RvBlocks.NEXUS_STONE.get().defaultBlockState();
        BlockState glow = RvBlocks.NEXUS_GLOW.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                level.setBlockAndUpdate(at.offset(dx, -1, dz), corner || (dx == 0 && dz == 0) ? glow : stone);
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos p = at.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
        return at;
    }
}
