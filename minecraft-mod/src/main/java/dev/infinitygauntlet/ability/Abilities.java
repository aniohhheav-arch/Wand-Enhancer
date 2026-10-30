package dev.infinitygauntlet.ability;

import dev.infinitygauntlet.ability.space.BlinkAbility;
import dev.infinitygauntlet.ability.space.CosmicGatewayAbility;
import dev.infinitygauntlet.ability.space.DimensionSwapAbility;
import dev.infinitygauntlet.ability.space.DimensionalPortalAbility;
import dev.infinitygauntlet.ability.space.PortalBarrageAbility;
import dev.infinitygauntlet.ability.space.SpatialCompressionAbility;
import dev.infinitygauntlet.ability.space.SpatialPrisonAbility;
import dev.infinitygauntlet.ability.space.SpatialRiftAbility;
import dev.infinitygauntlet.ability.space.UniversalTeleportationAbility;
import dev.infinitygauntlet.ability.time.ChronologicalBarrierAbility;
import dev.infinitygauntlet.ability.time.RewindAbility;
import dev.infinitygauntlet.ability.time.TemporalBubbleAbility;
import dev.infinitygauntlet.ability.time.TemporalEchoAbility;
import dev.infinitygauntlet.ability.time.TemporalLoopAbility;
import dev.infinitygauntlet.ability.time.TimeAccelerationAbility;
import dev.infinitygauntlet.ability.time.TimeFractureAbility;
import dev.infinitygauntlet.ability.time.TimeFreezeAbility;
import dev.infinitygauntlet.ability.time.TimeSlowAbility;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ordered ability lists per stone. The order is the selection order in the HUD and ability wheel. */
public final class Abilities {
    private static final Map<InfinityStone, List<Ability>> BY_STONE = new EnumMap<>(InfinityStone.class);
    private static final List<Ability> ALL = new ArrayList<>();

    static {
        register(new DimensionalPortalAbility());
        register(new SpatialRiftAbility());
        register(new BlinkAbility());
        register(new PortalBarrageAbility());
        register(new SpatialCompressionAbility());
        register(new DimensionSwapAbility());
        register(new CosmicGatewayAbility());
        register(new SpatialPrisonAbility());
        register(new UniversalTeleportationAbility());

        register(new TimeFreezeAbility());
        register(new TimeSlowAbility());
        register(new TimeAccelerationAbility());
        register(new TemporalBubbleAbility());
        register(new TemporalEchoAbility());
        register(new RewindAbility());
        register(new TemporalLoopAbility());
        register(new ChronologicalBarrierAbility());
        register(new TimeFractureAbility());
    }

    private static void register(Ability ability) {
        BY_STONE.computeIfAbsent(ability.stone(), s -> new ArrayList<>()).add(ability);
        ALL.add(ability);
    }

    public static List<Ability> forStone(InfinityStone stone) {
        return Collections.unmodifiableList(BY_STONE.getOrDefault(stone, List.of()));
    }

    public static List<Ability> all() {
        return Collections.unmodifiableList(ALL);
    }

    @Nullable
    public static Ability byId(ResourceLocation id) {
        for (Ability ability : ALL) {
            if (ability.id().equals(id)) {
                return ability;
            }
        }
        return null;
    }

    private Abilities() {
    }
}
