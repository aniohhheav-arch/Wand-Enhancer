package dev.riftverse.entity.ai;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.multiverse.MigrationManager;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Optional cross-dimensional curiosity: an idle creature occasionally notices a nearby rift and wanders into it. The
 * chance is configurable (riftSeekingPerMille) so ordinary mobs do not constantly vanish; unstable return rifts are
 * avoided, and creatures that just arrived somewhere are left alone by the migration cooldown.
 */
public class RiftSeekGoal extends Goal {
    private final PathfinderMob mob;
    @Nullable
    private BlockPos rift;
    private int timeout;

    public RiftSeekGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || mob.tickCount % 200 != mob.getId() % 200) return false;
        if (!(mob.level() instanceof ServerLevel level) || !MigrationManager.enabled(level) || !MigrationManager.canMigrate(mob)) return false;
        int chance = RiftverseConfig.get(RiftverseConfig.RIFT_SEEKING_CHANCE, 15);
        if (chance <= 0 || mob.getRandom().nextInt(1000) >= chance) return false;
        rift = nearestRift(level);
        return rift != null;
    }

    @Nullable
    private BlockPos nearestRift(ServerLevel level) {
        BlockPos best = null;
        double bd = 16 * 16;
        int cx = mob.blockPosition().getX() >> 4;
        int cz = mob.blockPosition().getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof RiftBlockEntity r) || r.type() == RiftType.RETURN) continue;
                    double d = be.getBlockPos().distSqr(mob.blockPosition());
                    if (d < bd) {
                        bd = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void start() {
        timeout = 300;
        if (rift != null) mob.getNavigation().moveTo(rift.getX() + 0.5, rift.getY() - 1, rift.getZ() + 0.5, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return rift != null && --timeout > 0 && mob.getTarget() == null && !mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        rift = null;
    }
}
