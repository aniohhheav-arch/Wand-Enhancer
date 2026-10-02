package com.infinitemultiverse.cosmic.gauntlet;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
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
 * The Infinity Gauntlet. Hold it: scroll to pick a stone, sneak + scroll to pick that stone's technique, right-click
 * to unleash it. Right-click with a stone in your other hand to set the stone into the gauntlet.
 */
public final class InfinityGauntletItem extends Item {
    public InfinityGauntletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gauntlet = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (other.getItem() instanceof InfinityStoneItem stoneItem) {
            if (!level.isClientSide && !Gauntlet.has(gauntlet, stoneItem.stone())) {
                Gauntlet.setStones(gauntlet, Gauntlet.stones(gauntlet) | stoneItem.stone().bit());
                other.shrink(1);
                ServerPlayer sp = (ServerPlayer) player;
                Cinematics.scene(sp.serverLevel(), SceneIds.STONE_SET, player.position(), player.getLookAngle(), stoneItem.stone().color(), 30, player, 1f);
                MultiverseVfx.sound(sp.serverLevel(), player.position(), ModSounds.STAND_AWAKEN, 1f, 1.4f);
                player.displayClientMessage(Component.translatable("message.infinitemultiverse.stone_set", stoneItem.stone().displayName()
                        .copy().withStyle(s -> s.withColor(stoneItem.stone().color()))), true);
            }
            return InteractionResultHolder.sidedSuccess(gauntlet, level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(gauntlet);
        }
        Ability ability = Gauntlet.selected(gauntlet);
        if (ability == null) {
            AbilityManager.deny((ServerPlayer) player, Component.translatable("message.infinitemultiverse.gauntlet_empty"));
            return InteractionResultHolder.fail(gauntlet);
        }
        AbilityManager.tryActivate((ServerPlayer) player, ability);
        return InteractionResultHolder.consume(gauntlet);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (InfinityStone stone : InfinityStone.values()) {
            boolean set = Gauntlet.has(stack, stone);
            tooltip.add(Component.literal(set ? "◆ " : "◇ ").append(stone.displayName())
                    .withStyle(s -> set ? s.withColor(stone.color()) : s.withColor(ChatFormatting.DARK_GRAY)));
        }
        tooltip.add(Component.translatable("item.infinitemultiverse.infinity_gauntlet.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Gauntlet.count(stack) == 6;
    }
}
