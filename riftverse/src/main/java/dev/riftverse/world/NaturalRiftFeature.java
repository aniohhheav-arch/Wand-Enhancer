package dev.riftverse.world;

import com.mojang.serialization.Codec;
import dev.riftverse.RiftverseConfig;
import dev.riftverse.block.RiftType;
import dev.riftverse.registry.RvBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A tear in the Overworld: the player's first glimpse of what lies beyond. */
public class NaturalRiftFeature extends Feature<NoneFeatureConfiguration> {
    public NaturalRiftFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static int rarity() {
        try {
            return RiftverseConfig.NATURAL_RIFT_RARITY.get();
        } catch (IllegalStateException e) {
            return 90;
        }
    }

    public static RiftType overworldType(RandomSource r) {
        int roll = r.nextInt(100);
        if (roll < 22) return RiftType.AZURE;
        if (roll < 31) return RiftType.VERDANT;
        if (roll < 38) return RiftType.CRIMSON;
        if (roll < 44) return RiftType.PRISMATIC;
        if (roll < 49) return RiftType.VOID;
        if (roll < 54) return RiftType.STELLAR;
        if (roll < 58) return RiftType.GLITCH;
        if (roll < 62) return RiftType.NEXUS;
        // expansion rifts: each leads to its own family of new realities
        RiftType[] extra = {RiftType.SOLAR, RiftType.ABYSSAL, RiftType.FUNGAL, RiftType.SANGUINE, RiftType.BRASS,
                RiftType.SACCHARINE, RiftType.TEMPEST, RiftType.UMBRAL, RiftType.PATINA, RiftType.AURORAL, RiftType.MOLTEN, RiftType.PRIMAL,
                RiftType.CHROME, RiftType.SCULK, RiftType.NEBULAR};
        return extra[r.nextInt(extra.length)];
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        RandomSource r = ctx.random();
        if (r.nextInt(rarity()) != 0) return false;
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        if (y <= level.getMinBuildHeight() + 2) return false;
        BlockPos ground = new BlockPos(origin.getX(), y, origin.getZ());
        if (!level.getBlockState(ground).getFluidState().isEmpty()) return false;
        RiftType type = overworldType(r);
        BlockPos riftPos = ground.above(2 + r.nextInt(2));
        Decorator.naturalRift(level, r, riftPos, type, MaterialSet.of(dev.riftverse.universe.Archetype.PRISMATIC));
        BlockState scorch = RvBlocks.VOID_STONE.get().defaultBlockState();
        for (int i = 0; i < 18; i++) {
            BlockPos p = ground.offset(r.nextInt(9) - 4, -1, r.nextInt(9) - 4);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                BlockState s = level.getBlockState(q);
                if (s.isSolid() && level.getBlockState(q.above()).isAir() && !s.is(Blocks.BEDROCK)) {
                    level.setBlock(q, r.nextInt(4) == 0 ? RvBlocks.RIFT_CRYSTAL.get().defaultBlockState() : scorch, 2);
                    break;
                }
            }
        }
        return true;
    }
}
