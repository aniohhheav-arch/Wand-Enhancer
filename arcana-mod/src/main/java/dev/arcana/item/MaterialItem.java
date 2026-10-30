package dev.arcana.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Crafting/evolution/customisation material with an optional lore line ({@code item.arcana.<id>.lore}). */
public class MaterialItem extends Item {
    private final boolean foil;
    private final String loreKey;

    public MaterialItem(Properties props, String id, boolean foil) {
        super(props);
        this.foil = foil;
        this.loreKey = "item.arcana." + id + ".lore";
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
