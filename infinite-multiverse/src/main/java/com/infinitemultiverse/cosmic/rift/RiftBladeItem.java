package com.infinitemultiverse.cosmic.rift;

import com.infinitemultiverse.core.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A blade sharp enough to cut space: right-click tears a rift open a few blocks ahead (30 s cooldown). */
public final class RiftBladeItem extends SwordItem {
    public RiftBladeItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.4f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            RiftEntity rift = ModEntities.RIFT.get().create(server);
            if (rift != null) {
                Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(5));
                rift.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
                rift.setLifetime(400);
                server.addFreshEntity(rift);
                player.getCooldowns().addCooldown(this, 600);
                stack.hurtAndBreak(4, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.rift_blade.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
