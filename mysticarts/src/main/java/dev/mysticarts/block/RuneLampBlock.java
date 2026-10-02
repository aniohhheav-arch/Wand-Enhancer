package dev.mysticarts.block;

import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A floating-rune lantern: full light, drifting glyphs and a faint sanctum hum. */
public class RuneLampBlock extends Block {
    private final int color;

    public RuneLampBlock(int color, Properties properties) {
        super(properties);
        this.color = color;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(MaParticles.RUNE.get().with(color, 0.45f, 50), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2,
                    pos.getY() + 1.0 + random.nextDouble() * 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2, 0, 0.015, 0);
        }
        if (random.nextInt(200) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, MaSounds.SANCTUM_AMBIENT.get(), SoundSource.AMBIENT,
                    0.5f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }
}
