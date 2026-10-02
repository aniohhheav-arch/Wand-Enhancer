package dev.riftverse.item;

import dev.riftverse.creator.RuptureService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * THE REALITY RUPTURE. Unobtainable in survival; materialised only through the creator's hidden interface. Every copy
 * carries a registered serial; in the hands of anyone not authorised (or as an unregistered copy) it crumbles.
 * Use: Reality Tear. Sneak-use: Existence Disassembly. Sneak-use looking straight up: THE FINAL RUPTURE.
 */
public class RealityRuptureItem extends Item {
    public RealityRuptureItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) RuptureService.use(sp, stack);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("THE REALITY RUPTURE").withColor(0xC080FF);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("An artifact that should not exist.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Use: Reality Tear").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak-use: Existence Disassembly").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak-use, looking up: THE FINAL RUPTURE").withStyle(ChatFormatting.GRAY));
    }
}
