package dev.mysticarts.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An item with a line of flavour text: {@code item.mysticarts.<key>.lore}. */
public class LoreItem extends Item {
    private final String key;

    public LoreItem(Properties properties, String key) {
        super(properties);
        this.key = key;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mysticarts." + key + ".lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
