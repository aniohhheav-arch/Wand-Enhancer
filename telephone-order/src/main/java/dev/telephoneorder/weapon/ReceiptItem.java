package dev.telephoneorder.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Collectable record of a previous order. */
public class ReceiptItem extends Item {
    public ReceiptItem(Item.Properties p) { super(p); }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) { out.accept(Component.literal("A blank receipt.").withStyle(ChatFormatting.GRAY)); return; }
        CompoundTag t = d.copyTag();
        out.accept(Component.literal("ORDER #" + t.getIntOr("oid", 0) + " - " + t.getStringOr("name", "?")).withStyle(ChatFormatting.GOLD));
        out.accept(Component.literal("Buyer: " + t.getStringOr("buyer", "?")).withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Day " + t.getIntOr("day", 0) + "  •  via " + t.getStringOr("method", "?") + "  •  " + t.getIntOr("price", 0) + " cr").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Thank you for choosing Telephone Order.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public Component getName(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d != null) {
            CompoundTag t = d.copyTag();
            if (t.contains("name")) return Component.literal("Receipt: " + t.getStringOr("name", "?"));
        }
        return super.getName(stack);
    }
}
