package dev.telephoneorder;

import dev.telephoneorder.Catalog.AmmoDef;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.entity.GunProjectile;
import dev.telephoneorder.weapon.*;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ModItems {
    public static final Map<String, Item> ALL = new LinkedHashMap<>();
    public static final Map<String, GunItem> GUNS = new LinkedHashMap<>();
    public static final Map<String, Item> AMMO = new LinkedHashMap<>();
    public static final Map<String, Item> ATTACHMENTS = new LinkedHashMap<>();
    public static Item CLUE_SLIP, ORDER_RECEIPT, CREDIT_CHIP, HANDHELD;
    public static Item PROP_CRATE, PROP_CRATE_OPEN, PROP_DRONE, PROP_ROTOR, PROP_POD, PROP_POD_OPEN, PROP_CONTAINER, PROP_CONTAINER_OPEN, PROP_CASE, PROP_CASE_OPEN;
    private static final Map<GunProjectile.Kind, Item> PROJ = new java.util.EnumMap<>(GunProjectile.Kind.class);

    private ModItems() {}

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(TelephoneOrder.MOD_ID, path); }

    public static <T extends Item> T reg(String name, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        T item = factory.apply(props.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ALL.put(name, item);
        return item;
    }

    public static Item.Properties plain() { return new Item.Properties(); }

    public static Item blockItem(String name, Block block) {
        return reg(name, p -> new BlockItem(block, p.useBlockDescriptionPrefix()), new Item.Properties());
    }

    public static Item proj(GunProjectile.Kind k) { return PROJ.get(k); }

    private static Rarity rarity(String r) {
        return switch (r) { case "uncommon" -> Rarity.UNCOMMON; case "rare" -> Rarity.RARE; case "epic" -> Rarity.EPIC; default -> Rarity.COMMON; };
    }

    public static void register() {
        for (GunDef def : Catalog.GUNS) {
            Item.Properties p = new Item.Properties().stacksTo(1).rarity(rarity(def.rarity()))
                    .component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0f));
            GunItem item = reg(def.id(), props -> new GunItem(props, def), p);
            GUNS.put(def.id(), item);
        }
        for (AmmoDef a : Catalog.AMMO) {
            Item i = reg(a.id(), p -> new AmmoItem(p, a), new Item.Properties().stacksTo(64));
            AMMO.put(a.id(), i);
        }
        for (AttDef a : Catalog.ATTS) {
            Item i = reg(a.id(), p -> new AttachmentItem(p, a), new Item.Properties().stacksTo(16).rarity(a.tier() >= 2 ? Rarity.UNCOMMON : Rarity.COMMON));
            ATTACHMENTS.put(a.id(), i);
        }
        CLUE_SLIP = reg("clue_slip", p -> new ClueSlipItem(p), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
        ORDER_RECEIPT = reg("order_receipt", ReceiptItem::new, new Item.Properties().stacksTo(1));
        CREDIT_CHIP = reg("credit_chip", CreditChipItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
        HANDHELD = reg("handheld_phone", dev.telephoneorder.weapon.HandheldPhoneItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        PROP_CRATE = reg("prop_crate", Item::new, plain());
        PROP_CRATE_OPEN = reg("prop_crate_open", Item::new, plain());
        PROP_DRONE = reg("prop_drone", Item::new, plain());
        PROP_ROTOR = reg("prop_rotor", Item::new, plain());
        PROP_POD = reg("prop_pod", Item::new, plain());
        PROP_POD_OPEN = reg("prop_pod_open", Item::new, plain());
        PROP_CONTAINER = reg("prop_container", Item::new, plain());
        PROP_CONTAINER_OPEN = reg("prop_container_open", Item::new, plain());
        PROP_CASE = reg("prop_case", Item::new, plain());
        PROP_CASE_OPEN = reg("prop_case_open", Item::new, plain());
        for (GunProjectile.Kind k : GunProjectile.Kind.values()) PROJ.put(k, reg("proj_" + k.name().toLowerCase(java.util.Locale.ROOT), Item::new, plain()));
    }

    public static void registerBlockItems() {
        blockItem("telephone_rotary", ModBlocks.TELEPHONE_ROTARY);
        blockItem("telephone_terminal", ModBlocks.TELEPHONE_TERMINAL);
        blockItem("telephone_uplink", ModBlocks.TELEPHONE_UPLINK);
        blockItem("weapons_workbench", ModBlocks.WORKBENCH);
    }

    public static void registerTab() {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.telephoneorder.main"))
                .icon(() -> new ItemStack(GUNS.get("vesper_p9")))
                .displayItems((params, out) -> {
                    out.accept(ALL.get("telephone_rotary")); out.accept(ALL.get("telephone_terminal")); out.accept(ALL.get("telephone_uplink"));
                    out.accept(ALL.get("weapons_workbench")); out.accept(HANDHELD);
                    for (Item i : AMMO.values()) out.accept(i);
                    for (Item i : ATTACHMENTS.values()) out.accept(i);
                    out.accept(CLUE_SLIP); out.accept(CREDIT_CHIP);
                }).build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
    }
}
