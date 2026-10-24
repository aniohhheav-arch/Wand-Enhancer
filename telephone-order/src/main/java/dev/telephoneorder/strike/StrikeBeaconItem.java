package dev.telephoneorder.strike;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

/** A one-use targeting beacon: look at a distant target and right-click to call the strike. */
public class StrikeBeaconItem extends Item {
    public final String strikeId;

    public StrikeBeaconItem(Item.Properties p, String id) { super(p); this.strikeId = id; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        HitResult hit = sp.pick(160.0, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            sp.sendOverlayMessage(Component.literal("§cNo target in range: aim at the ground or a block within 160 blocks."));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (StrikeDirector.launch(sp, strikeId, hit.getLocation()) && !sp.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Aim at a distant target and right-click.").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Single use. Danger close is denied. Long cooldown.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
