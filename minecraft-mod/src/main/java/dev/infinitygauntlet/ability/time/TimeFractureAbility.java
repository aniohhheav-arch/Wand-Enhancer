package dev.infinitygauntlet.ability.time;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.entity.TimeFractureEntity;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.ServerScheduler;
import dev.infinitygauntlet.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Charges, then shatters the air above the battlefield into a slowly turning pane of broken time. */
public final class TimeFractureAbility extends Ability {
    private static final int CHARGE_TICKS = 24;

    public TimeFractureAbility() {
        super("time_fracture", InfinityStone.TIME, 1200);
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer player = ctx.player();
        Vec3 ground = ctx.groundTarget(48, 20);
        Vec3 center = ground.add(0, 6, 0);
        float yaw = player.getYRot();
        if (ctx.gauntlet().getItem() instanceof InfinityGauntletItem gauntlet) {
            gauntlet.playAnimation(player, ctx.gauntlet(), "charge");
        }
        Vfx.sound(level, player.position(), ModSounds.GAUNTLET_CHARGE, 1.5f, 1.1f);
        Vfx.link(level, ctx.handPos(), center, InfinityStone.TIME, CHARGE_TICKS);
        Vfx.camera(level, player.position(), 8, 0.15f, 0.9f, CHARGE_TICKS, 0, 0, 0);
        ServerScheduler.schedule(CHARGE_TICKS, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            TimeFractureEntity fracture = new TimeFractureEntity(ModEntities.TIME_FRACTURE.get(), level);
            fracture.setup(player, ServerConfig.FRACTURE_SECONDS.get() * 20, 9f);
            fracture.face(yaw);
            fracture.moveTo(center.x, center.y, center.z);
            level.addFreshEntity(fracture);
        });
        return CastResult.SUCCESS;
    }
}
