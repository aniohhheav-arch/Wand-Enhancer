package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Riftverse.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RIFT_WALKER = reg("rift_walker", 3, 7, 6, 3, 9, 18, 2.5f, 0.05f,
            () -> Ingredient.of(RvItems.RIFT_SHARD.get()));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VOYAGER = reg("voyager", 3, 7, 6, 3, 9, 20, 2.0f, 0.0f,
            () -> Ingredient.of(RvItems.EXOTIC_INGOT.get()));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EVENT_HORIZON = reg("event_horizon", 4, 9, 7, 4, 11, 16, 3.5f, 0.15f,
            () -> Ingredient.of(RvItems.SINGULARITY_FRAGMENT.get()));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ASTRAL = reg("astral", 4, 9, 8, 4, 12, 25, 4.0f, 0.1f,
            () -> Ingredient.of(RvItems.STELLAR_DUST.get()));

    private RvArmorMaterials() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> reg(String name, int boots, int chest, int legs, int helmet, int body,
                                                                    int enchant, float toughness, float knockback, Supplier<Ingredient> repair) {
        return MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.BODY, body);
            return new ArmorMaterial(defense, enchant, RvSounds.ARMOR_EQUIP, repair,
                    List.of(new ArmorMaterial.Layer(Riftverse.id(name))), toughness, knockback);
        });
    }
}
