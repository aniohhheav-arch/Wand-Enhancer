package com.tardismod;

import com.tardismod.block.TardisBlock;
import com.tardismod.block.TardisBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

/** The bigger-on-the-inside dimension (first slice: one shared console room). Entering/leaving teleports between dimensions. */
public final class Interior {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, TardisMod.id("interior"));
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos DOOR = new BlockPos(0, 65, 9);
    private static final int R = 12, H = 12;

    private Interior() {}

    public static void enter(ServerPlayer player, ServerLevel from, BlockPos exteriorPos) {
        ServerLevel interior = player.level().getServer().getLevel(KEY);
        if (interior == null) { player.sendSystemMessage(net.minecraft.network.chat.Component.literal("The interior dimension failed to load.")); return; }
        build(interior);
        if (interior.getBlockEntity(DOOR) instanceof TardisBlockEntity door) { door.setReturn(from.dimension().identifier().toString(), exteriorPos); door.setOpen(true); }
        player.teleportTo(interior, 0.5, CENTER.getY() + 1, 4.5, Set.of(), 180f, 0f, true);
    }

    public static void leave(ServerPlayer player, TardisBlockEntity interiorDoor) {
        var server = player.level().getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse(interiorDoor.returnDimension().isEmpty() ? "minecraft:overworld" : interiorDoor.returnDimension()));
        ServerLevel target = server.getLevel(key); if (target == null) target = server.overworld();
        BlockPos p = interiorDoor.returnDimension().isEmpty() ? target.getRespawnData().pos() : interiorDoor.returnPos();
        BlockState st = target.getBlockState(p); Direction f = st.hasProperty(HorizontalDirectionalBlock.FACING) ? st.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;
        BlockPos out = p.relative(f, 2);
        player.teleportTo(target, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, Set.of(), f.toYRot(), 0f, true);
    }

    /** Builds the console room once (idempotent): floor, walls, lit ceiling, a glass time-rotor column and the exit door. */
    public static void build(ServerLevel level) {
        if (level.getBlockState(DOOR).getBlock() == TardisMod.TARDIS_BLOCK) return;
        BlockState floorA = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), floorB = Blocks.DEEPSLATE_TILES.defaultBlockState(), wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState(), trim = Blocks.GOLD_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int y0 = CENTER.getY();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            boolean edge = Math.abs(x) == R || Math.abs(z) == R;
            level.setBlock(new BlockPos(x, y0, z), ((x + z) & 1) == 0 ? floorA : floorB, 3);
            if (((x * x + z * z) < 100) && Math.abs(Math.hypot(x, z) - 6) < 0.6) level.setBlock(new BlockPos(x, y0, z), trim, 3); // ring inlay
            for (int y = 1; y <= H; y++) level.setBlock(new BlockPos(x, y0 + y, z), edge ? wall : air, 3);
            level.setBlock(new BlockPos(x, y0 + H + 1, z), (x % 4 == 0 && z % 4 == 0) ? lamp : wall, 3);
        }
        for (int y = 1; y <= 9; y++) level.setBlock(CENTER.offset(0, y, 0), y % 4 == 0 ? lamp : Blocks.GLASS.defaultBlockState(), 3); // time rotor
        level.setBlock(CENTER.offset(0, 1, 0), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3; level.setBlock(CENTER.offset((int) Math.round(Math.cos(a) * 2), 1, (int) Math.round(Math.sin(a) * 2)), Blocks.LECTERN.defaultBlockState(), 3); } // console stations
        level.setBlock(DOOR, TardisMod.TARDIS_BLOCK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        level.setBlock(new BlockPos(0, y0 + 1, -R + 1), Blocks.AIR.defaultBlockState(), 3);
    }
}
