package dev.mysticarts.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Mystic vestments. Each full set raises maximum mystic energy and has a passive (see {@link Vestments}). */
public class SorcererArmorItem extends ArmorItem {
    private final Vestments set;

    public SorcererArmorItem(Vestments set, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.set = set;
    }

    public Vestments set() {
        return set;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vestments.mysticarts." + set.id).withColor(set.color));
        tooltip.add(Component.translatable("vestments.mysticarts." + set.id + ".passive").withStyle(ChatFormatting.GRAY));
    }
}
