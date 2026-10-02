package com.infinitemultiverse.cosmic.gauntlet;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class InfinityStoneItem extends Item {
    private final InfinityStone stone;

    public InfinityStoneItem(Properties properties, InfinityStone stone) {
        super(properties);
        this.stone = stone;
    }

    public InfinityStone stone() {
        return stone;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.infinity_stone.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
