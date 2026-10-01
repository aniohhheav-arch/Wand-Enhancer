package dev.riftverse.item;

import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sneak-use on the ground to birth a permanent black hole high above it - a gateway to other universes. */
public class SingularityCoreItem extends Item {
    public SingularityCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            if (player != null && !ctx.getLevel().isClientSide) player.displayClientMessage(Component.translatable("message.riftverse.core_hint"), true);
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        if (ctx.getLevel() instanceof ServerLevel server) {
            Vec3 at = Vec3.atCenterOf(ctx.getClickedPos()).add(0, 22, 0);
            BlackHoleEntity hole = new BlackHoleEntity(RvEntities.BLACK_HOLE.get(), server);
            hole.moveTo(at.x, at.y, at.z, 0, 0);
            hole.setHorizonRadius(3.2f);
            hole.setNatural(true);
            server.addFreshEntity(hole);
            server.playSound(null, at.x, at.y, at.z, RvSounds.BLACK_HOLE_COLLAPSE.get(), SoundSource.PLAYERS, 3.0f, 0.6f);
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(at) < 96 * 96) PacketDistributor.sendToPlayer(p, new Payloads.Shake(0.9f, 40, 0.8f, 0xE0D0FF));
            }
            ctx.getItemInHand().consume(1, player);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.singularity_core.tip1").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.riftverse.singularity_core.tip2").withStyle(ChatFormatting.RED));
    }
}
