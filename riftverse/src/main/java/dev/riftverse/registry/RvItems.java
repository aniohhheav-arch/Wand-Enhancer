package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.item.DimensionalKeyItem;
import dev.riftverse.item.GravityGauntletItem;
import dev.riftverse.item.HomewardRiftItem;
import dev.riftverse.item.LoreItem;
import dev.riftverse.item.PortalGunItem;
import dev.riftverse.item.RealityShaperItem;
import dev.riftverse.item.RiftArmorItem;
import dev.riftverse.item.RiftBladeItem;
import dev.riftverse.item.RiftIgniterItem;
import dev.riftverse.item.RiftSigilItem;
import dev.riftverse.item.SingularityCoreItem;
import dev.riftverse.item.SingularityGrenadeItem;
import dev.riftverse.item.UniverseCompassItem;
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

public final class RvItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Riftverse.MODID);
    public static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> SPAWN_EGGS = new ArrayList<>();

    public static final DeferredItem<Item> RIFT_SHARD = add("rift_shard", () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), "rift_shard"));
    public static final DeferredItem<Item> VOID_ESSENCE = add("void_essence", () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), "void_essence"));
    public static final DeferredItem<Item> STELLAR_DUST = add("stellar_dust", () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), "stellar_dust"));
    public static final DeferredItem<Item> SINGULARITY_FRAGMENT = add("singularity_fragment", () -> new LoreItem(new Item.Properties().rarity(Rarity.RARE).fireResistant(), "singularity_fragment"));
    public static final DeferredItem<Item> EXOTIC_INGOT = add("exotic_ingot", () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), "exotic_ingot"));
    public static final DeferredItem<Item> WARDEN_CORE = add("warden_core", () -> new LoreItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16), "warden_core"));
    public static final DeferredItem<Item> LEVIATHAN_SCALE = add("leviathan_scale", () -> new LoreItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16), "leviathan_scale"));

    public static final DeferredItem<PortalGunItem> PORTAL_GUN = add("portal_gun", () -> new PortalGunItem(tool(Rarity.EPIC)));
    public static final DeferredItem<RiftBladeItem> RIFT_BLADE = add("rift_blade", () -> new RiftBladeItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()
            .attributes(RiftBladeItem.createAttributes())));
    public static final DeferredItem<GravityGauntletItem> GRAVITY_GAUNTLET = add("gravity_gauntlet", () -> new GravityGauntletItem(tool(Rarity.EPIC)));
    public static final DeferredItem<SingularityGrenadeItem> SINGULARITY_GRENADE = add("singularity_grenade", () -> new SingularityGrenadeItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final DeferredItem<SingularityCoreItem> SINGULARITY_CORE = add("singularity_core", () -> new SingularityCoreItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<RealityShaperItem> REALITY_SHAPER = add("reality_shaper", () -> new RealityShaperItem(tool(Rarity.EPIC)));
    public static final DeferredItem<DimensionalKeyItem> DIMENSIONAL_KEY = add("dimensional_key", () -> new DimensionalKeyItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(1)));
    public static final DeferredItem<UniverseCompassItem> UNIVERSE_COMPASS = add("universe_compass", () -> new UniverseCompassItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1)));
    public static final DeferredItem<HomewardRiftItem> HOMEWARD_RIFT = add("homeward_rift", () -> new HomewardRiftItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final DeferredItem<RiftIgniterItem> RIFT_IGNITER = add("rift_igniter", () -> new RiftIgniterItem(new Item.Properties().rarity(Rarity.UNCOMMON).durability(64)));
    public static final DeferredItem<dev.riftverse.item.relic.RelicBladeItem> RELIC_BLADE = add("relic_blade",
            () -> new dev.riftverse.item.relic.RelicBladeItem(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<dev.riftverse.item.relic.RelicBlasterItem> RELIC_BLASTER = add("relic_blaster",
            () -> new dev.riftverse.item.relic.RelicBlasterItem(new Item.Properties().rarity(Rarity.RARE).durability(900).fireResistant()));
    /** Deliberately not in the creative tab and without a recipe: only the creator interface can forge one. */
    public static final DeferredItem<dev.riftverse.item.RealityRuptureItem> REALITY_RUPTURE = add("reality_rupture",
            () -> new dev.riftverse.item.RealityRuptureItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<dev.riftverse.item.RealityRemoteItem> REALITY_REMOTE = add("reality_remote",
            () -> new dev.riftverse.item.RealityRemoteItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<RiftSigilItem> RIFT_SIGIL = add("rift_sigil", () -> new RiftSigilItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));

    public static final DeferredItem<RiftArmorItem> RIFT_WALKER_HELMET = armor("rift_walker_helmet", RvArmorMaterials.RIFT_WALKER, ArmorItem.Type.HELMET, 33);
    public static final DeferredItem<RiftArmorItem> RIFT_WALKER_CHESTPLATE = armor("rift_walker_chestplate", RvArmorMaterials.RIFT_WALKER, ArmorItem.Type.CHESTPLATE, 33);
    public static final DeferredItem<RiftArmorItem> RIFT_WALKER_LEGGINGS = armor("rift_walker_leggings", RvArmorMaterials.RIFT_WALKER, ArmorItem.Type.LEGGINGS, 33);
    public static final DeferredItem<RiftArmorItem> RIFT_WALKER_BOOTS = armor("rift_walker_boots", RvArmorMaterials.RIFT_WALKER, ArmorItem.Type.BOOTS, 33);
    public static final DeferredItem<RiftArmorItem> VOYAGER_HELMET = armor("voyager_helmet", RvArmorMaterials.VOYAGER, ArmorItem.Type.HELMET, 33);
    public static final DeferredItem<RiftArmorItem> VOYAGER_CHESTPLATE = armor("voyager_chestplate", RvArmorMaterials.VOYAGER, ArmorItem.Type.CHESTPLATE, 33);
    public static final DeferredItem<RiftArmorItem> VOYAGER_LEGGINGS = armor("voyager_leggings", RvArmorMaterials.VOYAGER, ArmorItem.Type.LEGGINGS, 33);
    public static final DeferredItem<RiftArmorItem> VOYAGER_BOOTS = armor("voyager_boots", RvArmorMaterials.VOYAGER, ArmorItem.Type.BOOTS, 33);
    public static final DeferredItem<RiftArmorItem> EVENT_HORIZON_HELMET = armor("event_horizon_helmet", RvArmorMaterials.EVENT_HORIZON, ArmorItem.Type.HELMET, 40);
    public static final DeferredItem<RiftArmorItem> EVENT_HORIZON_CHESTPLATE = armor("event_horizon_chestplate", RvArmorMaterials.EVENT_HORIZON, ArmorItem.Type.CHESTPLATE, 40);
    public static final DeferredItem<RiftArmorItem> EVENT_HORIZON_LEGGINGS = armor("event_horizon_leggings", RvArmorMaterials.EVENT_HORIZON, ArmorItem.Type.LEGGINGS, 40);
    public static final DeferredItem<RiftArmorItem> EVENT_HORIZON_BOOTS = armor("event_horizon_boots", RvArmorMaterials.EVENT_HORIZON, ArmorItem.Type.BOOTS, 40);
    public static final DeferredItem<RiftArmorItem> ASTRAL_HELMET = armor("astral_helmet", RvArmorMaterials.ASTRAL, ArmorItem.Type.HELMET, 45);
    public static final DeferredItem<RiftArmorItem> ASTRAL_CHESTPLATE = armor("astral_chestplate", RvArmorMaterials.ASTRAL, ArmorItem.Type.CHESTPLATE, 45);
    public static final DeferredItem<RiftArmorItem> ASTRAL_LEGGINGS = armor("astral_leggings", RvArmorMaterials.ASTRAL, ArmorItem.Type.LEGGINGS, 45);
    public static final DeferredItem<RiftArmorItem> ASTRAL_BOOTS = armor("astral_boots", RvArmorMaterials.ASTRAL, ArmorItem.Type.BOOTS, 45);

    public static final DeferredItem<DeferredSpawnEggItem> ASTRAL_JELLY_EGG = egg("astral_jelly_spawn_egg", RvEntities.ASTRAL_JELLY, 0x2B1250, 0xFF7AF0);
    public static final DeferredItem<DeferredSpawnEggItem> SKY_WHALE_EGG = egg("sky_whale_spawn_egg", RvEntities.SKY_WHALE, 0x2F6BFF, 0xBEE7FF);
    public static final DeferredItem<DeferredSpawnEggItem> LUMEN_STRIDER_EGG = egg("lumen_strider_spawn_egg", RvEntities.LUMEN_STRIDER, 0x1E4F48, 0x7CFFB2);
    public static final DeferredItem<DeferredSpawnEggItem> NEON_DRONE_EGG = egg("neon_drone_spawn_egg", RvEntities.NEON_DRONE, 0x1A1A24, 0x00F0FF);
    public static final DeferredItem<DeferredSpawnEggItem> GLITCHLING_EGG = egg("glitchling_spawn_egg", RvEntities.GLITCHLING, 0x000000, 0x39FF14);
    public static final DeferredItem<DeferredSpawnEggItem> VOID_STALKER_EGG = egg("void_stalker_spawn_egg", RvEntities.VOID_STALKER, 0x050308, 0x9B30FF);
    public static final DeferredItem<DeferredSpawnEggItem> CRYSTAL_SENTINEL_EGG = egg("crystal_sentinel_spawn_egg", RvEntities.CRYSTAL_SENTINEL, 0x6A3AAA, 0x7DF9FF);
    public static final DeferredItem<DeferredSpawnEggItem> RIFT_WRAITH_EGG = egg("rift_wraith_spawn_egg", RvEntities.RIFT_WRAITH, 0x120020, 0xFF2BD6);
    public static final DeferredItem<DeferredSpawnEggItem> RIFT_WARDEN_EGG = egg("rift_warden_spawn_egg", RvEntities.RIFT_WARDEN, 0x05010F, 0xFFC14D);
    public static final DeferredItem<DeferredSpawnEggItem> VOID_CULTIST_EGG = egg("void_cultist_spawn_egg", RvEntities.VOID_CULTIST, 0x10051A, 0x9B30FF);
    public static final DeferredItem<DeferredSpawnEggItem> CRYSTAL_SPIDER_EGG = egg("crystal_spider_spawn_egg", RvEntities.CRYSTAL_SPIDER, 0x2A1040, 0xC080FF);
    public static final DeferredItem<DeferredSpawnEggItem> STAR_MOTH_EGG = egg("star_moth_spawn_egg", RvEntities.STAR_MOTH, 0x1A1030, 0xFFE8A0);
    public static final DeferredItem<DeferredSpawnEggItem> LUNAR_GOLEM_EGG = egg("lunar_golem_spawn_egg", RvEntities.LUNAR_GOLEM, 0xB8B8C0, 0x7DF9FF);
    public static final DeferredItem<dev.riftverse.item.VehicleItem> DELOREAN = add("delorean",
            () -> new dev.riftverse.item.VehicleItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1), () -> RvEntities.DELOREAN.get()));
    public static final DeferredItem<dev.riftverse.item.VehicleItem> TARDIS = add("tardis",
            () -> new dev.riftverse.item.VehicleItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1), () -> RvEntities.TARDIS.get()));
    public static final DeferredItem<dev.riftverse.item.PruningStaffItem> PRUNING_STAFF = add("pruning_staff",
            () -> new dev.riftverse.item.PruningStaffItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
    public static final DeferredItem<dev.riftverse.fusion.UniverseSampleItem> UNIVERSE_SAMPLE = add("universe_sample",
            () -> new dev.riftverse.fusion.UniverseSampleItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));
    public static final DeferredItem<dev.riftverse.fusion.FusionEngineItem> FUSION_ENGINE = add("fusion_engine",
            () -> new dev.riftverse.fusion.FusionEngineItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    public static final DeferredItem<DeferredSpawnEggItem> TSA_AGENT_EGG = egg("tsa_agent_spawn_egg", RvEntities.TSA_AGENT, 0x14161C, 0x60A0FF);
    public static final DeferredItem<DeferredSpawnEggItem> DENIZEN_EGG = egg("denizen_spawn_egg", RvEntities.DENIZEN, 0x3A2A5A, 0xFFC14D);
    public static final DeferredItem<DeferredSpawnEggItem> COSMIC_DEITY_EGG = egg("cosmic_deity_spawn_egg", RvEntities.COSMIC_DEITY, 0x05010F, 0xC070FF);
    public static final DeferredItem<DeferredSpawnEggItem> ABYSSAL_LEVIATHAN_EGG = egg("abyssal_leviathan_spawn_egg", RvEntities.ABYSSAL_LEVIATHAN, 0x0A1A3A, 0x40FFE0);

    static {
        for (DeferredBlock<? extends Block> block : RvBlocks.WITH_ITEMS) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    private RvItems() {}

    private static Item.Properties tool(Rarity rarity) {
        return new Item.Properties().rarity(rarity).stacksTo(1).fireResistant();
    }

    private static <I extends Item> DeferredItem<I> add(String name, Supplier<I> supplier) {
        DeferredItem<I> item = ITEMS.register(name, supplier);
        TAB_ORDER.add(item);
        return item;
    }

    private static DeferredItem<RiftArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, int durabilityFactor) {
        return add(name, () -> new RiftArmorItem(material, type, new Item.Properties()
                .durability(type.getDurability(durabilityFactor)).rarity(Rarity.EPIC).fireResistant()));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        DeferredItem<DeferredSpawnEggItem> item = ITEMS.register(name, () -> new DeferredSpawnEggItem(type, bg, fg, new Item.Properties()));
        SPAWN_EGGS.add(item);
        return item;
    }
}
