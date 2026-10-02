package com.infinitemultiverse.power.gear;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.registry.ModItems;
import com.infinitemultiverse.power.PowerGrantItem;
import com.infinitemultiverse.power.mystic.SlingRingItem;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Hero suits, mystic relics and the power-granting consumables. */
public final class ModGear {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, InfiniteMultiverse.MOD_ID);

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name, int helmet, int chest, int legs, int boots, float toughness,
                                                                          Holder<net.minecraft.sounds.SoundEvent> sound, Item repair) {
        return MATERIALS.register(name, () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.HELMET, helmet);
            map.put(ArmorItem.Type.CHESTPLATE, chest);
            map.put(ArmorItem.Type.LEGGINGS, legs);
            map.put(ArmorItem.Type.BOOTS, boots);
            map.put(ArmorItem.Type.BODY, chest);
        }), 15, sound, () -> Ingredient.of(repair), List.of(new ArmorMaterial.Layer(InfiniteMultiverse.id(name))), toughness, 0.1f));
    }

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MARK_ARMOR = material("mark_armor", 3, 8, 6, 3, 2.5f, SoundEvents.ARMOR_EQUIP_IRON, Items.IRON_INGOT);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIGILANTE_SUIT = material("vigilante_suit", 2, 6, 5, 2, 1.0f, SoundEvents.ARMOR_EQUIP_LEATHER, Items.LEATHER);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> AMAZONIAN_ARMOR = material("amazonian_armor", 3, 7, 6, 3, 2.0f, SoundEvents.ARMOR_EQUIP_GOLD, Items.GOLD_INGOT);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CLOAK = material("cloak_of_levitation", 0, 3, 0, 0, 0f, SoundEvents.ARMOR_EQUIP_ELYTRA, Items.RED_WOOL);

    private static DeferredItem<ArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, int durability) {
        return ModItems.ITEMS.register(name, () -> new ArmorItem(material, type, new Item.Properties().durability(type.getDurability(durability)).rarity(Rarity.EPIC)));
    }

    public static final DeferredItem<ArmorItem> MARK_HELMET = armor("mark_armor_helmet", MARK_ARMOR, ArmorItem.Type.HELMET, 33);
    public static final DeferredItem<ArmorItem> MARK_CHEST = armor("mark_armor_chestplate", MARK_ARMOR, ArmorItem.Type.CHESTPLATE, 33);
    public static final DeferredItem<ArmorItem> MARK_LEGS = armor("mark_armor_leggings", MARK_ARMOR, ArmorItem.Type.LEGGINGS, 33);
    public static final DeferredItem<ArmorItem> MARK_BOOTS = armor("mark_armor_boots", MARK_ARMOR, ArmorItem.Type.BOOTS, 33);
    public static final DeferredItem<ArmorItem> VIGILANTE_HELMET = armor("vigilante_suit_helmet", VIGILANTE_SUIT, ArmorItem.Type.HELMET, 25);
    public static final DeferredItem<ArmorItem> VIGILANTE_CHEST = armor("vigilante_suit_chestplate", VIGILANTE_SUIT, ArmorItem.Type.CHESTPLATE, 25);
    public static final DeferredItem<ArmorItem> VIGILANTE_LEGS = armor("vigilante_suit_leggings", VIGILANTE_SUIT, ArmorItem.Type.LEGGINGS, 25);
    public static final DeferredItem<ArmorItem> VIGILANTE_BOOTS = armor("vigilante_suit_boots", VIGILANTE_SUIT, ArmorItem.Type.BOOTS, 25);
    public static final DeferredItem<ArmorItem> AMAZONIAN_HELMET = armor("amazonian_armor_helmet", AMAZONIAN_ARMOR, ArmorItem.Type.HELMET, 30);
    public static final DeferredItem<ArmorItem> AMAZONIAN_CHEST = armor("amazonian_armor_chestplate", AMAZONIAN_ARMOR, ArmorItem.Type.CHESTPLATE, 30);
    public static final DeferredItem<ArmorItem> AMAZONIAN_LEGS = armor("amazonian_armor_leggings", AMAZONIAN_ARMOR, ArmorItem.Type.LEGGINGS, 30);
    public static final DeferredItem<ArmorItem> AMAZONIAN_BOOTS = armor("amazonian_armor_boots", AMAZONIAN_ARMOR, ArmorItem.Type.BOOTS, 30);
    public static final DeferredItem<ArmorItem> CLOAK_OF_LEVITATION = armor("cloak_of_levitation", CLOAK, ArmorItem.Type.CHESTPLATE, 40);

    public static final DeferredItem<SlingRingItem> SLING_RING = ModItems.ITEMS.registerItem("sling_ring", SlingRingItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<PowerGrantItem> CURSED_FINGER = ModItems.ITEMS.registerItem("cursed_finger",
            p -> new PowerGrantItem(p, MultiverseSystem.CURSED_TECHNIQUES, "item.infinitemultiverse.cursed_finger.tooltip"),
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<PowerGrantItem> MUTANT_SERUM = ModItems.ITEMS.registerItem("mutant_serum",
            p -> new PowerGrantItem(p, MultiverseSystem.SUPERHEROES, "item.infinitemultiverse.mutant_serum.tooltip"),
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<PowerGrantItem> MYSTIC_TOME = ModItems.ITEMS.registerItem("mystic_tome",
            p -> new PowerGrantItem(p, MultiverseSystem.MYSTIC_ARTS, "item.infinitemultiverse.mystic_tome.tooltip"),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final List<DeferredItem<? extends Item>> ALL = List.of(CURSED_FINGER, MUTANT_SERUM, MYSTIC_TOME, SLING_RING, CLOAK_OF_LEVITATION,
            MARK_HELMET, MARK_CHEST, MARK_LEGS, MARK_BOOTS, VIGILANTE_HELMET, VIGILANTE_CHEST, VIGILANTE_LEGS, VIGILANTE_BOOTS,
            AMAZONIAN_HELMET, AMAZONIAN_CHEST, AMAZONIAN_LEGS, AMAZONIAN_BOOTS);

    private ModGear() {
    }

    /** True when every armour slot holds a piece of {@code material}. */
    public static boolean wearingSuit(ServerPlayer player, Holder<ArmorMaterial> material) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getMaterial().value() != material.value()) {
                return false;
            }
        }
        return true;
    }

    public static boolean wearingCloak(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(CLOAK_OF_LEVITATION.get());
    }
}
