package dev.telephoneorder.weapon;

import dev.telephoneorder.phone.PhoneService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A pocket telephone: right-click anywhere to dial the weapons corporation. */
public class HandheldPhoneItem extends Item {
    public HandheldPhoneItem(Item.Properties p) { super(p); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) PhoneService.openHandheld(sp, sl);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Right-click to call Telephone Order from anywhere.").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Keep it in a hand while the call is open.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
