package dev.infinitygauntlet.power;

import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.network.CinematicPayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.network.PowerPosePayload;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side presentation helpers shared by the Power Stone abilities. */
public final class PowerFx {
    public static final int PURPLE = InfinityStone.POWER.color();
    public static final int PINK = InfinityStone.POWER.highlight();
    public static final int VOID = 0x2A0848;
    public static final int WHITE_HOT = 0xFFF2FF;
    public static final int MAGENTA = 0xFF3FD2;

    /** The caster strikes {@code pose} for {@code ticks}; everyone nearby sees it. */
    public static void pose(ServerPlayer player, PowerPose pose, int ticks) {
        ModNetwork.sendNear(player.serverLevel(), player.position(), 96, new PowerPosePayload(player.getId(), pose.ordinal(), ticks));
    }

    /** Plays one of the gauntlet's GeckoLib power animations. */
    public static void anim(AbilityContext ctx, String animation) {
        if (ctx.gauntlet().getItem() instanceof InfinityGauntletItem gauntlet) {
            gauntlet.playAnimation(ctx.player(), ctx.gauntlet(), animation);
        }
    }

    /**
     * A camera shot for the caster: from {@code focus + from} to {@code focus + to}, always looking at the focus
     * (which tracks {@code focusEntity} when given). Clients can opt out in their config.
     */
    public static void cinematic(ServerPlayer player, @Nullable Entity focusEntity, Vec3 focus, Vec3 from, Vec3 to, int ticks, float roll, float fovMul) {
        ModNetwork.sendTo(player, new CinematicPayload(focusEntity == null ? -1 : focusEntity.getId(), focus, from, to, ticks, roll, fovMul));
    }

    /** An offset in the caster's horizontal frame: right, up and back from where they face. */
    public static Vec3 rel(Entity caster, double right, double up, double back) {
        float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        return side.scale(right).add(0, up, 0).subtract(fwd.scale(back));
    }

    public static void cracks(ServerLevel level, Vec3 center, float radius, int ticks, int color) {
        Vfx.send(level, VfxType.GROUND_CRACKS, center, Vec3.ZERO, color, PINK, radius, ticks);
    }

    /** Chunks of the ground under {@code center} blown into the air (visual only). */
    public static void debris(ServerLevel level, Vec3 center, float power, int count) {
        BlockState state = ground(level, center);
        Vfx.send(level, VfxType.DEBRIS, center, Vec3.ZERO, Block.getId(state), count, power, 60);
    }

    public static BlockState ground(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(pos.below(i));
            if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender(level, pos.below(i))) {
                return state;
            }
        }
        return Blocks.DIRT.defaultBlockState();
    }

    /**
     * Blasts a shallow bowl into the terrain when the server allows it (config and mobGriefing). Hard and
     * block-entity blocks are spared.
     */
    public static void crater(ServerLevel level, Vec3 center, float radius) {
        if (!ServerConfig.POWER_TERRAIN_DAMAGE.get() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        BlockPos c = BlockPos.containing(center);
        int r = Mth.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, 1, r))) {
            double dx = pos.getX() + 0.5 - center.x, dz = pos.getZ() + 0.5 - center.z;
            double depth = radius * 0.55 * (1 - (dx * dx + dz * dz) / (radius * radius));
            if (depth <= 0 || pos.getY() + 0.5 < center.y - depth) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0 || hardness > 3.5f || state.hasBlockEntity()) {
                continue;
            }
            level.destroyBlock(pos, level.random.nextFloat() < 0.15f);
        }
    }

    private PowerFx() {
    }
}
