package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Reality reset snapshots: a bounded box of blocks (including air and block entity data, excluding entities) saved to
 * {@code <world>/riftverse_snapshots/<name>.nbt} with vanilla's structure-template format, and placed back on demand.
 */
public final class RealitySnapshots {
    private RealitySnapshots() {}

    private static Path dir(MinecraftServer server) throws IOException {
        Path d = server.getWorldPath(LevelResource.ROOT).resolve("riftverse_snapshots");
        Files.createDirectories(d);
        return d;
    }

    public static boolean validName(String name) {
        return name.matches("[a-z0-9_\\-]{1,32}");
    }

    public static RealityOps.Outcome save(ServerLevel level, BlockPos around, String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!validName(name)) return RealityOps.Outcome.fail("Snapshot names use a-z, 0-9, _ and - (max 32).");
        int r = RiftverseConfig.get(RiftverseConfig.SNAPSHOT_RADIUS, 24);
        int h = RiftverseConfig.get(RiftverseConfig.SNAPSHOT_HEIGHT, 64);
        int minY = Math.max(level.getMinBuildHeight(), around.getY() - h / 3);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, minY + h - 1);
        BlockPos origin = new BlockPos(around.getX() - r, minY, around.getZ() - r);
        Vec3i size = new Vec3i(r * 2 + 1, maxY - minY + 1, r * 2 + 1);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, false, Blocks.STRUCTURE_VOID);
        CompoundTag tag = template.save(new CompoundTag());
        tag.putString("riftverse_dimension", level.dimension().location().toString());
        tag.putLong("riftverse_origin", origin.asLong());
        tag.putLong("riftverse_time", level.getGameTime());
        try {
            NbtIo.writeCompressed(tag, dir(level.getServer()).resolve(name + ".nbt"));
        } catch (IOException e) {
            return RealityOps.Outcome.fail("Could not write snapshot: " + e.getMessage());
        }
        return RealityOps.Outcome.ok("Snapshot '" + name + "' saved: " + size.getX() + "×" + size.getY() + "×" + size.getZ() + " blocks at "
                + origin.toShortString() + " in " + level.dimension().location() + ".");
    }

    public static RealityOps.Outcome load(MinecraftServer server, String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!validName(name)) return RealityOps.Outcome.fail("Invalid snapshot name.");
        CompoundTag tag;
        try {
            Path file = dir(server).resolve(name + ".nbt");
            if (!Files.exists(file)) return RealityOps.Outcome.fail("No snapshot named '" + name + "'.");
            tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return RealityOps.Outcome.fail("Could not read snapshot: " + e.getMessage());
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("riftverse_dimension"));
        ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) return RealityOps.Outcome.fail("The snapshot's dimension is not loaded.");
        BlockPos origin = BlockPos.of(tag.getLong("riftverse_origin"));
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), tag);
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2);
        return RealityOps.Outcome.ok("Snapshot '" + name + "' restored at " + origin.toShortString() + " in " + dim + ".");
    }

    public static List<String> list(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(server), "*.nbt")) {
            for (Path p : ds) {
                String f = p.getFileName().toString();
                out.add(f.substring(0, f.length() - 4));
            }
        } catch (IOException ignored) {
        }
        out.sort(String::compareTo);
        return out;
    }

    public static RealityOps.Outcome delete(MinecraftServer server, String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!validName(name)) return RealityOps.Outcome.fail("Invalid snapshot name.");
        try {
            boolean gone = Files.deleteIfExists(dir(server).resolve(name + ".nbt"));
            return gone ? RealityOps.Outcome.ok("Snapshot '" + name + "' deleted.") : RealityOps.Outcome.fail("No snapshot named '" + name + "'.");
        } catch (IOException e) {
            return RealityOps.Outcome.fail("Could not delete snapshot: " + e.getMessage());
        }
    }

}
