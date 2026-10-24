package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog.AmmoDef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class AmmoItem extends Item {
    public final AmmoDef def;
    public AmmoItem(Item.Properties p, AmmoDef def) { super(p); this.def = def; }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(def.per() + " rounds per item").withStyle(ChatFormatting.GRAY));
        if (def.special()) {
            out.accept(Component.literal(def.desc()).withStyle(ChatFormatting.GOLD));
            out.accept(Component.literal("Hold in the OFF-HAND and reload to load these.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            out.accept(Component.literal("Kept in your inventory; guns reload from it automatically.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
