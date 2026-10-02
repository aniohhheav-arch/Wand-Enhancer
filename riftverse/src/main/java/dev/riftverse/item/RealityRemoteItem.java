package dev.riftverse.item;

import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.RealityRemoteService;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.UniverseId;
import java.util.List;
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

/**
 * The Reality Remote: the endgame instrument of the multiverse. Use it to open its console (scan, stabilize, modify,
 * archive, restore, rebuild, architect universes, call events and run the End Protocols); sneak-use for a quick scan.
 */
public class RealityRemoteItem extends Item {
    public RealityRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        level.playSound(null, player.blockPosition(), RvSounds.UI_SELECT.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        if (player.isShiftKeyDown()) {
            UniverseId here = RealityOps.universeOf(sp);
            if (here == null) {
                sp.displayClientMessage(Component.literal("No universe to scan here — the Remote reads the Expanse.").withColor(0xFF8A9A), true);
            } else {
                for (String line : RealityOps.scan(sp, here).message().split("\n")) sp.sendSystemMessage(Component.literal(line).withColor(0xB0E8FF));
            }
        } else {
            RealityRemoteService.open(sp);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.riftverse.reality_remote.tip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.riftverse.reality_remote.tip2").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
