package dev.riftverse.item;

import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Ignites a rectangle of Rift Frame blocks into a stable portal (to the Nexus, or to an off-hand key's universe). */
public class RiftIgniterItem extends Item {
    private static final int MAX_CELLS = 21 * 21;

    public RiftIgniterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!level.getBlockState(pos).is(RvBlocks.RIFT_FRAME.get())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        Destination dest = Destination.nexus();
        String name = "The Multiverse Nexus";
        int color = 0xFFC14D;
        if (player != null && player.getOffhandItem().is(RvItems.DIMENSIONAL_KEY.get())) {
            Destination keyed = DimensionalKeyItem.destination(player.getOffhandItem());
            if (keyed != null) {
                dest = keyed;
                name = ItemData.read(player.getOffhandItem()).getString("name");
                color = DimensionalKeyItem.color(player.getOffhandItem());
            }
        }
        if (ignite(level, pos, dest, name, color)) {
            level.playSound(null, pos, RvSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 1.4f, 0.8f);
            if (player != null) ctx.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(ctx.getHand()));
            return InteractionResult.CONSUME;
        }
        if (player != null) player.displayClientMessage(Component.translatable("message.riftverse.invalid_frame"), true);
        return InteractionResult.FAIL;
    }

    /** Detects an empty rectangular frame next to {@code framePos} and fills it with portal membrane. */
    public static boolean ignite(Level level, BlockPos framePos, Destination dest, String label, int color) {
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            Direction[] plane = axis == Direction.Axis.X
                    ? new Direction[] {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}
                    : new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
            for (Direction d : plane) {
                BlockPos start = framePos.relative(d);
                if (!isOpen(level.getBlockState(start))) continue;
                Set<BlockPos> region = flood(level, start, plane);
                if (region == null) continue;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (BlockPos p : region) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                    maxZ = Math.max(maxZ, p.getZ());
                }
                int w = axis == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
                int h = maxY - minY + 1;
                if (w < 2 || h < 3 || w * h != region.size()) continue;
                BlockPos min = new BlockPos(minX, minY, minZ);
                PortalFieldBlockEntity.fill(level, min, axis, w, h, dest, label, color);
                if (level instanceof ServerLevel server) {
                    for (BlockPos p : region) {
                        if (server.random.nextInt(3) == 0) server.sendParticles(RvParticles.SPARK.get().with(color, 0.4f, 16), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.05);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isOpen(BlockState s) {
        return s.isAir() || s.is(RvBlocks.PORTAL_FIELD.get());
    }

    private static Set<BlockPos> flood(Level level, BlockPos start, Direction[] plane) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : plane) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(RvBlocks.RIFT_FRAME.get())) continue;
                if (!isOpen(s)) return null;
                seen.add(n);
                if (seen.size() > MAX_CELLS) return null;
                queue.add(n);
            }
        }
        return seen;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.rift_igniter.tip").withStyle(ChatFormatting.GRAY));
    }
}
