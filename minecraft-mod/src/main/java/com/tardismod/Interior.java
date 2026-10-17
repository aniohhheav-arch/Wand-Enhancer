package com.tardismod;

import com.tardismod.block.TardisBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

/**
 * Bigger-on-the-inside: one console room per TARDIS, laid out along the x axis of the interior dimension, SLOT apart.
 * A box gets its room the first time someone walks in (allocated by scanning for the first free exit door).
 */
public final class Interior {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, TardisMod.id("interior"));
    public static final int SLOT = 80, R = 12, H = 12, Y0 = 64;

    private Interior() {}

    public static BlockPos center(int id) { return new BlockPos(id * SLOT, Y0, 0); }
    public static BlockPos doorPos(int id) { return center(id).offset(0, 1, 9); }
    /** Which slot a position inside the interior dimension belongs to. */
    public static int idOf(BlockPos p) { return Math.max(0, Math.floorDiv(p.getX() + SLOT / 2, SLOT)); }

    /** First slot whose exit door has not been built yet; builds it so the id stays reserved. */
    public static int allocate(ServerLevel interior) {
        int id = 0;
        while (interior.getBlockState(doorPos(id)).getBlock() == TardisMod.TARDIS_BLOCK) id++;
        build(interior, id);
        return id;
    }

    public static void enter(ServerPlayer player, ServerLevel from, BlockPos exteriorPos) {
        ServerLevel interior = player.level().getServer().getLevel(KEY);
        if (interior == null) { player.sendSystemMessage(Component.literal("The interior dimension failed to load.")); return; }
        TardisBlockEntity ext = from.getBlockEntity(exteriorPos) instanceof TardisBlockEntity e ? e : null;
        int id = ext != null && ext.interiorId() >= 0 ? ext.interiorId() : allocate(interior);
        if (ext != null && ext.interiorId() != id) ext.setInterior(id);
        build(interior, id);
        if (interior.getBlockEntity(doorPos(id)) instanceof TardisBlockEntity door) { door.setReturn(from.dimension().identifier().toString(), exteriorPos); door.setOpen(true); }
        BlockPos c = center(id);
        player.teleportTo(interior, c.getX() + 0.5, c.getY() + 1, c.getZ() + 7.5, Set.of(), 180f, 0f, true); // just inside the exit door, facing the console
    }

    public static void leave(ServerPlayer player, TardisBlockEntity interiorDoor) {
        var server = player.level().getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(interiorDoor.returnDimension().isEmpty() ? "minecraft:overworld" : interiorDoor.returnDimension()));
        ServerLevel target = server.getLevel(key); if (target == null) target = server.overworld();
        BlockPos p = interiorDoor.returnDimension().isEmpty() ? target.getRespawnData().pos() : interiorDoor.returnPos();
        BlockState st = target.getBlockState(p); Direction f = st.hasProperty(HorizontalDirectionalBlock.FACING) ? st.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;
        BlockPos out = p.relative(f, 2);
        player.teleportTo(target, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, Set.of(), f.toYRot(), 0f, true);
    }

    /** Builds one console room (idempotent): floor, walls, lit ceiling, glass time-rotor column, four stations, exit door. */
    public static void build(ServerLevel level, int id) {
        BlockPos door = doorPos(id);
        if (level.getBlockState(door).getBlock() == TardisMod.TARDIS_BLOCK) return;
        BlockPos c = center(id);
        BlockState floorA = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), floorB = Blocks.DEEPSLATE_TILES.defaultBlockState(), wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState(), trim = Blocks.GOLD_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            boolean edge = Math.abs(x) == R || Math.abs(z) == R;
            level.setBlock(c.offset(x, 0, z), ((x + z) & 1) == 0 ? floorA : floorB, 3);
            if (Math.abs(Math.hypot(x, z) - 6) < 0.6) level.setBlock(c.offset(x, 0, z), trim, 3); // ring inlay
            for (int y = 1; y <= H; y++) level.setBlock(c.offset(x, y, z), edge ? wall : air, 3);
            level.setBlock(c.offset(x, H + 1, z), (x % 4 == 0 && z % 4 == 0) ? lamp : wall, 3);
        }
        for (int y = 1; y <= 9; y++) level.setBlock(c.offset(0, y, 0), y % 4 == 0 ? lamp : Blocks.GLASS.defaultBlockState(), 3); // time rotor
        level.setBlock(c.offset(0, 1, 0), floorA, 3);
        for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2 + Math.PI / 4; level.setBlock(c.offset((int) Math.round(Math.cos(a) * 3), 1, (int) Math.round(Math.sin(a) * 3)), TardisMod.CONSOLE_BLOCK.defaultBlockState().setValue(com.tardismod.block.ConsoleBlock.STATION, i), 3); }
        level.setBlock(door, TardisMod.TARDIS_BLOCK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
    }
}
