package dev.mysticarts.block;

import dev.mysticarts.registry.MaItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * In-world rituals performed at the Ritual Altar: drop the ingredients onto the altar and invoke it. The first
 * recipe whose ingredients are all present is performed.
 */
public final class RitualRecipes {
    public record Ingredient(Supplier<? extends Item> item, int count) {}

    public record Recipe(String id, Supplier<? extends Item> result, int resultCount, List<Ingredient> inputs, int color) {
        public ItemStack output() {
            return new ItemStack(result.get(), resultCount);
        }
    }

    public static final List<Recipe> ALL = new ArrayList<>();

    static {
        add("uru_ingot", MaItems.URU_INGOT, 2, 0xC9D3E0, in(() -> Items.IRON_INGOT, 2), in(() -> Items.GOLD_INGOT, 2), in(MaItems.COSMIC_SHARD, 1), in(() -> Items.BLAZE_POWDER, 1));
        add("infinity_gauntlet", MaItems.INFINITY_GAUNTLET, 1, 0xFFC94D, in(MaItems.URU_INGOT, 6), in(() -> Items.GOLD_BLOCK, 2), in(() -> Items.DIAMOND, 2));
        add("sling_ring", MaItems.SLING_RING, 1, 0xFF9A2E, in(() -> Items.GOLD_INGOT, 2), in(MaItems.ARCANE_DUST, 2), in(() -> Items.ENDER_PEARL, 1));
        add("eye_of_agamotto", MaItems.EYE_OF_AGAMOTTO, 1, 0x22E06A, in(() -> Items.GOLD_INGOT, 4), in(() -> Items.EMERALD, 2), in(MaItems.RELIC_FRAGMENT, 2), in(MaItems.ARCANE_DUST, 4));
        add("cloak_of_levitation", MaItems.CLOAK_OF_LEVITATION, 1, 0xD8263A, in(() -> Items.RED_WOOL, 4), in(() -> Items.PHANTOM_MEMBRANE, 2), in(() -> Items.GOLD_INGOT, 2), in(MaItems.RELIC_FRAGMENT, 1));
        add("mystic_wrist_wraps", MaItems.MYSTIC_WRIST_WRAPS, 1, 0xE8D6B0, in(() -> Items.LEATHER, 2), in(() -> Items.STRING, 2), in(MaItems.ARCANE_DUST, 2));
        add("tome_of_the_initiate", MaItems.TOME_OF_THE_INITIATE, 1, 0xFF9A2E, in(() -> Items.BOOK, 1), in(MaItems.ARCANE_DUST, 3), in(() -> Items.LAPIS_LAZULI, 3));
        add("book_of_vishanti", MaItems.BOOK_OF_VISHANTI, 1, 0xFFD27A, in(MaItems.TOME_OF_THE_INITIATE, 1), in(MaItems.RELIC_FRAGMENT, 3), in(() -> Items.GOLD_INGOT, 2), in(() -> Items.ENDER_EYE, 1));
        add("sorcerer_tunic", MaItems.SORCERER_TUNIC, 1, 0x2B4C9B, in(() -> Items.BLUE_WOOL, 6), in(MaItems.ARCANE_DUST, 2), in(() -> Items.GOLD_NUGGET, 4));
        add("sorcerer_trousers", MaItems.SORCERER_TROUSERS, 1, 0x2B4C9B, in(() -> Items.GRAY_WOOL, 5), in(MaItems.ARCANE_DUST, 2));
        add("sorcerer_boots", MaItems.SORCERER_BOOTS, 1, 0x3A2A1E, in(() -> Items.LEATHER, 4), in(MaItems.ARCANE_DUST, 1));
        add("ancient_hood", MaItems.ANCIENT_HOOD, 1, 0xE8B23A, in(() -> Items.YELLOW_WOOL, 4), in(MaItems.RELIC_FRAGMENT, 1), in(MaItems.ARCANE_DUST, 2));
        add("ancient_robe", MaItems.ANCIENT_ROBE, 1, 0xE8B23A, in(() -> Items.YELLOW_WOOL, 7), in(MaItems.RELIC_FRAGMENT, 2), in(MaItems.ARCANE_DUST, 2));
        add("ancient_leggings", MaItems.ANCIENT_LEGGINGS, 1, 0xE8B23A, in(() -> Items.ORANGE_WOOL, 6), in(MaItems.RELIC_FRAGMENT, 1), in(MaItems.ARCANE_DUST, 2));
        add("ancient_sandals", MaItems.ANCIENT_SANDALS, 1, 0x8A5A2E, in(() -> Items.LEATHER, 3), in(MaItems.RELIC_FRAGMENT, 1));
        add("dark_tunic", MaItems.DARK_TUNIC, 1, 0x5A1E8A, in(MaItems.SORCERER_TUNIC, 1), in(() -> Items.ECHO_SHARD, 2), in(() -> Items.BLACK_DYE, 4));
        add("dark_trousers", MaItems.DARK_TROUSERS, 1, 0x5A1E8A, in(MaItems.SORCERER_TROUSERS, 1), in(() -> Items.ECHO_SHARD, 1), in(() -> Items.BLACK_DYE, 3));
        add("dark_boots", MaItems.DARK_BOOTS, 1, 0x5A1E8A, in(MaItems.SORCERER_BOOTS, 1), in(() -> Items.ECHO_SHARD, 1), in(() -> Items.BLACK_DYE, 2));
    }

    private RitualRecipes() {}

    private static Ingredient in(Supplier<? extends Item> item, int count) {
        return new Ingredient(item, count);
    }

    private static void add(String id, Supplier<? extends Item> result, int count, int color, Ingredient... inputs) {
        ALL.add(new Recipe(id, result, count, List.of(inputs), color));
    }

    /** First recipe satisfied by the given pool of stacks, or null. */
    @Nullable
    public static Recipe match(List<ItemStack> pool) {
        for (Recipe r : ALL) {
            boolean ok = true;
            for (Ingredient i : r.inputs) {
                int have = 0;
                for (ItemStack s : pool) if (s.is(i.item.get())) have += s.getCount();
                if (have < i.count) {
                    ok = false;
                    break;
                }
            }
            if (ok) return r;
        }
        return null;
    }
}
