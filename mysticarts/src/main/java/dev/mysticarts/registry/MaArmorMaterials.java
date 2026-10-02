package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MaArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MysticArts.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SORCERER = reg("sorcerer", 2, 6, 5, 2, 18, 1.0f,
            () -> Ingredient.of(MaItems.ARCANE_DUST.get()));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ANCIENT = reg("ancient", 2, 6, 5, 3, 22, 1.5f,
            () -> Ingredient.of(MaItems.RELIC_FRAGMENT.get()));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DARK_DIMENSION = reg("dark_dimension", 3, 8, 6, 3, 15, 2.5f,
            () -> Ingredient.of(MaItems.ARCANE_DUST.get()));

    private MaArmorMaterials() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> reg(String name, int boots, int chest, int legs, int helmet, int enchant, float toughness,
                                                                    Supplier<Ingredient> repair) {
        return MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.BODY, chest);
            return new ArmorMaterial(defense, enchant, MaSounds.CLOAK_FLAP, repair, List.of(new ArmorMaterial.Layer(MysticArts.id(name))), toughness, 0f);
        });
    }
}
