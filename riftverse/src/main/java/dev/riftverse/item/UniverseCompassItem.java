package dev.riftverse.item;

import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.event.CommonEvents;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.UniverseSpec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Senses the nearest tear in reality and reports the identity of the universe you stand in. */
public class UniverseCompassItem extends Item {
    private static final int CHUNK_RADIUS = 8;

    public UniverseCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        UniverseSpec spec = CommonEvents.specOf(player);
        if (spec != null) player.sendSystemMessage(Component.literal(spec.name + "  [" + spec.id.designation() + "]").withStyle(ChatFormatting.LIGHT_PURPLE));
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof RiftBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(player.blockPosition());
                    if (d < bestD) {
                        bestD = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("message.riftverse.compass_none"), true);
        } else {
            Vec3 to = Vec3.atCenterOf(best).subtract(player.getEyePosition());
            String dir = cardinal(to);
            player.displayClientMessage(Component.translatable("message.riftverse.compass_found", (int) Math.sqrt(bestD), dir), true);
            Vec3 step = to.normalize();
            Vec3 eye = player.getEyePosition();
            for (int i = 1; i <= 14; i++) {
                Vec3 p = eye.add(step.scale(i * 0.8));
                server.sendParticles((net.minecraft.server.level.ServerPlayer) player, RvParticles.SPARK.get().with(0xB070FF, 0.3f, 30), true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        server.playSound(null, player.blockPosition(), RvSounds.UI_SELECT.get(), SoundSource.PLAYERS, 0.6f, 1.6f);
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(stack);
    }

    private static String cardinal(Vec3 v) {
        double angle = Math.toDegrees(Math.atan2(v.x, -v.z));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        String vertical = v.y > 6 ? " ↑" : v.y < -6 ? " ↓" : "";
        return names[idx] + vertical;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.universe_compass.tip").withStyle(ChatFormatting.GRAY));
    }
}
