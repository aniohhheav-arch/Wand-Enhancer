package dev.mysticarts.world;

import dev.mysticarts.MysticArts;
import dev.mysticarts.block.RelicPedestalBlockEntity;
import dev.mysticarts.registry.MaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small block-placing vocabulary shared by every mystic structure. */
public final class Build {
    public final WorldGenLevel level;
    public final RandomSource random;
    public final BlockPos origin;

    public Build(WorldGenLevel level, RandomSource random, BlockPos origin) {
        this.level = level;
        this.random = random;
        this.origin = origin;
    }

    public BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public void set(int x, int y, int z, BlockState s) {
        level.setBlock(at(x, y, z), s, 2);
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(at(x, y, z));
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    /** Fills with a weathered mix: each block has {@code decay} chance to be {@code worn} and {@code gap} chance to be skipped. */
    public void ruin(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s, BlockState worn, float decay, float gap) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    float r = random.nextFloat();
                    if (r < gap * (1 + (y - y0) * 0.15f)) continue;
                    set(x, y, z, r < decay + gap ? worn : s);
                }
    }

    public void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        fill(x0, y0, z0, x1, y1, z0, s);
        fill(x0, y0, z1, x1, y1, z1, s);
        fill(x0, y0, z0, x0, y1, z1, s);
        fill(x1, y0, z0, x1, y1, z1, s);
    }

    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
    }

    /** Extends the floor down to solid ground so nothing floats. */
    public void foundation(int x0, int z0, int x1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = -1; y > -24; y--) {
                    BlockState here = get(x, y, z);
                    if (here.isSolid() && here.getFluidState().isEmpty()) break;
                    set(x, y, z, s);
                }
            }
        }
    }

    public void disc(int cx, int y, int cz, double r, BlockState s) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) if (x * x + z * z <= r * r) set(cx + x, y, cz + z, s);
    }

    public void ring(int cx, int y, int cz, double r, BlockState s) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++)
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r && d > r - 1) set(cx + x, y, cz + z, s);
            }
    }

    /** A circular window in the x = {@code x} plane (the Seal of the Vishanti, rose windows). */
    public void roundWindow(int x, int cy, int cz, double r, BlockState frame, BlockState glass) {
        int ri = (int) Math.ceil(r);
        for (int y = -ri; y <= ri; y++)
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(y * y + z * z);
                if (d <= r) set(x, cy + y, cz + z, d > r - 1 ? frame : glass);
            }
    }

    public void chest(int x, int y, int z, Direction facing, String lootTable) {
        BlockPos p = at(x, y, z);
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, p, ResourceKey.create(Registries.LOOT_TABLE, MysticArts.id("chests/" + lootTable)));
    }

    public void pedestal(int x, int y, int z, ItemStack relic, boolean guarded) {
        BlockPos p = at(x, y, z);
        level.setBlock(p, MaBlocks.RELIC_PEDESTAL.get().defaultBlockState(), 2);
        if (level.getBlockEntity(p) instanceof RelicPedestalBlockEntity be) be.setRelic(relic, guarded);
    }

    public static int groundY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
    }
}
