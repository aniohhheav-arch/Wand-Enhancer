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
import java.util.Set;

/** Ordered ability lists per stone. The order is the selection order in the HUD and ability wheel. */
public final class Abilities {
    private static final Map<InfinityStone, List<Ability>> BY_STONE = new EnumMap<>(InfinityStone.class);
    private static final List<Ability> ALL = new ArrayList<>();
    private static final List<Ability> COMBINATIONS = new ArrayList<>();

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

        register(new dev.infinitygauntlet.finisher.FinisherAbility("dimensional_erasure", dev.infinitygauntlet.finisher.FinisherType.DIMENSIONAL_ERASURE, 400));
        register(new dev.infinitygauntlet.finisher.FinisherAbility("temporal_collapse", dev.infinitygauntlet.finisher.FinisherType.TEMPORAL_COLLAPSE, 400));
        register(new dev.infinitygauntlet.ability.mind.TelekinesisAbility());
        register(new dev.infinitygauntlet.ability.mind.MindControlAbility());
        register(new dev.infinitygauntlet.ability.mind.NeuralBlastAbility());
        register(new dev.infinitygauntlet.ability.mind.PsychicPulseAbility());
        register(new dev.infinitygauntlet.ability.mind.MentalOverloadAbility());
        register(new dev.infinitygauntlet.ability.mind.LevitationFieldAbility());
        register(new dev.infinitygauntlet.ability.mind.AstralProjectionAbility());
        register(new dev.infinitygauntlet.ability.mind.MentalShieldAbility());
        register(new dev.infinitygauntlet.ability.mind.MindsEyeAbility());
        register(new dev.infinitygauntlet.finisher.FinisherAbility("psychic_overload", dev.infinitygauntlet.finisher.FinisherType.PSYCHIC_OVERLOAD, 400));
        register(new dev.infinitygauntlet.finisher.FinisherAbility("reality_rewrite", dev.infinitygauntlet.finisher.FinisherType.REALITY_REWRITE, 400));
        register(new dev.infinitygauntlet.finisher.FinisherAbility("soul_extraction", dev.infinitygauntlet.finisher.FinisherType.SOUL_EXTRACTION, 400));

        register(new dev.infinitygauntlet.ability.power.PowerBeamAbility());
        register(new dev.infinitygauntlet.ability.power.CosmicPunchAbility());
        register(new dev.infinitygauntlet.ability.power.GroundbreakerAbility());
        register(new dev.infinitygauntlet.ability.power.EnergyNovaAbility());
        register(new dev.infinitygauntlet.ability.power.CosmicArtilleryAbility());
        register(new dev.infinitygauntlet.ability.power.PowerOverloadAbility());
        register(new dev.infinitygauntlet.ability.power.GravityImpactAbility());
        register(new dev.infinitygauntlet.ability.power.CosmicBarrageAbility());
        register(new dev.infinitygauntlet.ability.power.PlanetaryForceAbility());

        register(new dev.infinitygauntlet.ability.combo.RemoteConsciousnessAbility());
        register(new dev.infinitygauntlet.ability.combo.RealityBreachAbility());
    }

    private static void register(Ability ability) {
        if (ability.isCombination()) {
            COMBINATIONS.add(ability);
        } else {
            BY_STONE.computeIfAbsent(ability.stone(), s -> new ArrayList<>()).add(ability);
        }
        ALL.add(ability);
    }

    /** The stone's own abilities, excluding combinations. */
    public static List<Ability> forStone(InfinityStone stone) {
        return Collections.unmodifiableList(BY_STONE.getOrDefault(stone, List.of()));
    }

    /** The stone's own abilities followed by every combination involving it whose stones are all installed. */
    public static List<Ability> available(InfinityStone stone, Set<InfinityStone> installed) {
        List<Ability> list = new ArrayList<>(forStone(stone));
        for (Ability combo : COMBINATIONS) {
            if (combo.requiredStones().contains(stone) && installed.containsAll(combo.requiredStones())) {
                list.add(combo);
            }
        }
        return list;
    }

    public static List<Ability> combinations() {
        return Collections.unmodifiableList(COMBINATIONS);
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
