package dev.riftverse.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Armour forged from dimensional materials. Wearing a full set unlocks passives and an ability (default key: V). */
public class RiftArmorItem extends ArmorItem {
    private ArmorSet set;

    public RiftArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    public ArmorSet set() {
        if (set == null) set = ArmorSet.of(getMaterial());
        return set;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ArmorSet s = set();
        tooltip.add(Component.translatable("armorset.riftverse." + s.id).withStyle(st -> st.withColor(TextColor.fromRgb(s.primary))));
        tooltip.add(Component.translatable("armorset.riftverse." + s.id + ".passive").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("armorset.riftverse." + s.id + ".ability").withStyle(ChatFormatting.DARK_AQUA));
        if (s == ArmorSet.VOYAGER && getType() == Type.HELMET) {
            tooltip.add(Component.translatable("armorset.riftverse.voyager.helmet").withStyle(ChatFormatting.AQUA));
        }
    }
}
