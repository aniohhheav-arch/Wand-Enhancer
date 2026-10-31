package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.MentalShieldEntity;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.world.phys.Vec3;

/** Mental Shield (see {@link MentalShieldEntity}): fifteen seconds of a dome that turns arrows and stops everything else. */
public final class MentalShieldAbility extends Ability {
    public MentalShieldAbility() {
        super("mental_shield", InfinityStone.MIND, 400);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        MentalShieldEntity shield = new MentalShieldEntity(ModEntities.MENTAL_SHIELD.get(), ctx.level());
        shield.setup(ctx.player(), 300, 4.5f);
        shield.moveTo(ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
        ctx.level().addFreshEntity(shield);
        PowerFx.pose(ctx.player(), PowerPose.SPREAD, 14);
        PowerFx.anim(ctx, "mind_push");
        Vfx.send(ctx.level(), VfxType.GEOMETRY_BURST, ctx.player().position().add(0, 1, 0), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 4f, 18);
        Vfx.sound(ctx.level(), ctx.player().position(), ModSounds.MIND_SHIELD, 2f, 1f);
        return CastResult.SUCCESS;
    }
}
