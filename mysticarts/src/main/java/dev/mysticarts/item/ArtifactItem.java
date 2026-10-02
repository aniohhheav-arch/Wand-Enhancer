package dev.mysticarts.item;

import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.registry.MaSounds;
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
 * Cloak, amulet, ring and bracers. Using one binds it to its artifact slot (swapping out whatever was there); the
 * Artifacts screen (default key: J) unbinds them. Bound artifacts survive death.
 */
public class ArtifactItem extends Item {
    private final Artifact slot;

    public ArtifactItem(Artifact slot, Properties properties) {
        super(properties);
        this.slot = slot;
    }

    public Artifact slot() {
        return slot;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        PowerData data = PowerManager.data(player);
        ItemStack previous = data.artifacts[slot.ordinal()];
        data.artifacts[slot.ordinal()] = stack.copyWithCount(1);
        stack.shrink(1);
        if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        data.dirty = true;
        data.casterDirty = true;
        level.playSound(null, player.blockPosition(), slot == Artifact.CLOAK ? MaSounds.CLOAK_FLAP.get() : MaSounds.GAUNTLET_EQUIP.get(), SoundSource.PLAYERS, 1f, 1.1f);
        player.displayClientMessage(Component.translatable("message.mysticarts.artifact_bound", stack.getHoverName()).withColor(slot.color), true);
        if (player instanceof ServerPlayer sp) PowerManager.sync(sp, data);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = getDescriptionId();
        tooltip.add(Component.translatable(key + ".lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(key + ".effect").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mysticarts.artifact_bind", Component.translatable("artifact.mysticarts." + slot.id))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return slot == Artifact.AMULET;
    }
}
