package dev.mysticarts.item;

import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Reading a tome permanently teaches its tier of the Mystic Arts. */
public class TomeItem extends Item {
    private final int tier;

    public TomeItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        PowerData data = PowerManager.data(player);
        int learn = tier == PowerData.TIER_ADEPT ? PowerData.TIER_INITIATE | PowerData.TIER_ADEPT : tier;
        if ((data.tiers & learn) == learn) {
            player.displayClientMessage(Component.translatable("message.mysticarts.tome_known"), true);
            return InteractionResultHolder.fail(stack);
        }
        data.tiers |= learn;
        data.dirty = true;
        ServerLevel server = (ServerLevel) level;
        server.sendParticles(MaParticles.RUNE.get().with(0xFF9A2E, 0.8f, 50), player.getX(), player.getY() + 1, player.getZ(), 40, 0.8, 0.8, 0.8, 0.05);
        server.sendParticles(MaParticles.RING.get().with(0xFFD27A, 3f, 25), player.getX(), player.getY() + 0.1, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), MaSounds.RITUAL_COMPLETE.get(), SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable(getDescriptionId() + ".learned").withStyle(ChatFormatting.GOLD), false);
        if (player instanceof ServerPlayer sp) PowerManager.sync(sp, data);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(getDescriptionId() + ".effect").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
