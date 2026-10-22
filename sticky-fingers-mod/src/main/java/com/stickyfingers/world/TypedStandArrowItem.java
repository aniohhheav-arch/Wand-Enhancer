package com.stickyfingers.world;

import com.stickyfingers.ability.StandManager;
import com.stickyfingers.ability.StandType;
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

/** Arrow that grants or switches to a specific Stand type. */
public class TypedStandArrowItem extends Item {
    private final StandType type;

    public TypedStandArrowItem(StandType type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            StandData data = StandData.get(sp);
            if (data.hasStand() && data.standType() == type) {
                sp.displayClientMessage(Component.translatable("message.stickyfingers.already_awakened").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                return InteractionResultHolder.fail(stack);
            }
            // dismiss old stand if present
            if (data.isSummoned()) StandManager.dismiss(sp);
            data.setHasStand(true);
            data.setStandType(type);
            var sound = switch (type) {
                case STAR_PLATINUM -> ModSounds.SP_SUMMON.get();
                case D4C -> ModSounds.D4C_SUMMON.get();
                default -> ModSounds.STAND_SUMMON.get();
            };
            level.playSound(null, sp.blockPosition(), sound, SoundSource.PLAYERS, 1.2f, 0.8f);
            sp.displayClientMessage(Component.translatable("message.stickyfingers.awakened_stand", type.standName)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            StandManager.summon(sp);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stickyfingers.stand_arrow.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(type.standName).withStyle(ChatFormatting.GOLD));
    }
}
