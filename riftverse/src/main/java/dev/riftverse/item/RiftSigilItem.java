package dev.riftverse.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Calls the Rift Warden when offered at the Rift Altar in the Nexus. */
public class RiftSigilItem extends Item {
    public RiftSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.rift_sigil.tip1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.riftverse.rift_sigil.tip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
