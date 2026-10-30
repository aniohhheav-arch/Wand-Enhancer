package dev.infinitygauntlet.ability.power;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.power.PlanetaryCoreEntity;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Planetary Force: a three-second charge, a colossal throw and a four-stage impact (see {@link PlanetaryCoreEntity}). */
public final class PlanetaryForceAbility extends Ability {
    public PlanetaryForceAbility() {
        super("planetary_force", InfinityStone.POWER, 600);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        PlanetaryCoreEntity core = new PlanetaryCoreEntity(ModEntities.PLANETARY_CORE.get(), ctx.level());
        core.setup(ctx.player(), 200, 1.6f);
        Vec3 at = PlanetaryCoreEntity.holdPoint(ctx.player());
        core.moveTo(at.x, at.y, at.z);
        ctx.level().addFreshEntity(core);
        PowerFx.pose(ctx.player(), PowerPose.HOLD, PlanetaryCoreEntity.CHARGE);
        PowerFx.anim(ctx, "power_charge");
        Vfx.sound(ctx.level(), at, ModSounds.POWER_PLANETARY_CHARGE, 4f, 1f);
        // the pull-back: from over the shoulder to a wide shot of the caster dwarfed by what they are holding
        PowerFx.cinematic(ctx.player(), core, at, PowerFx.rel(ctx.player(), 1.1, 0.5, 4.2), PowerFx.rel(ctx.player(), -6, 5, 15),
                PlanetaryCoreEntity.CHARGE + 4, 0f, 1.1f);
        ServerScheduler.schedule(PlanetaryCoreEntity.CHARGE, () -> PowerFx.anim(ctx, "power_fire"));
        return CastResult.SUCCESS;
    }
}
