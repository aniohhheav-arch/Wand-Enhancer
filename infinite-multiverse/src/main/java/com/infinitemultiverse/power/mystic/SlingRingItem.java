package com.infinitemultiverse.power.mystic;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Sneak-use to bind the ring to where you stand; the Sling Ring Portal spell then opens gateways there. */
public final class SlingRingItem extends Item {
    public record Destination(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public SlingRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", level.dimension().location().toString());
            tag.putLong("pos", player.blockPosition().asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.displayClientMessage(Component.translatable("message.infinitemultiverse.sling_ring_bound",
                    player.blockPosition().toShortString(), level.dimension().location().toString()).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static Optional<Destination> destination(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null || !tag.contains("pos")) {
            return Optional.empty();
        }
        return Optional.of(new Destination(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLong("pos"))));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.sling_ring.tooltip").withStyle(ChatFormatting.GRAY));
        destination(stack).ifPresent(d -> tooltip.add(Component.translatable("item.infinitemultiverse.sling_ring.bound",
                d.pos().toShortString(), d.dimension().location().toString()).withStyle(ChatFormatting.GOLD)));
    }
}
