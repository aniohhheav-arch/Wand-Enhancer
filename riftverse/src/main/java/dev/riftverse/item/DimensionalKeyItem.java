package dev.riftverse.item;

import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.registry.RvBlocks;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Carries the coordinates of a reality. Imprint it at a Multiverse Console, then use it on a Rift Frame portal (or hold
 * it in the off-hand while charging the Portal Gun) to open a passage to exactly that universe.
 */
public class DimensionalKeyItem extends Item {
    public DimensionalKeyItem(Properties properties) {
        super(properties);
    }

    public static void imprint(ItemStack stack, Destination dest, String name, int color) {
        ItemData.edit(stack, t -> {
            t.put("dest", dest.save());
            t.putString("name", name);
            t.putInt("color", color);
        });
    }

    @Nullable
    public static Destination destination(ItemStack stack) {
        CompoundTag t = ItemData.read(stack);
        return t.contains("dest") ? Destination.load(t.getCompound("dest")) : null;
    }

    public static int color(ItemStack stack) {
        CompoundTag t = ItemData.read(stack);
        return t.contains("color") ? t.getInt("color") : 0x8F6BFF;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Destination dest = destination(stack);
        if (dest == null) return InteractionResult.PASS;
        if (level.getBlockState(pos).is(RvBlocks.PORTAL_FIELD.get()) && level.getBlockEntity(pos) instanceof PortalFieldBlockEntity field) {
            if (!level.isClientSide) {
                String name = ItemData.read(stack).getString("name");
                PortalFieldBlockEntity.fill(level, field.origin(), field.axis(), field.width(), field.height(), dest, name, color(stack));
                level.playSound(null, pos, RvSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockState(pos).is(RvBlocks.RIFT_FRAME.get())) {
            if (!level.isClientSide) {
                String name = ItemData.read(stack).getString("name");
                if (RiftIgniterItem.ignite(level, pos, dest, name, color(stack))) {
                    level.playSound(null, pos, RvSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 1.2f, 0.9f);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return destination(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = ItemData.read(stack);
        if (t.contains("dest")) {
            tooltip.add(Component.literal(t.getString("name")).withStyle(s -> s.withColor(TextColor.fromRgb(color(stack)))));
            tooltip.add(Component.literal(Destination.load(t.getCompound("dest")).describe()).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.riftverse.dimensional_key.blank").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
