package dev.infinitygauntlet.ability.space;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.entity.MiniPortalEntity;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/** Opens a ring of small portals around the caster that track enemies and fire space bolts through themselves. */
public final class PortalBarrageAbility extends Ability {
    private static final int PORTALS = 6;

    public PortalBarrageAbility() {
        super("portal_barrage", InfinityStone.SPACE, 180);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        for (int i = 0; i < PORTALS; i++) {
            MiniPortalEntity portal = new MiniPortalEntity(ModEntities.MINI_PORTAL.get(), ctx.level());
            portal.configure(ctx.player(), i, PORTALS, i * 3);
            ctx.level().addFreshEntity(portal);
        }
        Vfx.shockwave(ctx.level(), ctx.player().position().add(0, 0.1, 0), new Vec3(0, 1, 0), InfinityStone.SPACE, 3.5f, 14);
        Vfx.sound(ctx.level(), ctx.player().position(), ModSounds.GAUNTLET_CAST, 1.2f, 1.1f);
        return CastResult.SUCCESS;
    }
}
