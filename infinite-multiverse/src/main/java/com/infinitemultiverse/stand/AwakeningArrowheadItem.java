package com.infinitemultiverse.stand;

import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
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
import net.minecraft.world.phys.Vec3;

/** Pierces its user to awaken a random Stand. Consumed on success; harmless if it refuses. */
public final class AwakeningArrowheadItem extends Item {
    private static final float PIERCE_DAMAGE = 2f;

    public AwakeningArrowheadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        StandType current = StandManager.standTypeOf(serverPlayer);
        if (current != null && !MultiverseConfig.SERVER.allowStandReroll.get()) {
            AbilityManager.deny(serverPlayer, Component.translatable("message.infinitemultiverse.already_have_stand", current.displayName()));
            return InteractionResultHolder.fail(stack);
        }
        List<StandType> candidates = MultiverseRegistries.STAND_TYPES.stream().filter(type -> type != current).toList();
        if (candidates.isEmpty()) {
            AbilityManager.deny(serverPlayer, Component.translatable("message.infinitemultiverse.no_stand_available"));
            return InteractionResultHolder.fail(stack);
        }
        StandType awakened = candidates.get(serverPlayer.getRandom().nextInt(candidates.size()));
        StandManager.awaken(serverPlayer, awakened);
        serverPlayer.hurt(level.damageSources().magic(), PIERCE_DAMAGE);
        stack.consume(1, serverPlayer);

        Vec3 at = serverPlayer.position();
        MultiverseVfx.sound(serverPlayer.serverLevel(), at, ModSounds.STAND_AWAKEN, 1.0f, 1.0f);
        MultiverseVfx.broadcast(serverPlayer.serverLevel(), VfxIds.STAND_AWAKEN, at, Vec3.ZERO, (float) awakened.color());
        serverPlayer.displayClientMessage(Component.translatable("message.infinitemultiverse.stand_awakened",
                awakened.displayName().copy().withStyle(style -> style.withColor(awakened.color()))), false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infinitemultiverse.awakening_arrowhead.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
