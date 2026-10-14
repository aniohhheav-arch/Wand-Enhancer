package com.stickyfingers.world;

import com.stickyfingers.ability.StandManager;
import com.stickyfingers.data.StandData;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Pierce yourself to awaken Sticky Fingers. */
public class StandArrowItem extends Item {
    public StandArrowItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            StandData data = StandData.get(sp);
            if (data.hasStand()) {
                sp.displayClientMessage(Component.translatable("message.stickyfingers.already_awakened").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                return InteractionResultHolder.fail(stack);
            }
            data.setHasStand(true);
            level.playSound(null, sp.blockPosition(), ModSounds.STAND_SUMMON.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
            sp.displayClientMessage(Component.translatable("message.stickyfingers.awakened").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            StandManager.summon(sp);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stickyfingers.stand_arrow.desc").withStyle(ChatFormatting.GRAY));
    }
}
