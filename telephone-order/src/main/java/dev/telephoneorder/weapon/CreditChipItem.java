package dev.telephoneorder.weapon;

import dev.telephoneorder.Fx;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.phone.Account;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A found credit chip: right-click to redeem its value into your Telephone Order account. */
public class CreditChipItem extends Item {
    public CreditChipItem(Item.Properties p) { super(p); }

    public static int value(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 25 : d.copyTag().getIntOr("value", 25);
    }

    public static ItemStack make(Item item, int value) {
        ItemStack s = new ItemStack(item);
        CompoundTag t = new CompoundTag();
        t.putInt("value", value);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int v = Math.round(value(stack) * ModConfig.get().credits);
            Account.add(sp, v);
            stack.shrink(1);
            Fx.sound(sp, "order_confirm", SoundSource.PLAYERS, 0.8f, 1.5f);
            sp.sendOverlayMessage(Component.literal("Redeemed +" + v + " credits  (balance " + Account.credits(sp) + ")"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(value(stack) + " credits").withStyle(ChatFormatting.GOLD));
        out.accept(Component.literal("Right-click to redeem.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
