package com.infinitemultiverse.abilities.stand;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.ability.Ability;
import com.infinitemultiverse.core.registry.MultiverseRegistries;
import com.infinitemultiverse.stand.StandType;
import java.util.List;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StandAbilities {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, InfiniteMultiverse.MOD_ID);
    public static final DeferredRegister<StandType> STAND_TYPES = DeferredRegister.create(MultiverseRegistries.STAND_TYPE_KEY, InfiniteMultiverse.MOD_ID);

    public static final DeferredHolder<Ability, ManifestStandAbility> MANIFEST = ABILITIES.register("stand_manifest", ManifestStandAbility::new);
    public static final DeferredHolder<Ability, BarrageAbility> ORA_BARRAGE = ABILITIES.register("ora_barrage", BarrageAbility::new);
    public static final DeferredHolder<Ability, PrecisionStrikeAbility> STAR_FINGER = ABILITIES.register("star_finger", PrecisionStrikeAbility::new);
    public static final DeferredHolder<Ability, GuardAbility> STAR_GUARD = ABILITIES.register("star_guard", GuardAbility::new);
    public static final DeferredHolder<Ability, TimeStopAbility> THE_WORLD = ABILITIES.register("star_platinum_the_world", TimeStopAbility::new);

    /** Close-range powerhouse: barrage, precision strike, projectile guard and a short time stop. */
    public static final DeferredHolder<StandType, StandType> STAR_PLATINUM = STAND_TYPES.register("star_platinum",
            () -> new StandType(0xC77DFF, List.of(MANIFEST, ORA_BARRAGE, STAR_FINGER, STAR_GUARD, THE_WORLD)));

    private StandAbilities() {
    }
}
