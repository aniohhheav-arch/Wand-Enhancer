package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.advancement.GauntletTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, InfinityGauntlet.MOD_ID);

    /** Fires with the ability id when an ability is cast successfully. */
    public static final DeferredHolder<CriterionTrigger<?>, GauntletTrigger> ABILITY_USED = REGISTER.register("ability_used", GauntletTrigger::new);
    /** Fires with the stone name when a stone is installed. */
    public static final DeferredHolder<CriterionTrigger<?>, GauntletTrigger> STONE_INSTALLED = REGISTER.register("stone_installed", GauntletTrigger::new);
    /** Fires with "same_dimension" / "cross_dimension" when a player travels through a rupture portal. */
    public static final DeferredHolder<CriterionTrigger<?>, GauntletTrigger> PORTAL_TRAVEL = REGISTER.register("portal_travel", GauntletTrigger::new);

    private ModTriggers() {
    }
}
