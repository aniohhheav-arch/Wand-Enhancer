package dev.telephoneorder.weapon;

import dev.telephoneorder.phone.Account;
import dev.telephoneorder.phone.Calls;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

/** A torn slip with one digit of the Archive number. Reading it adds it to your account's clue log. */
public class ClueSlipItem extends Item {
    public ClueSlipItem(Item.Properties p) { super(p); }

    public static ItemStack make(Item item, int idx) {
        ItemStack s = new ItemStack(item);
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putInt("idx", idx);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    private static int idx(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? -1 : d.copyTag().getIntOr("idx", -1);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int i = idx(stack);
        if (i < 0 || i > 6) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            Account.addClue(sp, i);
            int have = Integer.bitCount(Account.clues(sp));
            sp.sendSystemMessage(Component.literal("Slip #" + (i + 1) + ": digit " + (i + 1) + " of the Archive line is " + Calls.ARCHIVE_DIGITS[i] + "  (" + have + "/7 found)"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        int i = idx(stack);
        out.accept(Component.literal("A torn order slip, stained and hurried.").withStyle(ChatFormatting.GRAY));
        if (i >= 0 && i <= 6) {
            out.accept(Component.literal("Digit " + (i + 1) + " of 7: " + Calls.ARCHIVE_DIGITS[i]).withStyle(ChatFormatting.GOLD));
            out.accept(Component.literal("\"... the Archive does not take calls from the incomplete.\"").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
