package com.wandcraft.magic.spells;

import com.wandcraft.magic.util.Aim;
import com.wandcraft.magic.util.TempBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Helpers for elemental terrain interaction. */
final class Elem {
    private Elem() {}

    /** Briefly scorch the ground with harmless flames: only on solid, non-flammable floors with no flammable neighbours. */
    static void scorch(ServerLevel l, Vec3 center, double radius, int count, ServerPlayer owner, int ticks) {
        for (int i = 0; i < count; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = Math.sqrt(l.random.nextDouble()) * radius;
            BlockPos probe = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 1, center.z + Math.sin(a) * r);
            Aim.stepGround(l, probe.below()).ifPresent(pos -> {
                if (!BaseFireBlock.canBePlacedAt(l, pos, Direction.UP) || flammableAround(l, pos)) return;
                if (l.getBlockState(pos.below()).isFlammable(l, pos.below(), Direction.UP)) return;
                TempBlocks.place(l, pos, Blocks.FIRE.defaultBlockState(), ticks, 1, owner);
            });
        }
    }

    static boolean flammableAround(ServerLevel l, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (l.getBlockState(pos.relative(d)).isFlammable(l, pos.relative(d), d.getOpposite())) return true;
        }
        return false;
    }
}
