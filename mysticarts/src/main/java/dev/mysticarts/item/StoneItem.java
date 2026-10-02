package dev.mysticarts.item;

import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** One of the six Infinity Stones. Use it while holding the Infinity Gauntlet in your other hand to socket it. */
public class StoneItem extends Item {
    private final Source stone;

    public StoneItem(Source stone, Properties properties) {
        super(properties);
        this.stone = stone;
    }

    public Source stone() {
        return stone;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(other.getItem() instanceof InfinityGauntletItem)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.mysticarts.socket_hint"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (InfinityGauntletItem.has(other, stone)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.mysticarts.already_socketed"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            InfinityGauntletItem.socket(other, stone);
            stack.shrink(1);
            level.playSound(null, player.blockPosition(), MaSounds.STONE_SOCKET.get(), SoundSource.PLAYERS, 1.2f, 1f);
            player.displayClientMessage(Component.translatable("message.mysticarts.socketed", getDescription()).withColor(stone.color), true);
            if (InfinityGauntletItem.stones(other) == Source.ALL_STONES) {
                player.displayClientMessage(Component.translatable("message.mysticarts.all_stones").withColor(0xFFE27A), false);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".lore").withColor(stone.color));
        tooltip.add(Component.translatable("tooltip.mysticarts.socket").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
