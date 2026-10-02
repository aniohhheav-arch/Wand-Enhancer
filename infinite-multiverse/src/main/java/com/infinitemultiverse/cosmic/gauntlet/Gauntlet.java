package com.infinitemultiverse.cosmic.gauntlet;

import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.cosmic.CosmicContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Gauntlet state lives on the item: which stones are set, and the selected page/ability.
 * Pages 0–5 are the stones; page 6 ("All Stones") holds the combinations and the Snap.
 */
public final class Gauntlet {
    public static final int ALL_PAGE = 6;
    public static final int FULL = 0b111111;

    private Gauntlet() {
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int stones(ItemStack stack) {
        return tag(stack).getInt("stones") & FULL;
    }

    public static void setStones(ItemStack stack, int mask) {
        CompoundTag t = tag(stack);
        t.putInt("stones", mask & FULL);
        write(stack, t);
    }

    public static int page(ItemStack stack) {
        return Mth.clamp(tag(stack).getInt("page"), 0, ALL_PAGE);
    }

    public static int slot(ItemStack stack) {
        return Math.max(0, tag(stack).getInt("slot"));
    }

    public static boolean has(ItemStack stack, InfinityStone stone) {
        return (stones(stack) & stone.bit()) != 0;
    }

    public static int count(ItemStack stack) {
        return Integer.bitCount(stones(stack));
    }

    /** The gauntlet in either hand, or EMPTY. */
    public static ItemStack held(LivingEntity entity) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            if (stack.getItem() instanceof InfinityGauntletItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean holds(LivingEntity entity, InfinityStone stone) {
        ItemStack g = held(entity);
        return !g.isEmpty() && has(g, stone);
    }

    public static int heldCount(LivingEntity entity) {
        ItemStack g = held(entity);
        return g.isEmpty() ? 0 : count(g);
    }

    /** Abilities on a page. Page 6 lists the combinations and the Snap. */
    public static List<Supplier<? extends Ability>> pageAbilities(int page) {
        return page >= ALL_PAGE ? CosmicContent.comboAbilities() : InfinityStone.values()[page].abilities();
    }

    /** Pages the player can scroll to: owned stones, plus "All" once two or more stones are set. */
    public static boolean pageAvailable(ItemStack stack, int page) {
        return page >= ALL_PAGE ? count(stack) >= 2 : (stones(stack) & (1 << page)) != 0;
    }

    /** Scroll the stone page by {@code pageDelta} (skipping missing stones) or the ability by {@code slotDelta}. */
    public static void scroll(ItemStack stack, int pageDelta, int slotDelta) {
        CompoundTag t = tag(stack);
        int page = page(stack);
        if (pageDelta != 0 && stones(stack) != 0) {
            for (int i = 0; i < ALL_PAGE + 1; i++) {
                page = Math.floorMod(page + Integer.signum(pageDelta), ALL_PAGE + 1);
                if (pageAvailable(stack, page)) {
                    break;
                }
            }
            t.putInt("page", page);
            t.putInt("slot", 0);
        }
        if (slotDelta != 0) {
            int size = pageAbilities(page).size();
            t.putInt("slot", Math.floorMod(slot(stack) + Integer.signum(slotDelta), size));
        }
        write(stack, t);
    }

    @Nullable
    public static Ability selected(ItemStack stack) {
        int page = page(stack);
        if (!pageAvailable(stack, page)) {
            return null;
        }
        List<Supplier<? extends Ability>> list = pageAbilities(page);
        return list.isEmpty() ? null : list.get(Math.min(slot(stack), list.size() - 1)).get();
    }
}
