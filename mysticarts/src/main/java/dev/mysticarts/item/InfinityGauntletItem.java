package dev.mysticarts.item;

import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * The Infinity Gauntlet. Holds up to six stones (data component {@code mysticarts:stones}); its powers are cast with
 * the ability keys while it is held in either hand.
 */
public class InfinityGauntletItem extends Item {
    public InfinityGauntletItem(Properties properties) {
        super(properties);
    }

    public static int stones(ItemStack stack) {
        return stack.getOrDefault(MaComponents.STONES.get(), 0);
    }

    public static boolean has(ItemStack stack, Source stone) {
        return (stones(stack) & stone.bit()) != 0;
    }

    public static void socket(ItemStack stack, Source stone) {
        stack.set(MaComponents.STONES.get(), stones(stack) | stone.bit());
    }

    public static ItemStack full(Item gauntlet) {
        ItemStack stack = new ItemStack(gauntlet);
        stack.set(MaComponents.STONES.get(), Source.ALL_STONES);
        return stack;
    }

    /** The gauntlet the entity is holding (main hand first), or empty. */
    public static ItemStack held(@Nullable LivingEntity entity) {
        if (entity == null) return ItemStack.EMPTY;
        if (entity.getMainHandItem().getItem() instanceof InfinityGauntletItem) return entity.getMainHandItem();
        if (entity.getOffhandItem().getItem() instanceof InfinityGauntletItem) return entity.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static int heldStones(@Nullable Player player) {
        ItemStack s = held(player);
        return s.isEmpty() ? 0 : stones(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mysticarts.infinity_gauntlet.lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        int mask = stones(stack);
        for (Source s : Source.STONES) {
            boolean in = (mask & s.bit()) != 0;
            Component name = Component.translatable("item.mysticarts." + s.id + "_stone");
            tooltip.add(in ? Component.literal(" ◆ ").append(name).withColor(s.color)
                    : Component.literal(" ◇ ").append(name).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.mysticarts.gauntlet_keys").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
