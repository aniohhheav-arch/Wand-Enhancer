package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.stand.StandType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StandAbilities {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, InfiniteMultiverse.MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES = DeferredRegister.create(MultiverseRegistries.STAND_TYPE_KEY, InfiniteMultiverse.MOD_ID);

    private static final String SHOUT = "message.infinitemultiverse.shout.";

    public static final DeferredHolder<Ability, ManifestStandAbility> MANIFEST = ABILITIES.register("stand_manifest", ManifestStandAbility::new);

    // Star Platinum
    public static final DeferredHolder<Ability, BarrageAbility> ORA_BARRAGE = ABILITIES.register("ora_barrage", () -> new BarrageAbility(SHOUT + "ora", ChatFormatting.LIGHT_PURPLE));
    public static final DeferredHolder<Ability, PrecisionStrikeAbility> STAR_FINGER = ABILITIES.register("star_finger", () -> new PrecisionStrikeAbility(1f));
    public static final DeferredHolder<Ability, GuardAbility> STAR_GUARD = ABILITIES.register("star_guard", GuardAbility::new);
    public static final DeferredHolder<Ability, TimeStopAbility> STAR_PLATINUM_THE_WORLD = ABILITIES.register("star_platinum_the_world",
            () -> new TimeStopAbility(1f, SHOUT + "star_platinum_the_world"));

    // The World
    public static final DeferredHolder<Ability, BarrageAbility> MUDA_BARRAGE = ABILITIES.register("muda_barrage", () -> new BarrageAbility(SHOUT + "muda", ChatFormatting.GOLD));
    public static final DeferredHolder<Ability, KnifeThrowAbility> KNIFE_THROW = ABILITIES.register("knife_throw", KnifeThrowAbility::new);
    public static final DeferredHolder<Ability, TimeStopAbility> ZA_WARUDO = ABILITIES.register("za_warudo", () -> new TimeStopAbility(1.5f, SHOUT + "za_warudo"));

    // Killer Queen
    public static final DeferredHolder<Ability, FirstBombAbility> FIRST_BOMB = ABILITIES.register("first_bomb", FirstBombAbility::new);
    public static final DeferredHolder<Ability, DetonateAbility> DETONATE = ABILITIES.register("detonate", DetonateAbility::new);
    public static final DeferredHolder<Ability, BitesTheDustAbility> BITES_THE_DUST = ABILITIES.register("bites_the_dust", BitesTheDustAbility::new);

    // Gold Experience
    public static final DeferredHolder<Ability, BarrageAbility> GOLD_EXPERIENCE_BARRAGE = ABILITIES.register("gold_experience_barrage", () -> new BarrageAbility(SHOUT + "muda", ChatFormatting.YELLOW));
    public static final DeferredHolder<Ability, LifeGiverAbility> LIFE_GIVER = ABILITIES.register("life_giver", LifeGiverAbility::new);
    public static final DeferredHolder<Ability, HealingFieldAbility> HEALING_FIELD = ABILITIES.register("healing_field", HealingFieldAbility::new);

    // King Crimson
    public static final DeferredHolder<Ability, EpitaphAbility> EPITAPH = ABILITIES.register("epitaph", EpitaphAbility::new);
    public static final DeferredHolder<Ability, TimeEraseAbility> TIME_ERASE = ABILITIES.register("time_erase", TimeEraseAbility::new);
    public static final DeferredHolder<Ability, PrecisionStrikeAbility> KING_CRIMSON_CHOP = ABILITIES.register("king_crimson_chop", () -> new PrecisionStrikeAbility(1.6f));

    // Made in Heaven
    public static final DeferredHolder<Ability, AccelerationAbility> HEAVEN_ACCELERATION = ABILITIES.register("heaven_acceleration", AccelerationAbility::new);
    public static final DeferredHolder<Ability, TimeAccelerationAbility> TIME_ACCELERATION = ABILITIES.register("time_acceleration", TimeAccelerationAbility::new);
    public static final DeferredHolder<Ability, HeavenBlinkAbility> HEAVEN_BLINK = ABILITIES.register("heaven_blink", HeavenBlinkAbility::new);

    // Tusk
    public static final DeferredHolder<Ability, NailShotAbility> NAIL_SHOT = ABILITIES.register("nail_shot", () -> new NailShotAbility(NailShotAbility.Variant.NAIL));
    public static final DeferredHolder<Ability, NailShotAbility> GOLDEN_ROTATION = ABILITIES.register("golden_rotation", () -> new NailShotAbility(NailShotAbility.Variant.GOLDEN));
    public static final DeferredHolder<Ability, NailShotAbility> INFINITE_ROTATION = ABILITIES.register("infinite_rotation", () -> new NailShotAbility(NailShotAbility.Variant.INFINITE));

    public static final DeferredHolder<StandType, StandType> STAR_PLATINUM = STAND_TYPES.register("star_platinum",
            () -> new StandType(0x8F7BFF, List.of(MANIFEST, ORA_BARRAGE, STAR_FINGER, STAR_GUARD, STAR_PLATINUM_THE_WORLD)));
    public static final DeferredHolder<StandType, StandType> THE_WORLD = STAND_TYPES.register("the_world",
            () -> new StandType(0xFFD34D, 1.05f, false, List.of(MANIFEST, MUDA_BARRAGE, KNIFE_THROW, STAR_GUARD, ZA_WARUDO)));
    public static final DeferredHolder<StandType, StandType> KILLER_QUEEN = STAND_TYPES.register("killer_queen",
            () -> new StandType(0xFF8EC7, 1f, true, List.of(MANIFEST, FIRST_BOMB, DETONATE, BITES_THE_DUST)));
    public static final DeferredHolder<StandType, StandType> GOLD_EXPERIENCE = STAND_TYPES.register("gold_experience",
            () -> new StandType(0xF5C542, List.of(MANIFEST, GOLD_EXPERIENCE_BARRAGE, LIFE_GIVER, HEALING_FIELD)));
    public static final DeferredHolder<StandType, StandType> KING_CRIMSON = STAND_TYPES.register("king_crimson",
            () -> new StandType(0xE0284A, 1.05f, false, List.of(MANIFEST, KING_CRIMSON_CHOP, EPITAPH, TIME_ERASE)));
    public static final DeferredHolder<StandType, StandType> MADE_IN_HEAVEN = STAND_TYPES.register("made_in_heaven",
            () -> new StandType(0xB8FFE0, List.of(MANIFEST, HEAVEN_ACCELERATION, HEAVEN_BLINK, TIME_ACCELERATION)));
    public static final DeferredHolder<StandType, StandType> TUSK = STAND_TYPES.register("tusk",
            () -> new StandType(0xFF9ED0, 0.55f, false, List.of(MANIFEST, NAIL_SHOT, GOLDEN_ROTATION, INFINITE_ROTATION)));

    private StandAbilities() {
    }
}
