package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.item.ArtifactItem;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.item.LoreItem;
import dev.mysticarts.item.SorcererArmorItem;
import dev.mysticarts.item.StoneItem;
import dev.mysticarts.item.TomeItem;
import dev.mysticarts.item.Vestments;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MysticArts.MODID);
    public static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> SPAWN_EGGS = new ArrayList<>();

    public static final DeferredItem<InfinityGauntletItem> INFINITY_GAUNTLET = add("infinity_gauntlet",
            () -> new InfinityGauntletItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant().component(MaComponents.STONES.get(), 0)));
    public static final DeferredItem<StoneItem> SPACE_STONE = stone(Source.SPACE);
    public static final DeferredItem<StoneItem> MIND_STONE = stone(Source.MIND);
    public static final DeferredItem<StoneItem> REALITY_STONE = stone(Source.REALITY);
    public static final DeferredItem<StoneItem> POWER_STONE = stone(Source.POWER);
    public static final DeferredItem<StoneItem> TIME_STONE = stone(Source.TIME);
    public static final DeferredItem<StoneItem> SOUL_STONE = stone(Source.SOUL);

    public static final DeferredItem<ArtifactItem> CLOAK_OF_LEVITATION = artifact("cloak_of_levitation", Artifact.CLOAK, Rarity.EPIC);
    public static final DeferredItem<ArtifactItem> EYE_OF_AGAMOTTO = artifact("eye_of_agamotto", Artifact.AMULET, Rarity.EPIC);
    public static final DeferredItem<ArtifactItem> SLING_RING = artifact("sling_ring", Artifact.RING, Rarity.RARE);
    public static final DeferredItem<ArtifactItem> MYSTIC_WRIST_WRAPS = artifact("mystic_wrist_wraps", Artifact.BRACERS, Rarity.UNCOMMON);

    public static final DeferredItem<TomeItem> TOME_OF_THE_INITIATE = add("tome_of_the_initiate",
            () -> new TomeItem(PowerData.TIER_INITIATE, new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1)));
    public static final DeferredItem<TomeItem> BOOK_OF_VISHANTI = add("book_of_vishanti",
            () -> new TomeItem(PowerData.TIER_ADEPT, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));

    public static final DeferredItem<Item> ARCANE_DUST = lore("arcane_dust", Rarity.COMMON);
    public static final DeferredItem<Item> RELIC_FRAGMENT = lore("relic_fragment", Rarity.UNCOMMON);
    public static final DeferredItem<Item> COSMIC_SHARD = lore("cosmic_shard", Rarity.RARE);
    public static final DeferredItem<Item> URU_INGOT = lore("uru_ingot", Rarity.RARE);

    public static final DeferredItem<SorcererArmorItem> SORCERER_TUNIC = armor("sorcerer_tunic", Vestments.SORCERER_SUPREME, MaArmorMaterials.SORCERER, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<SorcererArmorItem> SORCERER_TROUSERS = armor("sorcerer_trousers", Vestments.SORCERER_SUPREME, MaArmorMaterials.SORCERER, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<SorcererArmorItem> SORCERER_BOOTS = armor("sorcerer_boots", Vestments.SORCERER_SUPREME, MaArmorMaterials.SORCERER, ArmorItem.Type.BOOTS);
    public static final DeferredItem<SorcererArmorItem> ANCIENT_HOOD = armor("ancient_hood", Vestments.ANCIENT_ONE, MaArmorMaterials.ANCIENT, ArmorItem.Type.HELMET);
    public static final DeferredItem<SorcererArmorItem> ANCIENT_ROBE = armor("ancient_robe", Vestments.ANCIENT_ONE, MaArmorMaterials.ANCIENT, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<SorcererArmorItem> ANCIENT_LEGGINGS = armor("ancient_leggings", Vestments.ANCIENT_ONE, MaArmorMaterials.ANCIENT, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<SorcererArmorItem> ANCIENT_SANDALS = armor("ancient_sandals", Vestments.ANCIENT_ONE, MaArmorMaterials.ANCIENT, ArmorItem.Type.BOOTS);
    public static final DeferredItem<SorcererArmorItem> DARK_TUNIC = armor("dark_tunic", Vestments.DARK_DIMENSION, MaArmorMaterials.DARK_DIMENSION, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<SorcererArmorItem> DARK_TROUSERS = armor("dark_trousers", Vestments.DARK_DIMENSION, MaArmorMaterials.DARK_DIMENSION, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<SorcererArmorItem> DARK_BOOTS = armor("dark_boots", Vestments.DARK_DIMENSION, MaArmorMaterials.DARK_DIMENSION, ArmorItem.Type.BOOTS);

    public static final DeferredItem<DeferredSpawnEggItem> SPECTRAL_BEAST_EGG = egg("spectral_beast_spawn_egg", MaEntities.SPECTRAL_BEAST, 0x5A1A00, 0xFF8A1E);

    static {
        for (DeferredBlock<? extends Block> block : MaBlocks.WITH_ITEMS) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    private MaItems() {}

    private static <I extends Item> DeferredItem<I> add(String name, Supplier<I> supplier) {
        DeferredItem<I> item = ITEMS.register(name, supplier);
        TAB_ORDER.add(item);
        return item;
    }

    private static DeferredItem<StoneItem> stone(Source s) {
        return add(s.id + "_stone", () -> new StoneItem(s, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    }

    private static DeferredItem<ArtifactItem> artifact(String name, Artifact slot, Rarity rarity) {
        return add(name, () -> new ArtifactItem(slot, new Item.Properties().rarity(rarity).stacksTo(1).fireResistant()));
    }

    private static DeferredItem<Item> lore(String name, Rarity rarity) {
        return add(name, () -> new LoreItem(new Item.Properties().rarity(rarity), name));
    }

    private static DeferredItem<SorcererArmorItem> armor(String name, Vestments set, Holder<ArmorMaterial> material, ArmorItem.Type type) {
        return add(name, () -> new SorcererArmorItem(set, material, type, new Item.Properties().durability(type.getDurability(25)).rarity(Rarity.RARE)));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        DeferredItem<DeferredSpawnEggItem> item = ITEMS.register(name, () -> new DeferredSpawnEggItem(type, bg, fg, new Item.Properties()));
        SPAWN_EGGS.add(item);
        return item;
    }
}
