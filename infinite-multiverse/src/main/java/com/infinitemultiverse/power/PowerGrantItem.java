package com.infinitemultiverse.power;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
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

/** Consumable that grants a random power set of one system (Cursed Finger, Mutant Serum, Mystic Tome). */
public final class PowerGrantItem extends Item {
    private final MultiverseSystem system;
    private final String tooltipKey;

    public PowerGrantItem(Properties properties, MultiverseSystem system, String tooltipKey) {
        super(properties);
        this.system = system;
        this.tooltipKey = tooltipKey;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        PowerSet current = PowerManager.powerOf(serverPlayer, system);
        if (current != null && !MultiverseConfig.SERVER.allowStandReroll.get()) {
            AbilityManager.deny(serverPlayer, Component.translatable("message.infinitemultiverse.already_have_power", current.displayName()));
            return InteractionResultHolder.fail(stack);
        }
        List<PowerSet> candidates = PowerManager.setsFor(system).stream().filter(set -> set != current).toList();
        if (candidates.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        PowerSet granted = candidates.get(serverPlayer.getRandom().nextInt(candidates.size()));
        PowerManager.grant(serverPlayer, granted);
        stack.consume(1, serverPlayer);
        Vec3 at = serverPlayer.position();
        MultiverseVfx.sound(serverPlayer.serverLevel(), at, ModSounds.STAND_AWAKEN, 1.0f, 1.0f);
        MultiverseVfx.broadcast(serverPlayer.serverLevel(), VfxIds.STAND_AWAKEN, at, Vec3.ZERO, (float) granted.color());
        serverPlayer.displayClientMessage(Component.translatable("message.infinitemultiverse.power_granted",
                granted.displayName().copy().withStyle(style -> style.withColor(granted.color()))), false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
