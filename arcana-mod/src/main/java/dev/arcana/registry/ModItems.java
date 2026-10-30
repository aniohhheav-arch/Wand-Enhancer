package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.item.MaterialItem;
import dev.arcana.item.WandItem;
import dev.arcana.magic.School;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArcanaMod.MODID);

    public static final Map<School, DeferredItem<WandItem>> WANDS = new EnumMap<>(School.class);
    public static final Map<School, DeferredItem<Item>> CRYSTALS = new EnumMap<>(School.class);
    /** Cores, runes and rare progression materials, in creative-tab order. */
    public static final Map<String, DeferredItem<Item>> MATERIALS = new LinkedHashMap<>();

    public static DeferredItem<Item> AWAKENING_SHARD, ASCENSION_CORE, FORBIDDEN_FRAGMENT;
    public static DeferredItem<DeferredSpawnEggItem> ARCHMAGE_EGG;

    static {
        for (School s : School.values()) {
            Item.Properties p = new Item.Properties().stacksTo(1).fireResistant();
            if (s == School.NONE) p.rarity(Rarity.EPIC);
            WANDS.put(s, ITEMS.registerItem(s.id + "_wand", props -> new WandItem(props.stacksTo(1).fireResistant()
                .rarity(s == School.NONE ? Rarity.EPIC : Rarity.RARE), s)));
            if (s != School.NONE) {
                CRYSTALS.put(s, ITEMS.registerItem(s.id + "_crystal", props -> new MaterialItem(props, s.id + "_crystal", false)));
            }
        }
        AWAKENING_SHARD = material("awakening_shard", Rarity.UNCOMMON, true);
        ASCENSION_CORE = material("ascension_core", Rarity.EPIC, true);
        FORBIDDEN_FRAGMENT = material("forbidden_fragment", Rarity.EPIC, true);
        for (String c : new String[] {"core_ember", "core_frost", "core_volt", "core_spirit"}) material(c, Rarity.UNCOMMON, false);
        for (String r : new String[] {"rune_amplify", "rune_efficient", "rune_swift", "rune_wide", "rune_leech"}) material(r, Rarity.UNCOMMON, false);
        ARCHMAGE_EGG = ITEMS.register("hollow_archmage_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.ARCHMAGE, 0x2B0A3D, 0xFF2BD6, new Item.Properties()));
    }

    private static DeferredItem<Item> material(String id, Rarity rarity, boolean foil) {
        DeferredItem<Item> i = ITEMS.registerItem(id, props -> new MaterialItem(props.rarity(rarity), id, foil));
        MATERIALS.put(id, i);
        return i;
    }

    public static void init() {}

    private ModItems() {}
}
