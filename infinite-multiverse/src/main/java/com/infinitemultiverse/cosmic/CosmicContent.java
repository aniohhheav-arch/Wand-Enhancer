package com.infinitemultiverse.cosmic;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModItems;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.cosmic.gauntlet.InfinityGauntletItem;
import com.infinitemultiverse.cosmic.gauntlet.InfinityStone;
import com.infinitemultiverse.cosmic.gauntlet.InfinityStoneItem;
import com.infinitemultiverse.cosmic.gauntlet.StoneAbilities;
import com.infinitemultiverse.cosmic.time.ChronoAbilities;
import com.infinitemultiverse.power.PowerManager;
import com.infinitemultiverse.power.PowerSet;
import com.infinitemultiverse.power.ability.AreaAbility;
import com.infinitemultiverse.power.ability.BeamAbility;
import com.infinitemultiverse.power.ability.BuffToggleAbility;
import com.infinitemultiverse.stand.RewindTracker;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Phase 3 (Cosmic) registrations: the Infinity Gauntlet and stones, Chronokinesis, and the cosmic items. */
public final class CosmicContent {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, InfiniteMultiverse.MOD_ID);
    public static final DeferredRegister<PowerSet> POWER_SETS = DeferredRegister.create(MultiverseRegistries.POWER_SET_KEY, InfiniteMultiverse.MOD_ID);

    private static final MultiverseSystem GAUNTLET = MultiverseSystem.INFINITY_GAUNTLET;
    private static final String NEEDS = "message.infinitemultiverse.needs_stones";

    private static DeferredHolder<Ability, Ability> ability(String id, Supplier<? extends Ability> factory) {
        return ABILITIES.register(id, factory);
    }

    // ---------------- SPACE ----------------
    public static final DeferredHolder<Ability, Ability> SPACE_WARP = ability("space_warp", StoneAbilities.SpaceWarp::new);
    public static final DeferredHolder<Ability, Ability> SPACE_PULL = ability("space_pull",
            () -> BeamAbility.builder(GAUNTLET).length(32).radius(1.2).hits(1).damage(4).pull(2.2).color(0x3D7BFF).scene(SceneIds.BEAM_HELIX)
                    .requires(StoneAbilities.needs(InfinityStone.SPACE), NEEDS).sound(ModSounds.PHASE_STEP_DEPART, 0.8f).build());
    public static final DeferredHolder<Ability, Ability> TESSERACT_FOLD = ability("tesseract_fold", StoneAbilities.SpaceFold::new);
    // ---------------- MIND ----------------
    public static final DeferredHolder<Ability, Ability> MIND_CONTROL = ability("mind_control", StoneAbilities.MindControl::new);
    public static final DeferredHolder<Ability, Ability> PSIONIC_LANCE = ability("psionic_lance",
            () -> BeamAbility.builder(GAUNTLET).length(30).radius(0.9).hits(4).damage(11).magic().color(0xFFD83D).scene(SceneIds.BEAM_PSYCHIC)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 120, 0))
                    .requires(StoneAbilities.needs(InfinityStone.MIND), NEEDS).sound(ModSounds.SHOCKWAVE, 1.7f).build());
    public static final DeferredHolder<Ability, Ability> PSIONIC_STORM = ability("psionic_storm", StoneAbilities.PsionicStorm::new);
    // ---------------- REALITY ----------------
    public static final DeferredHolder<Ability, Ability> REALITY_WARP = ability("reality_warp", StoneAbilities.RealityWarp::new);
    public static final DeferredHolder<Ability, Ability> REALITY_LANCE = ability("reality_lance",
            () -> BeamAbility.builder(GAUNTLET).length(30).radius(1.0).hits(5).damage(12).magic().color(0xE0202A).scene(SceneIds.BEAM_HELIX)
                    .effect(() -> new MobEffectInstance(MobEffects.WITHER, 80, 0))
                    .requires(StoneAbilities.needs(InfinityStone.REALITY), NEEDS).sound(ModSounds.STAND_TIME_ERASE, 1.4f).build());
    public static final DeferredHolder<Ability, Ability> REALITY_SHATTER = ability("reality_shatter", StoneAbilities.RealityShatter::new);
    // ---------------- POWER ----------------
    public static final DeferredHolder<Ability, Ability> POWER_PUNCH = ability("power_punch",
            () -> AreaAbility.builder(GAUNTLET).radius(7).cone(0.55).damage(14).knockback(4.5).color(0xA040FF).scene(SceneIds.WAVE_CONE)
                    .requires(StoneAbilities.needs(InfinityStone.POWER), NEEDS).sound(ModSounds.STAND_HEAVY, 0.7f).build());
    public static final DeferredHolder<Ability, Ability> POWER_BLAST = ability("power_blast",
            () -> BeamAbility.builder(GAUNTLET).length(40).radius(1.8).hits(10).damage(20).knockback(2.5).color(0xA040FF).endBurst()
                    .charge(8).scene(SceneIds.BEAM_HELIX).requires(StoneAbilities.needs(InfinityStone.POWER), NEEDS).sound(ModSounds.SHOCKWAVE, 0.8f).build());
    public static final DeferredHolder<Ability, Ability> POWER_NOVA = ability("power_nova",
            () -> AreaAbility.builder(GAUNTLET).radius(16).damage(30).knockback(5).color(0xA040FF).scene(SceneIds.WAVE)
                    .shout("message.infinitemultiverse.shout.power_nova", ChatFormatting.DARK_PURPLE)
                    .requires(StoneAbilities.needs(InfinityStone.POWER), NEEDS).sound(ModSounds.SHOCKWAVE, 0.5f).build());
    // ---------------- TIME ----------------
    public static final DeferredHolder<Ability, Ability> TIME_REWIND = ability("time_rewind", StoneAbilities.TimeRewind::new);
    public static final DeferredHolder<Ability, Ability> TIME_AGE = ability("time_age",
            () -> BeamAbility.builder(GAUNTLET).length(24).radius(1.0).hits(3).damage(6).magic().color(0x2EE06A).scene(SceneIds.BEAM_PSYCHIC)
                    .effect(() -> new MobEffectInstance(MobEffects.WITHER, 160, 1)).effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 2))
                    .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 160, 2))
                    .requires(StoneAbilities.needs(InfinityStone.TIME), NEEDS).sound(ModSounds.STAND_REWIND, 0.6f).build());
    public static final DeferredHolder<Ability, Ability> TIME_FREEZE = ability("time_freeze", StoneAbilities.TimeFreeze::new);
    // ---------------- SOUL ----------------
    public static final DeferredHolder<Ability, Ability> SOUL_SIPHON = ability("soul_siphon", StoneAbilities.SoulSiphon::new);
    public static final DeferredHolder<Ability, Ability> SOUL_GUARD = ability("soul_guard",
            () -> BuffToggleAbility.builder(GAUNTLET, 3f).buff(MobEffects.ABSORPTION, 2).buff(MobEffects.REGENERATION, 0).aura(0xFF8A1A)
                    .scene(SceneIds.AURA_FIRE).requires(StoneAbilities.needs(InfinityStone.SOUL), NEEDS).build());
    public static final DeferredHolder<Ability, Ability> SOUL_HARVEST = ability("soul_harvest", StoneAbilities.SoulHarvest::new);
    // ---------------- COMBINATIONS ----------------
    public static final DeferredHolder<Ability, Ability> ANNIHILATION = ability("annihilation",
            () -> BeamAbility.builder(GAUNTLET).length(56).radius(2.6).hits(24).damage(45).magic().color(0xC0206A).endBurst()
                    .cinematic(SceneIds.UNIBEAM, 15, 40).shout("message.infinitemultiverse.shout.annihilation", ChatFormatting.DARK_RED)
                    .requires(StoneAbilities.needs(InfinityStone.REALITY, InfinityStone.POWER), NEEDS).sound(ModSounds.SHOCKWAVE, 0.5f).build());
    public static final DeferredHolder<Ability, Ability> CHRONO_JUMP = ability("chrono_jump", StoneAbilities.ChronoJump::new);
    public static final DeferredHolder<Ability, Ability> SNAP = ability("snap", StoneAbilities.Snap::new);

    private static final Map<InfinityStone, List<Supplier<? extends Ability>>> BY_STONE = new EnumMap<>(Map.of(
            InfinityStone.SPACE, List.of(SPACE_WARP, SPACE_PULL, TESSERACT_FOLD),
            InfinityStone.MIND, List.of(MIND_CONTROL, PSIONIC_LANCE, PSIONIC_STORM),
            InfinityStone.REALITY, List.of(REALITY_WARP, REALITY_LANCE, REALITY_SHATTER),
            InfinityStone.POWER, List.of(POWER_PUNCH, POWER_BLAST, POWER_NOVA),
            InfinityStone.TIME, List.of(TIME_REWIND, TIME_AGE, TIME_FREEZE),
            InfinityStone.SOUL, List.of(SOUL_SIPHON, SOUL_GUARD, SOUL_HARVEST)));
    private static final List<Supplier<? extends Ability>> COMBOS = List.of(ANNIHILATION, CHRONO_JUMP, SNAP);

    public static List<Supplier<? extends Ability>> stoneAbilities(InfinityStone stone) {
        return BY_STONE.get(stone);
    }

    public static List<Supplier<? extends Ability>> comboAbilities() {
        return COMBOS;
    }

    // ---------------- Chronokinesis (superhero power set) ----------------
    private static final MultiverseSystem HEROES = MultiverseSystem.SUPERHEROES;
    public static final DeferredHolder<Ability, Ability> TIME_DILATION = ability("time_dilation", ChronoAbilities.TimeDilation::new);
    public static final DeferredHolder<Ability, Ability> OVERCLOCK = ability("overclock",
            () -> BuffToggleAbility.builder(HEROES, 4f).buff(MobEffects.MOVEMENT_SPEED, 3).buff(MobEffects.DIG_SPEED, 3).buff(MobEffects.JUMP, 1)
                    .aura(0x2EE0C0).scene(SceneIds.AURA_SPEED).build());
    public static final DeferredHolder<Ability, Ability> REWIND_STEP = ability("rewind_step", ChronoAbilities.RewindStep::new);
    public static final DeferredHolder<Ability, Ability> STASIS = ability("stasis", ChronoAbilities.Stasis::new);
    public static final DeferredHolder<PowerSet, PowerSet> CHRONOKINESIS = POWER_SETS.register("chronokinesis",
            () -> new PowerSet(HEROES, 0x2EE0C0, List.of(TIME_DILATION, OVERCLOCK, REWIND_STEP, STASIS)));

    // ---------------- space, portals, rifts ----------------
    public static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> MATERIALS = DeferredRegister.create(net.minecraft.core.registries.Registries.ARMOR_MATERIAL, InfiniteMultiverse.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.ArmorMaterial, net.minecraft.world.item.ArmorMaterial> SPACE_SUIT_MATERIAL = MATERIALS.register("space_suit",
            () -> new net.minecraft.world.item.ArmorMaterial(net.minecraft.Util.make(new java.util.EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class), m -> {
                m.put(net.minecraft.world.item.ArmorItem.Type.HELMET, 2);
                m.put(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, 6);
                m.put(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, 5);
                m.put(net.minecraft.world.item.ArmorItem.Type.BOOTS, 2);
                m.put(net.minecraft.world.item.ArmorItem.Type.BODY, 6);
            }), 10, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT),
                    List.of(new net.minecraft.world.item.ArmorMaterial.Layer(InfiniteMultiverse.id("space_suit"))), 1.0f, 0.0f));

    private static DeferredItem<net.minecraft.world.item.ArmorItem> suit(String name, net.minecraft.world.item.ArmorItem.Type type) {
        return ModItems.ITEMS.register(name, () -> new net.minecraft.world.item.ArmorItem(SPACE_SUIT_MATERIAL, type,
                new Item.Properties().durability(type.getDurability(25)).rarity(Rarity.RARE)));
    }

    public static final DeferredItem<net.minecraft.world.item.ArmorItem> SPACE_HELMET = suit("space_suit_helmet", net.minecraft.world.item.ArmorItem.Type.HELMET);
    public static final DeferredItem<net.minecraft.world.item.ArmorItem> SPACE_CHEST = suit("space_suit_chestplate", net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<net.minecraft.world.item.ArmorItem> SPACE_LEGS = suit("space_suit_leggings", net.minecraft.world.item.ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<net.minecraft.world.item.ArmorItem> SPACE_BOOTS = suit("space_suit_boots", net.minecraft.world.item.ArmorItem.Type.BOOTS);
    public static final DeferredItem<com.infinitemultiverse.cosmic.space.OrbitalBeaconItem> ORBITAL_BEACON = ModItems.ITEMS.registerItem("orbital_beacon",
            com.infinitemultiverse.cosmic.space.OrbitalBeaconItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<com.infinitemultiverse.cosmic.portal.PortalGunItem> PORTAL_GUN = ModItems.ITEMS.registerItem("portal_gun",
            com.infinitemultiverse.cosmic.portal.PortalGunItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<com.infinitemultiverse.cosmic.rift.RiftBladeItem> RIFT_BLADE = ModItems.ITEMS.registerItem("rift_blade",
            com.infinitemultiverse.cosmic.rift.RiftBladeItem::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    // ---------------- items ----------------
    public static final DeferredItem<InfinityGauntletItem> INFINITY_GAUNTLET = ModItems.ITEMS.registerItem("infinity_gauntlet", InfinityGauntletItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final Map<InfinityStone, DeferredItem<InfinityStoneItem>> STONES = new EnumMap<>(InfinityStone.class);

    static {
        for (InfinityStone stone : InfinityStone.values()) {
            STONES.put(stone, ModItems.ITEMS.registerItem(stone.id() + "_stone", p -> new InfinityStoneItem(p, stone),
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
        }
        RewindTracker.extraTracking = player -> com.infinitemultiverse.cosmic.gauntlet.Gauntlet.holds(player, InfinityStone.TIME)
                || PowerManager.powerOf(player, HEROES) == CHRONOKINESIS.get();
    }

    /** Everything Phase 3 adds to the creative tab. */
    public static List<DeferredItem<? extends Item>> creativeItems() {
        List<DeferredItem<? extends Item>> list = new java.util.ArrayList<>();
        list.add(INFINITY_GAUNTLET);
        list.addAll(STONES.values());
        list.addAll(List.of(PORTAL_GUN, RIFT_BLADE, ORBITAL_BEACON, SPACE_HELMET, SPACE_CHEST, SPACE_LEGS, SPACE_BOOTS));
        return list;
    }

    private CosmicContent() {
    }

    /** Called from the mod constructor so every DeferredRegister above is attached. */
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        ABILITIES.register(modBus);
        MATERIALS.register(modBus);
        POWER_SETS.register(modBus);
    }
}
